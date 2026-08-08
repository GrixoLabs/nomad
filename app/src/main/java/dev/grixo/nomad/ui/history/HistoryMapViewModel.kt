package dev.grixo.nomad.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.grixo.nomad.data.network.model.HistoryResponse
import dev.grixo.nomad.domain.repository.JournalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class HistoryMapUiState(
    val days: Int = 7,
    val loading: Boolean = true,
    val tileUrlTemplate: String? = null,
    val historyJson: String? = null,
    val history: HistoryResponse? = null,
    val errorMessage: String? = null,
    val detailTitle: String? = null,
    val detailBody: String? = null
)

@HiltViewModel
class HistoryMapViewModel @Inject constructor(
    private val journalRepository: JournalRepository
) : ViewModel() {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(HistoryMapUiState())
    val uiState: StateFlow<HistoryMapUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun setDays(days: Int) {
        _uiState.update { it.copy(days = days.coerceIn(1, 14)) }
        reload()
    }

    fun dismissDetail() {
        _uiState.update { it.copy(detailTitle = null, detailBody = null) }
    }

    fun selectJournal(id: Long) {
        val pin = _uiState.value.history?.journal_pins?.firstOrNull { it.entry_id == id } ?: return
        _uiState.update {
            it.copy(
                detailTitle = pin.created_at.take(16).replace('T', ' '),
                detailBody = buildString {
                    pin.place_label?.let { label -> append(label).append("\n\n") }
                    append(pin.body)
                }
            )
        }
    }

    fun selectNight(id: Long) {
        val night = _uiState.value.history?.night_stays?.firstOrNull { it.night_stay_id == id }
            ?: return
        _uiState.update {
            it.copy(
                detailTitle = "Night · ${night.stay_date}",
                detailBody = buildString {
                    append("Idle ${"%.1f".format(night.idle_hours)} h\n")
                    append(night.started_at.take(16).replace('T', ' '))
                    append(" → ")
                    append(night.ended_at.take(16).replace('T', ' '))
                    append("\n\n")
                    append(night.weather_summary ?: "Weather unavailable")
                    night.temperature_c?.let { t -> append(" · ${"%.0f".format(t)}°C") }
                }
            )
        }
    }

    private fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null, detailTitle = null) }
            val mapConfig = journalRepository.loadMapConfig()
            val history = journalRepository.loadHistory(_uiState.value.days)
            if (mapConfig.isFailure) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = mapConfig.exceptionOrNull()?.message
                            ?: "Map config unavailable (set STADIA_API on server)"
                    )
                }
                return@launch
            }
            if (history.isFailure) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = history.exceptionOrNull()?.message ?: "History failed"
                    )
                }
                return@launch
            }
            val hist = history.getOrThrow()
            _uiState.update {
                it.copy(
                    loading = false,
                    tileUrlTemplate = mapConfig.getOrThrow().tile_url_template,
                    history = hist,
                    historyJson = json.encodeToString(hist)
                )
            }
        }
    }
}
