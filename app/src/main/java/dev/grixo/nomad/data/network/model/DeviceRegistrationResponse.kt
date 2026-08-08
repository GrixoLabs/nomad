package dev.grixo.nomad.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistrationResponse(
    val device_id: Long,
    val message: String
)
