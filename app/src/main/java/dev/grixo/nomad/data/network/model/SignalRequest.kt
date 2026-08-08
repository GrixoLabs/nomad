package dev.grixo.nomad.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class SignalRequest(
    val device_uuid: String,
    val gps_timestamp_utc: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy_m: Float,
    val altitude_m: Double,
    val speed_mps: Float,
    val bearing_deg: Float,
    val battery_percent: Int,
    val charging: Boolean,
    val battery_temperature: Float,
    val network_type: String,
    val wifi_enabled: Boolean,
    val bluetooth_enabled: Boolean,
    val screen_on: Boolean,
    val power_save_mode: Boolean
)
