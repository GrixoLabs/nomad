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
    fun onAgeChange(value: String) = _uiState.update {
        it.copy(age = value.filter { ch -> ch.isDigit() }.take(3), errorMessage = null)
    }
    fun onGenderChange(value: Gender) = _uiState.update { it.copy(gender = value, errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        val email = state.email.trim().takeIf { it.isNotEmpty() }
        val phone = state.phone.trim().takeIf { it.isNotEmpty() }
        val age = state.age.toIntOrNull()

        val error = when {
            state.name.isBlank() -> "Name is required"
            email == null && phone == null -> "Add an email or phone number"
            age == null || age !in 13..120 -> "Enter an age between 13 and 120"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(errorMessage = error) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = userRepository.register(
                UserProfile(
                    name = state.name.trim(),
                    email = email,
                    phone = phone,
                    age = age!!,
                    gender = state.gender
                )
            )
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isSubmitting = false, completed = true)
                } else {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Could not save profile"
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
}
