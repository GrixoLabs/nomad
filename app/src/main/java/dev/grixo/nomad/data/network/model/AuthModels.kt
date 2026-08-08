package dev.grixo.nomad.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthRegisterRequest(
    val name: String? = null,
    val email: String? = null,
    val phone_number: String? = null,
    val password: String,
    val confirm_password: String,
    val age: Int? = null,
    val gender: String? = null,
    val device_uuid: String? = null
)

@Serializable
data class AuthRegisterResponse(
    val user_id: String,
    val account_status: String,
    val email_verified: Boolean = false,
    val phone_verified: Boolean = false,
    val journal_enabled: Boolean = false,
    val message: String = ""
)

@Serializable
data class SendEmailOtpRequest(val email: String)

@Serializable
data class SendSmsOtpRequest(val phone_number: String)

@Serializable
data class VerifyEmailOtpRequest(val email: String, val otp: String)

@Serializable
data class VerifySmsOtpRequest(val phone_number: String, val otp: String)

@Serializable
data class AuthMessageResponse(val message: String = "")

@Serializable
data class LoginRequest(
    val email: String? = null,
    val phone_number: String? = null,
    val password: String
)

@Serializable
data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String = "bearer",
    val user_id: String,
    val account_status: String = "",
    val email_verified: Boolean = false,
    val phone_verified: Boolean = false,
    val journal_enabled: Boolean = false,
    val name: String? = null,
    val email: String? = null,
    val phone_number: String? = null,
    val age: Int? = null,
    val gender: String? = null
)

@Serializable
data class ForgotPasswordRequest(
    val email: String? = null,
    val phone_number: String? = null
)

@Serializable
data class ResetPasswordRequest(
    val email: String? = null,
    val phone_number: String? = null,
    val otp: String,
    val new_password: String,
    val confirm_password: String
)
