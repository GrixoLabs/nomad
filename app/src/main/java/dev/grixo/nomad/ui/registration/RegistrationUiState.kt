package dev.grixo.nomad.ui.registration

import dev.grixo.nomad.domain.model.Gender

data class RegistrationUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val age: String = "",
    val gender: Gender = Gender.PREFER_NOT,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val completed: Boolean = false
)
