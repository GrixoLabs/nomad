package dev.grixo.nomad.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.grixo.nomad.data.network.NomadApi
import dev.grixo.nomad.domain.model.OnboardingStatus
import dev.grixo.nomad.domain.repository.DeviceRepository
import dev.grixo.nomad.domain.repository.SignalRepository
import dev.grixo.nomad.domain.repository.UserRepository
import dev.grixo.nomad.worker.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val signalRepository: SignalRepository,
    private val userRepository: UserRepository,
    private val api: NomadApi,
    private val workManager: WorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            deviceRepository.initializeDevice()
            checkBackendHealth()
        }
        viewModelScope.launch {
            userRepository.observeOnboardingStatus().collect { status ->
                _uiState.update {
                    it.copy(isRegistered = status == OnboardingStatus.REGISTERED)
                }
            }
        }
        viewModelScope.launch {
            userRepository.observeProfile().collect { profile ->
                _uiState.update { it.copy(userName = profile?.name) }
            }
        }
        viewModelScope.launch {
            userRepository.observeJournalEnabled().collect { enabled ->
                _uiState.update { it.copy(journalEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            signalRepository.getAllOfflineSignals().collect { offline ->
                _uiState.update { it.copy(offlineQueueCount = offline.size) }
            }
        }
    }

    fun checkBackendHealth() {
        viewModelScope.launch {
            try {
                val response = api.checkHealth()
                _uiState.update { it.copy(isConnected = response.isSuccessful) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isConnected = false) }
            }
        }
    }

    fun setTrackingStatus(isTracking: Boolean) {
        _uiState.update { it.copy(isTracking = isTracking) }
    }

    fun setPermissionDenied(denied: Boolean) {
        _uiState.update { it.copy(permissionDenied = denied) }
    }

    fun updateLiveSignal(
        latitude: Double?,
        longitude: Double?,
        accuracy: Float?,
        batteryPercent: Int?,
        networkType: String,
        uploaded: Boolean
    ) {
        _uiState.update {
            it.copy(
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                batteryPercent = batteryPercent,
                networkType = networkType,
                lastUploadTime = if (uploaded) {
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                } else {
                    it.lastUploadTime
                }
            )
        }
    }

    fun triggerManualSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "nomad_manual_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
