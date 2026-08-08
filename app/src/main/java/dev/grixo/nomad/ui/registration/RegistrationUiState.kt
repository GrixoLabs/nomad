package dev.grixo.nomad.ui.registration

import dev.grixo.nomad.domain.model.Gender

enum class RegistrationStep {
    PROFILE,
    OTP,
    SIGN_IN,
    FORGOT,
    RESET
}

data class RegistrationUiState(
    val step: RegistrationStep = RegistrationStep.PROFILE,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    /** Single contact field for sign-in / forgot (email or phone). */
    val contact: String = "",
    val age: String = "",
    val gender: Gender = Gender.PREFER_NOT,
    val password: String = "",
    val confirmPassword: String = "",
    val otp: String = "",
    val otpHint: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val completed: Boolean = false
)
