package dev.grixo.nomad.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistrationRequest(
    val device_uuid: String,
    val device_name: String,
    val manufacturer: String,
    val model: String,
    val android_version: String,
    val app_version: String
)
