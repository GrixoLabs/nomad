package dev.grixo.nomad.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class RouteRequest(
    val origin_lat: Double,
    val origin_lon: Double,
    val dest_lat: Double,
    val dest_lon: Double,
    val travel_mode: String = "DRIVE"
)

@Serializable
data class RoutePoint(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class RouteResponse(
    val points: List<RoutePoint> = emptyList(),
    val distance_m: Int? = null,
    val duration_seconds: Int? = null,
    val encoded_polyline: String? = null,
    val travel_mode: String = "DRIVE"
)
