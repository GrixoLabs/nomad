package dev.grixo.nomad.ui.main

data class MainUiState(
    val isConnected: Boolean = false,
    val isTracking: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val batteryPercent: Int? = null,
    val networkType: String = "Unknown",
    val lastUploadTime: String = "Never",
    val errorMessage: String? = null
)
