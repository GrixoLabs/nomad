package dev.grixo.nomad.ui.history

import dev.grixo.nomad.data.network.model.HistoryResponse
import dev.grixo.nomad.data.network.model.PlotPointResponse
import dev.grixo.nomad.data.network.model.TrackPoint
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Geometry helpers for Travel History map styling. */
internal object HistoryMapGeometry {

    const val STAY_RADIUS_M = 1_000.0
    const val LONG_STAY_HOURS = 1.0
    /** Overnight markers: require meaningful night dwell (tighter than raw flags). */
    const val OVERNIGHT_IDLE_HOURS = 5.0
    const val FLIGHT_GAP_M = 1_000_000.0
    private const val EARTH_RADIUS_M = 6_371_000.0

    data class StayMarker(
        val latitude: Double,
        val longitude: Double,
        val plotId: Long,
        val hours: Double,
        val overnight: Boolean
    )

    fun trackCollection(history: HistoryResponse): FeatureCollection {
        val features = mutableListOf<Feature>()
        val segments = history.segments.filter { it.points.size >= 2 }
        if (segments.isNotEmpty()) {
            for (seg in segments) {
                features += curvedSegmentFeature(seg.points, seg.kind)
            }
        } else {
            // Fallback: chronological plot cells when segments are thin.
            val ordered = history.plot_points.sortedBy { it.last_gps_timestamp }
            if (ordered.size >= 2) {
                val pts = ordered.map {
                    TrackPoint(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        captured_at = it.last_gps_timestamp
                    )
                }
                features += curvedSegmentFeature(pts, "travel")
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    /**
     * Build a path that uses great-circle arcs for jumps > [FLIGHT_GAP_M]
     * (e.g. in-flight GPS gaps) and straight segments otherwise.
     */
    private fun curvedSegmentFeature(points: List<TrackPoint>, kind: String): Feature {
        val lngLats = mutableListOf<Point>()
        lngLats += Point.fromLngLat(points.first().longitude, points.first().latitude)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val dist = haversineMeters(
                prev.latitude, prev.longitude, cur.latitude, cur.longitude
            )
            if (dist > FLIGHT_GAP_M) {
                val steps = flightArcSteps(dist)
                val arc = greatCirclePoints(
                    prev.latitude, prev.longitude,
                    cur.latitude, cur.longitude,
                    steps
                )
                // Skip first point (already present).
                lngLats.addAll(arc.drop(1))
            } else {
                lngLats += Point.fromLngLat(cur.longitude, cur.latitude)
            }
        }
        return Feature.fromGeometry(LineString.fromLngLats(lngLats)).also {
            it.addStringProperty("kind", kind)
        }
    }

    private fun flightArcSteps(distanceM: Double): Int {
        // Roughly one vertex per ~250 km, clamped.
        return min(48, maxOf(8, (distanceM / 250_000.0).toInt()))
    }

    /**
     * Long stays: cluster plots within 1 km; highlight when dwell ≥ 1 hour.
     * Overnight: tightened night markers (dark-red encircled), clustered to 1 km.
     */
    fun stayMarkers(history: HistoryResponse): Pair<List<StayMarker>, List<StayMarker>> {
        val overnightSeeds = overnightSeedPlots(history)
        val overnight = clusterStays(overnightSeeds, overnight = true)
        val overnightIds = overnight.map { it.plotId }.toHashSet()

        // Re-cluster all dwell plots within 1 km for ≥1 h stays.
        val longClusters = clusterByRadius(history.plot_points.filter { it.total_time_hours > 0 })
            .mapNotNull { cluster ->
                val hours = cluster.sumOf { it.total_time_hours }
                if (hours < LONG_STAY_HOURS) return@mapNotNull null
                val anchor = cluster.maxBy { it.total_time_hours }
                val lat = cluster.sumOf { it.latitude * it.total_time_hours } / hours
                val lon = cluster.sumOf { it.longitude * it.total_time_hours } / hours
                StayMarker(
                    latitude = lat,
                    longitude = lon,
                    plotId = anchor.plot_id,
                    hours = hours,
                    overnight = false
                )
            }
            // Drop long-stay dots that already have an overnight marker nearby.
            .filter { stay ->
                overnight.none { night ->
                    haversineMeters(
                        stay.latitude, stay.longitude,
                        night.latitude, night.longitude
                    ) <= STAY_RADIUS_M
                } && stay.plotId !in overnightIds
            }

        return longClusters to overnight
    }

    fun longStayCollection(stays: List<StayMarker>): FeatureCollection {
        val features = stays.map { stay ->
            Feature.fromGeometry(Point.fromLngLat(stay.longitude, stay.latitude)).also {
                it.addNumberProperty("plot_id", stay.plotId)
                it.addNumberProperty("hours", stay.hours)
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    fun overnightCollection(stays: List<StayMarker>): FeatureCollection {
        val features = stays.map { stay ->
            Feature.fromGeometry(Point.fromLngLat(stay.longitude, stay.latitude)).also {
                it.addNumberProperty("plot_id", stay.plotId)
                it.addNumberProperty("hours", stay.hours)
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    /**
     * Prefer dedicated night_stays with idle ≥ [OVERNIGHT_IDLE_HOURS];
     * fall back to night_stayed plots with enough total dwell.
     */
    private fun overnightSeedPlots(history: HistoryResponse): List<PlotPointResponse> {
        if (history.night_stays.isNotEmpty()) {
            val strong = history.night_stays.filter { it.idle_hours >= OVERNIGHT_IDLE_HOURS }
            if (strong.isNotEmpty()) {
                return strong.map { night ->
                    history.plot_points.firstOrNull { plot ->
                        haversineMeters(
                            plot.latitude, plot.longitude,
                            night.latitude, night.longitude
                        ) <= STAY_RADIUS_M
                    } ?: PlotPointResponse(
                        plot_id = night.night_stay_id,
                        latitude = night.latitude,
                        longitude = night.longitude,
                        first_gps_timestamp = night.started_at,
                        last_gps_timestamp = night.ended_at,
                        total_time_hours = night.idle_hours,
                        night_stayed = true
                    )
                }
            }
        }
        return history.plot_points.filter { plot ->
            plot.night_stayed && plot.total_time_hours >= OVERNIGHT_IDLE_HOURS
        }
    }

    private fun clusterStays(
        plots: List<PlotPointResponse>,
        overnight: Boolean
    ): List<StayMarker> {
        if (plots.isEmpty()) return emptyList()
        return clusterByRadius(plots).map { cluster ->
            val hours = cluster.sumOf { it.total_time_hours }.coerceAtLeast(
                cluster.maxOf { it.total_time_hours }
            )
            val weight = cluster.sumOf { maxOf(it.total_time_hours, 0.01) }
            val lat = cluster.sumOf { it.latitude * maxOf(it.total_time_hours, 0.01) } / weight
            val lon = cluster.sumOf { it.longitude * maxOf(it.total_time_hours, 0.01) } / weight
            val anchor = cluster.maxBy { it.total_time_hours }
            StayMarker(
                latitude = lat,
                longitude = lon,
                plotId = anchor.plot_id,
                hours = hours,
                overnight = overnight
            )
        }
    }

    /** Greedy 1 km clustering — merge if within radius of any member. */
    private fun clusterByRadius(plots: List<PlotPointResponse>): List<List<PlotPointResponse>> {
        if (plots.isEmpty()) return emptyList()
        val remaining = plots.toMutableList()
        val clusters = mutableListOf<List<PlotPointResponse>>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val cluster = mutableListOf(seed)
            var grew = true
            while (grew) {
                grew = false
                val iter = remaining.iterator()
                while (iter.hasNext()) {
                    val candidate = iter.next()
                    val near = cluster.any { member ->
                        haversineMeters(
                            member.latitude, member.longitude,
                            candidate.latitude, candidate.longitude
                        ) <= STAY_RADIUS_M
                    }
                    if (near) {
                        cluster += candidate
                        iter.remove()
                        grew = true
                    }
                }
            }
            clusters += cluster
        }
        return clusters
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dφ = Math.toRadians(lat2 - lat1)
        val dλ = Math.toRadians(lon2 - lon1)
        val a = sin(dφ / 2).pow(2) +
            cos(p1) * cos(p2) * sin(dλ / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /**
     * Spherical linear interpolation along the great-circle from A → B.
     * [steps] is the number of intermediate segments (≥ 2).
     */
    fun greatCirclePoints(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        steps: Int
    ): List<Point> {
        val φ1 = Math.toRadians(lat1)
        val λ1 = Math.toRadians(lon1)
        val φ2 = Math.toRadians(lat2)
        val λ2 = Math.toRadians(lon2)

        val x1 = cos(φ1) * cos(λ1)
        val y1 = cos(φ1) * sin(λ1)
        val z1 = sin(φ1)
        val x2 = cos(φ2) * cos(λ2)
        val y2 = cos(φ2) * sin(λ2)
        val z2 = sin(φ2)

        var cosD = x1 * x2 + y1 * y2 + z1 * z2
        cosD = cosD.coerceIn(-1.0, 1.0)
        val d = acosSafe(cosD)
        if (d < 1e-9) {
            return listOf(
                Point.fromLngLat(lon1, lat1),
                Point.fromLngLat(lon2, lat2)
            )
        }

        val n = maxOf(2, steps)
        val out = ArrayList<Point>(n + 1)
        for (i in 0..n) {
            val f = i.toDouble() / n
            val a = sin((1 - f) * d) / sin(d)
            val b = sin(f * d) / sin(d)
            val x = a * x1 + b * x2
            val y = a * y1 + b * y2
            val z = a * z1 + b * z2
            val lat = Math.toDegrees(atan2(z, sqrt(x * x + y * y)))
            val lon = Math.toDegrees(atan2(y, x))
            out += Point.fromLngLat(lon, lat)
        }
        return out
    }

    private fun acosSafe(v: Double): Double = kotlin.math.acos(v.coerceIn(-1.0, 1.0))
}
