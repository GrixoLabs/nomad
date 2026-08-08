package dev.grixo.nomad.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class UserRegistrationRequest(
    val device_uuid: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val age: Int,
    val gender: String
)

@Serializable
data class UserRegistrationResponse(
    val user_id: Long? = null,
    val message: String = "accepted"
)
