package dev.grixo.nomad.ui.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.grixo.nomad.domain.model.Gender
import dev.grixo.nomad.domain.model.UserProfile
import dev.grixo.nomad.domain.repository.DeviceRepository
import dev.grixo.nomad.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            deviceRepository.initializeDevice()
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, errorMessage = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value, errorMessage = null) }
    fun onContactChange(value: String) =
        _uiState.update { it.copy(contact = value, errorMessage = null) }
    fun onAgeChange(value: String) = _uiState.update {
        it.copy(age = value.filter { ch -> ch.isDigit() }.take(3), errorMessage = null)
    }
    fun onGenderChange(value: Gender) = _uiState.update { it.copy(gender = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    fun onOtpChange(value: String) =
        _uiState.update { it.copy(otp = value.filter { ch -> ch.isDigit() }.take(8), errorMessage = null) }

    fun showSignIn() {
        _uiState.update {
            it.copy(
                step = RegistrationStep.SIGN_IN,
                password = "",
                confirmPassword = "",
                otp = "",
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun showForgotPassword() {
        _uiState.update {
            it.copy(
                step = RegistrationStep.FORGOT,
                password = "",
                confirmPassword = "",
                otp = "",
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun showRegister() {
        _uiState.update {
            it.copy(
                step = RegistrationStep.PROFILE,
                password = "",
                confirmPassword = "",
                otp = "",
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun backFromOtp() {
        _uiState.update {
            it.copy(step = RegistrationStep.PROFILE, otp = "", errorMessage = null, infoMessage = null)
        }
    }

    fun backFromAuthSecondary() {
        _uiState.update {
            when (it.step) {
                RegistrationStep.RESET -> it.copy(
                    step = RegistrationStep.FORGOT,
                    otp = "",
                    password = "",
                    confirmPassword = "",
                    errorMessage = null,
                    infoMessage = null
                )
                RegistrationStep.FORGOT -> it.copy(
                    step = RegistrationStep.SIGN_IN,
                    otp = "",
                    password = "",
                    confirmPassword = "",
                    errorMessage = null,
                    infoMessage = null
                )
                RegistrationStep.SIGN_IN, RegistrationStep.OTP -> it.copy(
                    step = RegistrationStep.PROFILE,
                    otp = "",
                    password = "",
                    confirmPassword = "",
                    errorMessage = null,
                    infoMessage = null
                )
                else -> it
            }
        }
    }

    fun submitProfile() {
        val state = _uiState.value
        val email = state.email.trim().takeIf { it.isNotEmpty() }
        val phone = state.phone.trim().takeIf { it.isNotEmpty() }
        val age = state.age.toIntOrNull()

        val error = when {
            state.name.isBlank() -> "Name is required"
            email == null && phone == null -> "Add an email or phone number"
            age == null || age !in 13..120 -> "Enter an age between 13 and 120"
            state.password.length < 8 -> "Password must be at least 8 characters"
            state.password != state.confirmPassword -> "Passwords do not match"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(errorMessage = error) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }
            deviceRepository.initializeDevice()
            val result = userRepository.startRegistration(
                profile = UserProfile(
                    name = state.name.trim(),
                    email = email,
                    phone = phone,
                    age = age!!,
                    gender = state.gender
                ),
                password = state.password
            )
            _uiState.update {
                if (result.isSuccess) {
                    val hint = when {
                        email != null -> "Code sent to $email"
                        else -> "Code sent to $phone"
                    }
                    it.copy(
                        isSubmitting = false,
                        step = RegistrationStep.OTP,
                        otpHint = hint,
                        infoMessage = hint
                    )
                } else {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Could not register"
                    )
                }
            }
        }
    }

    fun verifyOtp() {
        val code = _uiState.value.otp
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = userRepository.verifyRegistrationOtp(code)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isSubmitting = false, completed = true)
                } else {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Verification failed"
                    )
                }
            }
        }
    }

    fun resendOtp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = userRepository.resendRegistrationOtp()
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isSubmitting = false, infoMessage = "Code resent")
                } else {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Could not resend code"
                    )
                }
            }
        }
    }

    fun signIn() {
        val state = _uiState.value
        val (email, phone) = parseContact(state.contact)
        val error = when {
            email == null && phone == null -> "Enter email or phone"
            state.password.length < 8 -> "Password must be at least 8 characters"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(errorMessage = error) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }
            deviceRepository.initializeDevice()
            val result = userRepository.login(email, phone, state.password)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isSubmitting = false, completed = true)
                } else {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Sign in failed"
                    )
                }
            }
        }
    }

    fun sendForgotCode() {
        val state = _uiState.value
        val (email, phone) = parseContact(state.contact)
        if (email == null && phone == null) {
            _uiState.update { it.copy(errorMessage = "Enter email or phone") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }
            val result = userRepository.forgotPassword(email, phone)
            _uiState.update {
                if (result.isSuccess) {
                    val dest = email ?: phone
                    it.copy(
                        isSubmitting = false,
                        step = RegistrationStep.RESET,
                        otpHint = "Code sent to $dest",
                        infoMessage = "Code sent to $dest",
                        password = "",
                        confirmPassword = "",
                        otp = ""
                    )
                } else {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Could not send code"
                    )
                }
            }
        }
    }

    fun submitResetPassword() {
        val state = _uiState.value
        val (email, phone) = parseContact(state.contact)
        val error = when {
            email == null && phone == null -> "Enter email or phone"
            state.otp.length < 4 -> "Enter the verification code"
            state.password.length < 8 -> "Password must be at least 8 characters"
            state.password != state.confirmPassword -> "Passwords do not match"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(errorMessage = error) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }
            val result = userRepository.resetPassword(
                email = email,
                phone = phone,
                otp = state.otp,
                newPassword = state.password
            )
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isSubmitting = false,
                        step = RegistrationStep.SIGN_IN,
                        otp = "",
                        password = "",
                        confirmPassword = "",
                        infoMessage = "Password updated. Sign in."
                    )
                } else {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Reset failed"
                    )
                }
            }
        }
    }

    fun skip() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            userRepository.skipRegistration()
            _uiState.update { it.copy(isSubmitting = false, completed = true) }
        }
    }

    companion object {
        /** Treat values with @ as email; otherwise as phone. */
        fun parseContact(raw: String): Pair<String?, String?> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null to null
            return if (trimmed.contains('@')) {
                trimmed.lowercase() to null
            } else {
                val phone = trimmed.filter { it.isDigit() || it == '+' }
                null to phone.takeIf { it.length >= 8 }
            }
        }
    }
}
