package dev.grixo.nomad.ui.navigate

import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.grixo.nomad.data.network.model.RoutePoint
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale

@Composable
fun NavigateMapRoute(
    onClose: () -> Unit,
    viewModel: NavigateMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    NavigateMapScreen(
        state = state,
        onClose = onClose,
        onRetry = viewModel::reload
    )
}

@Composable
fun NavigateMapScreen(
    state: NavigateMapUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) {
                Text("Back", color = colors.primary)
            }
            Text(
                text = state.placeName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )
        }

        val summary = buildString {
            state.distanceM?.let { append("%.1f km".format(Locale.US, it / 1000.0)) }
            state.durationSeconds?.let { seconds ->
                if (isNotEmpty()) append(" · ")
                append("%d min".format(Locale.US, (seconds + 59) / 60))
            }
        }
        if (summary.isNotBlank()) {
            Text(
                summary,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .background(colors.surfaceVariant, RoundedCornerShape(16.dp))
        ) {
            when {
                state.loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = colors.primary
                    )
                }
                state.errorMessage != null && state.routePoints.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.errorMessage, color = colors.error)
                        TextButton(onClick = onRetry) {
                            Text("Retry", color = colors.primary)
                        }
                    }
                }
                else -> {
                    MapLibreRouteMap(
                        styleUrl = state.styleUrl,
                        tileUrlTemplate = state.tileUrlTemplate,
                        attribution = state.attribution,
                        originLat = state.originLat,
                        originLon = state.originLon,
                        destLat = state.destLat,
                        destLon = state.destLon,
                        routePoints = state.routePoints
                    )
                }
            }
        }
    }
}

@Composable
private fun MapLibreRouteMap(
    styleUrl: String?,
    tileUrlTemplate: String?,
    attribution: String,
    originLat: Double?,
    originLon: Double?,
    destLat: Double?,
    destLon: Double?,
    routePoints: List<RoutePoint>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    var mapReady by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { view ->
            if (!mapReady) {
                view.getMapAsync { map ->
                    mapReady = true
                    val styleBuilder = when {
                        !styleUrl.isNullOrBlank() -> Style.Builder().fromUri(styleUrl)
                        !tileUrlTemplate.isNullOrBlank() -> Style.Builder().fromJson(
                            rasterStyleJson(tileUrlTemplate, attribution)
                        )
                        else -> return@getMapAsync
                    }
                    map.setStyle(styleBuilder) { style ->
                        addRouteLayers(style, originLat, originLon, destLat, destLon, routePoints)
                        fitToRoute(map, originLat, originLon, destLat, destLon, routePoints)
                    }
                }
            } else {
                view.getMapAsync { map ->
                    map.getStyle { style ->
                        updateRouteLayers(style, originLat, originLon, destLat, destLon, routePoints)
                        fitToRoute(map, originLat, originLon, destLat, destLon, routePoints)
                    }
                }
            }
        }
    )
}

private fun rasterStyleJson(tileUrl: String, attribution: String): String {
    val safeTiles = tileUrl.replace("\\", "\\\\").replace("\"", "\\\"")
    val safeAttr = attribution.replace("\\", "\\\\").replace("\"", "\\\"")
    return """
    {
      "version": 8,
      "name": "Nomad Stadia Raster",
      "sources": {
        "stadia": {
          "type": "raster",
          "tiles": ["$safeTiles"],
          "tileSize": 256,
          "attribution": "$safeAttr"
        }
      },
      "layers": [
        { "id": "stadia", "type": "raster", "source": "stadia" }
      ]
    }
    """.trimIndent()
}

private fun addRouteLayers(
    style: Style,
    originLat: Double?,
    originLon: Double?,
    destLat: Double?,
    destLon: Double?,
    routePoints: List<RoutePoint>
) {
    if (style.getSource(SOURCE_ROUTE) == null) {
        style.addSource(GeoJsonSource(SOURCE_ROUTE, routeCollection(routePoints)))
        style.addLayer(
            LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#0B1F4A")),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineOpacity(0.95f)
            )
        )
    } else {
        (style.getSource(SOURCE_ROUTE) as? GeoJsonSource)?.setGeoJson(routeCollection(routePoints))
    }

    if (style.getSource(SOURCE_ENDS) == null) {
        style.addSource(
            GeoJsonSource(SOURCE_ENDS, endsCollection(originLat, originLon, destLat, destLon))
        )
        style.addLayer(
            CircleLayer(LAYER_ORIGIN, SOURCE_ENDS).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(Color.parseColor("#2563EB")),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            ).withFilter(
                org.maplibre.android.style.expressions.Expression.eq(
                    org.maplibre.android.style.expressions.Expression.get("kind"),
                    org.maplibre.android.style.expressions.Expression.literal("origin")
                )
            )
        )
        style.addLayer(
            CircleLayer(LAYER_DEST, SOURCE_ENDS).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(Color.parseColor("#DC2626")),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            ).withFilter(
                org.maplibre.android.style.expressions.Expression.eq(
                    org.maplibre.android.style.expressions.Expression.get("kind"),
                    org.maplibre.android.style.expressions.Expression.literal("dest")
                )
            )
        )
    } else {
        (style.getSource(SOURCE_ENDS) as? GeoJsonSource)?.setGeoJson(
            endsCollection(originLat, originLon, destLat, destLon)
        )
    }
}

private fun updateRouteLayers(
    style: Style,
    originLat: Double?,
    originLon: Double?,
    destLat: Double?,
    destLon: Double?,
    routePoints: List<RoutePoint>
) {
    (style.getSource(SOURCE_ROUTE) as? GeoJsonSource)?.setGeoJson(routeCollection(routePoints))
    (style.getSource(SOURCE_ENDS) as? GeoJsonSource)?.setGeoJson(
        endsCollection(originLat, originLon, destLat, destLon)
    )
}

private fun routeCollection(points: List<RoutePoint>): FeatureCollection {
    if (points.size < 2) return FeatureCollection.fromFeatures(emptyList())
    val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
    return FeatureCollection.fromFeature(Feature.fromGeometry(line))
}

private fun endsCollection(
    originLat: Double?,
    originLon: Double?,
    destLat: Double?,
    destLon: Double?
): FeatureCollection {
    val features = buildList {
        if (originLat != null && originLon != null) {
            add(
                Feature.fromGeometry(Point.fromLngLat(originLon, originLat)).also {
                    it.addStringProperty("kind", "origin")
                }
            )
        }
        if (destLat != null && destLon != null) {
            add(
                Feature.fromGeometry(Point.fromLngLat(destLon, destLat)).also {
                    it.addStringProperty("kind", "dest")
                }
            )
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun fitToRoute(
    map: org.maplibre.android.maps.MapLibreMap,
    originLat: Double?,
    originLon: Double?,
    destLat: Double?,
    destLon: Double?,
    routePoints: List<RoutePoint>
) {
    val points = buildList {
        routePoints.forEach { add(LatLng(it.latitude, it.longitude)) }
        if (originLat != null && originLon != null) add(LatLng(originLat, originLon))
        if (destLat != null && destLon != null) add(LatLng(destLat, destLon))
    }
    if (points.isEmpty()) return
    if (points.size == 1) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 13.0))
        return
    }
    val bounds = LatLngBounds.Builder().includes(points).build()
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
}

private const val SOURCE_ROUTE = "nomad-nav-route"
private const val SOURCE_ENDS = "nomad-nav-ends"
private const val LAYER_ROUTE = "nomad-nav-route-line"
private const val LAYER_ORIGIN = "nomad-nav-origin"
private const val LAYER_DEST = "nomad-nav-dest"
