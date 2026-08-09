package dev.grixo.nomad.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.grixo.nomad.data.location.LocationBus
import dev.grixo.nomad.data.network.model.HistoryResponse
import dev.grixo.nomad.data.network.model.JournalEntryResponse
import dev.grixo.nomad.domain.repository.JournalRepository
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JournalTableRow(
    val entryId: Long,
    /** Calendar date for column 1; blank when continuing the same-day group. */
    val dateLabel: String,
    val entryText: String,
    val showDate: Boolean,
    val startsDayGroup: Boolean
)

data class JournalUiState(
    val body: String = "",
    val placeLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val saving: Boolean = false,
    val loadingEntries: Boolean = true,
    val entries: List<JournalTableRow> = emptyList(),
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
        loadEntries()
    }

    fun onBodyChange(value: String) {
        _uiState.update {
            it.copy(body = value.take(500), errorMessage = null, savedMessage = null)
        }
    }

    fun reloadEntries() {
        loadEntries()
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
                    loadEntries()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(saving = false, errorMessage = err.message ?: "Save failed")
                    }
                }
            )
        }
    }

    private fun loadEntries() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingEntries = true) }
            val result = journalRepository.loadHistory(days = 90)
            result.fold(
                onSuccess = { history ->
                    _uiState.update {
                        it.copy(
                            loadingEntries = false,
                            entries = buildJournalTableRows(history)
                        )
                    }
                },
                onFailure = {
                    // Keep the write form usable even if the list fails to load.
                    _uiState.update { state -> state.copy(loadingEntries = false) }
                }
            )
        }
    }

    companion object {
        private val dateOnlyFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

        fun buildJournalTableRows(history: HistoryResponse): List<JournalTableRow> {
            val pins = collectJournals(history)
            val sorted = pins.sortedByDescending { parseCreatedAt(it.created_at) }
            if (sorted.isEmpty()) return emptyList()

            return sorted.mapIndexed { index, entry ->
                val date = parseCreatedAt(entry.created_at)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                val prevDate = sorted.getOrNull(index - 1)?.let {
                    parseCreatedAt(it.created_at).atZone(ZoneId.systemDefault()).toLocalDate()
                }
                val startsGroup = prevDate != date
                val text = buildString {
                    entry.place_label?.takeIf { it.isNotBlank() }?.let { label ->
                        append(label)
                        append('\n')
                    }
                    append(entry.body)
                }
                JournalTableRow(
                    entryId = entry.entry_id,
                    dateLabel = if (startsGroup) date.format(dateOnlyFormatter) else "",
                    entryText = text,
                    showDate = startsGroup,
                    startsDayGroup = startsGroup
                )
            }
        }

        private fun collectJournals(history: HistoryResponse): List<JournalEntryResponse> {
            return if (history.journal_pins.isNotEmpty()) {
                history.journal_pins
            } else {
                history.plot_points.flatMap { it.journals }
            }
        }

        private fun parseCreatedAt(raw: String): Instant {
            val value = raw.trim()
            return try {
                Instant.parse(value)
            } catch (_: DateTimeParseException) {
                try {
                    OffsetDateTime.parse(value).toInstant()
                } catch (_: DateTimeParseException) {
                    try {
                        LocalDate.parse(value.take(10))
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                    } catch (_: Exception) {
                        Instant.EPOCH
                    }
                }
            }
        }
    }
}
