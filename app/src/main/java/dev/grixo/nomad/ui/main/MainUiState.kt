package dev.grixo.nomad.ui.main

data class MainUiState(
    val userName: String? = null,
    val isRegistered: Boolean = false,
    val journalEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val isTracking: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val batteryPercent: Int? = null,
    val networkType: String = "Unknown",
    val lastUploadTime: String = "Never",
    val offlineQueueCount: Int = 0,
    val permissionDenied: Boolean = false,
    val errorMessage: String? = null
)
