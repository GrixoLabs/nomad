package dev.grixo.nomad.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.grixo.nomad.domain.model.OnboardingStatus
import dev.grixo.nomad.domain.repository.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OnboardingGateViewModel @Inject constructor(
    userRepository: UserRepository
) : ViewModel() {

    val status: StateFlow<OnboardingStatus?> = userRepository
        .observeOnboardingStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
