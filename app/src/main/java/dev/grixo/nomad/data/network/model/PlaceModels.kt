package dev.grixo.nomad.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaceResolveResponse(
    val display_name: String,
    val locality: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val area_label: String? = null,
    val grid_key: String,
    val cached: Boolean = false
)

@Serializable
data class WeatherResponse(
    val summary: String,
    val temperature_c: Double? = null,
    val feels_like_c: Double? = null,
    val humidity_percent: Int? = null,
    val wind_speed_kmh: Double? = null,
    val weather_code: Int? = null,
    val cached: Boolean = false
)

@Serializable
data class NearbyPlace(
    val name: String,
    val category: String? = null,
    val latitude: Double,
    val longitude: Double,
    val distance_m: Double? = null,
    val popularity_score: Int = 0
)

@Serializable
data class NearbyPlacesResponse(
    val places: List<NearbyPlace> = emptyList(),
    val cached: Boolean = false,
    val sort: String = "popularity"
)
