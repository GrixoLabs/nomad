package dev.grixo.nomad.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.grixo.nomad.data.location.LocationBus
import dev.grixo.nomad.data.network.NomadApi
import dev.grixo.nomad.data.network.model.HistoryResponse
import dev.grixo.nomad.data.network.model.JournalEntryResponse
import dev.grixo.nomad.domain.repository.JournalRepository
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JournalTableRow(
    val entryId: Long,
    /** Date + time for column 1. */
    val dateTimeLabel: String,
    /** Short preview for the list column. */
    val previewText: String,
    val fullBody: String,
    /** Human place name from place_cache / place_label. */
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
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
    val selectedEntry: JournalTableRow? = null,
    val errorMessage: String? = null,
    val savedMessage: String? = null
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val locationBus: LocationBus,
    private val api: NomadApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            locationBus.events.collect { event ->
                _uiState.update {
                    it.copy(
                        latitude = event.latitude,
                        longitude = event.longitude
                    )
                }
                resolvePlaceLabel(event.latitude, event.longitude)
            }
        }
        loadEntries()
    }

    fun onBodyChange(value: String) {
        _uiState.update {
            it.copy(body = value.take(500), errorMessage = null, savedMessage = null)
        }
    }

    fun openEntry(entryId: Long) {
        val entry = _uiState.value.entries.firstOrNull { it.entryId == entryId } ?: return
        _uiState.update { it.copy(selectedEntry = entry) }
    }

    fun dismissEntry() {
        _uiState.update { it.copy(selectedEntry = null) }
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
            // Ensure we attach a place name from place_cache / resolve before save.
            val placeName = state.placeLabel ?: resolvePlaceLabel(lat, lon)
            val result = journalRepository.createEntry(
                body = state.body,
                latitude = lat,
                longitude = lon,
                placeLabel = placeName
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

    private suspend fun resolvePlaceLabel(lat: Double, lon: Double): String? {
        return try {
            val response = api.resolvePlace(lat, lon)
            val body = response.body()
            if (!response.isSuccessful || body == null) return null
            val label = body.area_label
                ?: listOfNotNull(body.locality, body.city, body.region, body.country)
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { body.display_name }
            if (label.isNotBlank()) {
                _uiState.update { it.copy(placeLabel = label) }
                label
            } else {
                null
            }
        } catch (_: Exception) {
            null
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
                            entries = buildJournalTableRows(history),
                            selectedEntry = null
                        )
                    }
                },
                onFailure = {
                    _uiState.update { state -> state.copy(loadingEntries = false) }
                }
            )
        }
    }

    companion object {
        private val dateTimeFormatter =
            DateTimeFormatter.ofPattern("dd MMM yyyy\nHH:mm", Locale.US)

        fun buildJournalTableRows(history: HistoryResponse): List<JournalTableRow> {
            val pins = collectJournals(history)
            val sorted = pins.sortedByDescending { parseCreatedAt(it.created_at) }
            if (sorted.isEmpty()) return emptyList()

            return sorted.mapIndexed { index, entry ->
                val zoned = parseCreatedAt(entry.created_at).atZone(ZoneId.systemDefault())
                val date = zoned.toLocalDate()
                val prevDate = sorted.getOrNull(index - 1)?.let {
                    parseCreatedAt(it.created_at).atZone(ZoneId.systemDefault()).toLocalDate()
                }
                val startsGroup = prevDate != date
                val name = entry.place_label?.takeIf { it.isNotBlank() } ?: "Unknown location"
                JournalTableRow(
                    entryId = entry.entry_id,
                    dateTimeLabel = zoned.format(dateTimeFormatter),
                    previewText = preview(entry.body),
                    fullBody = entry.body,
                    locationName = name,
                    latitude = entry.latitude,
                    longitude = entry.longitude,
                    startsDayGroup = startsGroup
                )
            }
        }

        private fun preview(body: String, maxChars: Int = 90): String {
            val trimmed = body.trim().replace('\n', ' ')
            return if (trimmed.length <= maxChars) trimmed
            else trimmed.take(maxChars - 1).trimEnd() + "…"
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
