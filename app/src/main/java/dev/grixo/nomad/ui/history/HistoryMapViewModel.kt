package dev.grixo.nomad.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.grixo.nomad.data.location.LocationBus
import dev.grixo.nomad.data.network.model.HistoryResponse
import dev.grixo.nomad.data.network.model.JournalEntryResponse
import dev.grixo.nomad.data.network.model.PlotPointResponse
import dev.grixo.nomad.domain.repository.JournalRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryLiveLocation(
    val latitude: Double,
    val longitude: Double,
    val bearingDeg: Float?
)

data class HistoryMapUiState(
    val days: Int = 7,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val loading: Boolean = true,
    val styleUrl: String? = null,
    val tileUrlTemplate: String? = null,
    val attribution: String = "",
    val history: HistoryResponse? = null,
    val errorMessage: String? = null,
    val selectedPlotId: Long? = null,
    val journalIndex: Int = 0,
    val detailTitle: String? = null,
    val detailBody: String? = null,
    val journalCarousel: List<JournalEntryResponse> = emptyList(),
    val liveLocation: HistoryLiveLocation? = null
)

@HiltViewModel
class HistoryMapViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val locationBus: LocationBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryMapUiState())
    val uiState: StateFlow<HistoryMapUiState> = _uiState.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            var prev: HistoryLiveLocation? = null
            locationBus.events.collect { event ->
                val bearing = event.bearingDeg ?: prev?.let { last ->
                    bearingBetween(
                        last.latitude,
                        last.longitude,
                        event.latitude,
                        event.longitude
                    )
                }
                val next = HistoryLiveLocation(
                    latitude = event.latitude,
                    longitude = event.longitude,
                    bearingDeg = bearing
                )
                prev = next
                _uiState.update { it.copy(liveLocation = next) }
            }
        }
    }

    private fun bearingBetween(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float? {
        if (lat1 == lat2 && lon1 == lon2) return null
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δλ = Math.toRadians(lon2 - lon1)
        val y = Math.sin(Δλ) * Math.cos(φ2)
        val x = Math.cos(φ1) * Math.sin(φ2) -
            Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ)
        val deg = Math.toDegrees(Math.atan2(y, x))
        return ((deg + 360.0) % 360.0).toFloat()
    }

    fun setDays(days: Int) {
        _uiState.update {
            it.copy(
                days = days.coerceIn(1, 90),
                startDate = null,
                endDate = null
            )
        }
        reload()
    }

    fun setDateRange(start: LocalDate, end: LocalDate) {
        val today = LocalDate.now()
        val earliest = today.minusDays(89)
        var from = if (start.isBefore(end)) start else end
        var to = if (start.isBefore(end)) end else start
        if (from.isBefore(earliest)) from = earliest
        if (to.isAfter(today)) to = today
        if (from.isAfter(to)) from = to
        val span = ChronoUnit.DAYS.between(from, to).toInt().coerceAtLeast(0) + 1
        _uiState.update {
            it.copy(
                startDate = from,
                endDate = to,
                days = span.coerceIn(1, 90)
            )
        }
        reload()
    }

    fun dismissDetail() {
        _uiState.update {
            it.copy(
                detailTitle = null,
                detailBody = null,
                selectedPlotId = null,
                journalIndex = 0,
                journalCarousel = emptyList()
            )
        }
    }

    fun selectPlot(plotId: Long) {
        val plot = _uiState.value.history?.plot_points?.firstOrNull { it.plot_id == plotId }
            ?: return
        showPlot(plot, journalIndex = 0)
    }

    fun selectJournal(entryId: Long) {
        val history = _uiState.value.history ?: return
        val plot = history.plot_points.firstOrNull { p ->
            p.journals.any { it.entry_id == entryId }
        }
        if (plot != null) {
            val idx = plot.journals.indexOfFirst { it.entry_id == entryId }.coerceAtLeast(0)
            showPlot(plot, idx)
            return
        }
        val pin = history.journal_pins.firstOrNull { it.entry_id == entryId } ?: return
        _uiState.update {
            it.copy(
                selectedPlotId = null,
                journalCarousel = listOf(pin),
                journalIndex = 0,
                detailTitle = pin.created_at.take(16).replace('T', ' '),
                detailBody = buildString {
                    pin.place_label?.let { label -> append(label).append("\n\n") }
                    append(pin.body)
                }
            )
        }
    }

    fun selectNight(plotId: Long) {
        selectPlot(plotId)
    }

    fun nextJournal() {
        val state = _uiState.value
        if (state.journalCarousel.isEmpty()) return
        val next = (state.journalIndex + 1) % state.journalCarousel.size
        applyJournalIndex(next)
    }

    fun prevJournal() {
        val state = _uiState.value
        if (state.journalCarousel.isEmpty()) return
        val prev = if (state.journalIndex == 0) {
            state.journalCarousel.lastIndex
        } else {
            state.journalIndex - 1
        }
        applyJournalIndex(prev)
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                    detailTitle = null,
                    selectedPlotId = null,
                    journalCarousel = emptyList()
                )
            }

            val mapConfig = journalRepository.loadMapConfig()
            val config = mapConfig.getOrNull()
            val tileUrl = config?.tile_url_template
            val styleUrl = config?.style_url

            if (tileUrl.isNullOrBlank() && styleUrl.isNullOrBlank()) {
                _uiState.update {
                    it.copy(loading = false, errorMessage = "Map unavailable")
                }
                return@launch
            }

            val state = _uiState.value
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            val historyResult = journalRepository.loadHistory(
                days = state.days,
                startDate = state.startDate?.format(formatter),
                endDate = state.endDate?.format(formatter)
            )
            if (historyResult.isFailure) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = historyResult.exceptionOrNull()?.message
                            ?: "History unavailable"
                    )
                }
                return@launch
            }
            val hist = historyResult.getOrThrow()

            _uiState.update {
                it.copy(
                    loading = false,
                    styleUrl = styleUrl,
                    tileUrlTemplate = tileUrl,
                    attribution = config?.attribution.orEmpty(),
                    history = hist,
                    errorMessage = null
                )
            }
        }
    }

    private fun showPlot(plot: PlotPointResponse, journalIndex: Int) {
        val journals = plot.journals
        val idx = journalIndex.coerceIn(0, (journals.size - 1).coerceAtLeast(0))
        val journal = journals.getOrNull(idx)
        _uiState.update {
            it.copy(
                selectedPlotId = plot.plot_id,
                journalCarousel = journals,
                journalIndex = idx,
                detailTitle = when {
                    journal != null -> journal.created_at.take(16).replace('T', ' ')
                    plot.night_stayed -> "Night stay"
                    else -> plot.place_label ?: "Location"
                },
                detailBody = buildString {
                    plot.place_label?.let { label -> append(label).append('\n') }
                    append("Dwell ${"%.1f".format(plot.total_time_hours)} h")
                    if (plot.night_stayed) append(" · night stayed")
                    append(" · visits ${plot.visit_count}")
                    if (journal != null) {
                        append("\n\n")
                        append(journal.body)
                    } else if (plot.journal_count > 0) {
                        append("\n\n${plot.journal_count} journal(s)")
                    }
                }
            )
        }
    }

    private fun applyJournalIndex(index: Int) {
        val state = _uiState.value
        val journal = state.journalCarousel.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                journalIndex = index,
                detailTitle = journal.created_at.take(16).replace('T', ' '),
                detailBody = buildString {
                    val plot = it.history?.plot_points?.firstOrNull { p ->
                        p.plot_id == it.selectedPlotId
                    }
                    plot?.place_label?.let { label -> append(label).append("\n\n") }
                        ?: journal.place_label?.let { label -> append(label).append("\n\n") }
                    append(journal.body)
                }
            )
        }
    }
}
