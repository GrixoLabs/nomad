package dev.grixo.nomad.ui.main

data class NearbyPlaceUi(
    val name: String,
    val category: String?,
    val distanceKm: Double?,
    val popularityScore: Int = 0
)

data class MainUiState(
    val userName: String? = null,
    val isRegistered: Boolean = false,
    val journalEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val isTracking: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val altitudeM: Double? = null,
    val placeLabel: String = "Locating…",
    val weatherSummary: String = "—",
    val temperatureC: Double? = null,
    val nearbyPlaces: List<NearbyPlaceUi> = emptyList(),
    val nearbyLoading: Boolean = false,
    val nearbySort: String = "popularity",
    val showNearby: Boolean = false,
    val contextLoading: Boolean = false,
    val batteryPercent: Int? = null,
    val networkType: String = "Unknown",
    val lastUploadTime: String = "Never",
    val offlineQueueCount: Int = 0,
    val permissionDenied: Boolean = false,
    val errorMessage: String? = null,
    val loggedOut: Boolean = false
)
