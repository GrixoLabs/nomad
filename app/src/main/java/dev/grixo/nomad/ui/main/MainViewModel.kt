package dev.grixo.nomad.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.grixo.nomad.data.datastore.PreferenceManager
import dev.grixo.nomad.data.location.LocationBus
import dev.grixo.nomad.data.network.NomadApi
import dev.grixo.nomad.domain.model.OnboardingStatus
import dev.grixo.nomad.domain.repository.DeviceRepository
import dev.grixo.nomad.domain.repository.SignalRepository
import dev.grixo.nomad.domain.repository.UserRepository
import dev.grixo.nomad.worker.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val signalRepository: SignalRepository,
    private val userRepository: UserRepository,
    private val preferenceManager: PreferenceManager,
    private val api: NomadApi,
    private val workManager: WorkManager,
    private val locationBus: LocationBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            deviceRepository.initializeDevice()
            checkBackendHealth()
            val enabled = preferenceManager.trackingEnabled.first()
            val notifVisible = preferenceManager.trackingNotificationVisible.first()
            _uiState.update {
                it.copy(
                    isTracking = enabled,
                    notificationVisible = notifVisible,
                    shouldAutoStartTracking = enabled
                )
            }
        }
        viewModelScope.launch {
            preferenceManager.trackingNotificationVisible.collect { visible ->
                _uiState.update { it.copy(notificationVisible = visible) }
            }
        }
        viewModelScope.launch {
            preferenceManager.trackingEnabled.collect { enabled ->
                _uiState.update { it.copy(isTracking = enabled) }
            }
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
        viewModelScope.launch {
            locationBus.events.collect { event ->
                _uiState.update {
                    it.copy(
                        latitude = event.latitude,
                        longitude = event.longitude,
                        accuracy = event.accuracyM,
                        altitudeM = event.altitudeM,
                        batteryPercent = event.batteryPercent,
                        networkType = event.networkType,
                        lastUploadTime = if (event.uploaded) {
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        } else {
                            it.lastUploadTime
                        },
                        isTracking = true
                    )
                }
                refreshPlaceContext(event.latitude, event.longitude)
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
        viewModelScope.launch {
            preferenceManager.setTrackingEnabled(isTracking)
            _uiState.update {
                it.copy(isTracking = isTracking, shouldAutoStartTracking = false)
            }
        }
    }

    fun consumeAutoStart() {
        _uiState.update { it.copy(shouldAutoStartTracking = false) }
    }

    fun setNotificationVisible(visible: Boolean) {
        viewModelScope.launch {
            preferenceManager.setTrackingNotificationVisible(visible)
            _uiState.update { it.copy(notificationVisible = visible) }
        }
    }

    fun setPermissionDenied(denied: Boolean) {
        _uiState.update { it.copy(permissionDenied = denied) }
    }

    fun setNearbySort(sort: String) {
        val normalized = if (sort == "distance") "distance" else "popularity"
        _uiState.update { it.copy(nearbySort = normalized) }
        if (_uiState.value.showNearby) {
            loadNearbyPlaces()
        }
    }

    fun loadNearbyPlaces() {
        val lat = _uiState.value.latitude
        val lon = _uiState.value.longitude
        if (lat == null || lon == null) {
            _uiState.update { it.copy(errorMessage = "Location not available yet") }
            return
        }
        val sort = _uiState.value.nearbySort
        viewModelScope.launch {
            _uiState.update { it.copy(nearbyLoading = true, showNearby = true, errorMessage = null) }
            try {
                val response = api.nearbyPlaces(lat, lon, 10, sort)
                if (response.isSuccessful && response.body() != null) {
                    val places = response.body()!!.places.map {
                        NearbyPlaceUi(
                            name = it.name,
                            category = it.category,
                            latitude = it.latitude,
                            longitude = it.longitude,
                            distanceKm = it.distance_m?.div(1000.0),
                            popularityScore = it.popularity_score
                        )
                    }
                    _uiState.update {
                        it.copy(
                            nearbyPlaces = places,
                            nearbyLoading = false,
                            nearbySort = response.body()!!.sort
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            nearbyLoading = false,
                            errorMessage = "Could not load nearby places (${response.code()})"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        nearbyLoading = false,
                        errorMessage = e.message ?: "Nearby places request failed"
                    )
                }
            }
        }
    }

    fun hideNearby() {
        _uiState.update { it.copy(showNearby = false) }
    }

    fun logout() {
        viewModelScope.launch {
            userRepository.logout()
            _uiState.update { it.copy(loggedOut = true, isTracking = false) }
        }
    }

    fun consumeLogout() {
        _uiState.update { it.copy(loggedOut = false) }
    }

    private fun refreshPlaceContext(lat: Double, lon: Double) {
        viewModelScope.launch {
            refreshPlaceContextSuspend(lat, lon)
            checkBackendHealth()
        }
    }

    private suspend fun refreshPlaceContextSuspend(lat: Double, lon: Double) {
        _uiState.update { it.copy(contextLoading = true) }
        try {
            val place = api.resolvePlace(lat, lon)
            if (place.isSuccessful && place.body() != null) {
                val body = place.body()!!
                val label = body.area_label
                    ?: listOfNotNull(body.locality, body.city, body.region, body.country)
                        .distinct()
                        .joinToString(", ")
                        .ifBlank { body.display_name }
                _uiState.update { it.copy(placeLabel = label) }
            }
        } catch (_: Exception) {
            // keep previous label
        }
        try {
            val weather = api.getWeather(lat, lon)
            if (weather.isSuccessful && weather.body() != null) {
                val body = weather.body()!!
                _uiState.update {
                    it.copy(
                        weatherSummary = body.summary,
                        temperatureC = body.temperature_c
                    )
                }
            }
        } catch (_: Exception) {
            // keep previous weather
        }
        _uiState.update { it.copy(contextLoading = false) }
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
        checkBackendHealth()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            try {
                checkBackendHealth()
                triggerManualSync()
                val lat = _uiState.value.latitude
                val lon = _uiState.value.longitude
                if (lat != null && lon != null) {
                    refreshPlaceContextSuspend(lat, lon)
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    companion object {
        /** Display precision only — DB keeps full GPS precision. */
        fun formatCoord(value: Double): String =
            String.format(Locale.US, "%.3f", value)
    }
}
