package dev.grixo.nomad.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.grixo.nomad.data.location.LocationBus
import dev.grixo.nomad.domain.repository.JournalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JournalUiState(
    val body: String = "",
    val placeLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val savedMessage: String? = null
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val locationBus: LocationBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val event = locationBus.events.firstOrNull()
            if (event != null) {
                _uiState.update {
                    it.copy(
                        latitude = event.latitude,
                        longitude = event.longitude
                    )
                }
            }
        }
    }

    fun onBodyChange(value: String) {
        _uiState.update {
            it.copy(body = value.take(500), errorMessage = null, savedMessage = null)
        }
    }

    fun save() {
        val state = _uiState.value
        val lat = state.latitude
        val lon = state.longitude
        if (lat == null || lon == null) {
            _uiState.update { it.copy(errorMessage = "Location not available yet") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, errorMessage = null) }
            val result = journalRepository.createEntry(
                body = state.body,
                latitude = lat,
                longitude = lon,
                placeLabel = state.placeLabel
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            saving = false,
                            body = "",
                            savedMessage = "Saved — pin will appear on your history map"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(saving = false, errorMessage = err.message ?: "Save failed")
                    }
                }
            )
        }
    }
}
