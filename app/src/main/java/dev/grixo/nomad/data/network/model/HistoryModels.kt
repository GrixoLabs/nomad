package dev.grixo.nomad.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class JournalCreateRequest(
    val device_uuid: String,
    val body: String,
    val latitude: Double,
    val longitude: Double,
    val place_label: String? = null
)

@Serializable
data class JournalEntryResponse(
    val entry_id: Long,
    val latitude: Double,
    val longitude: Double,
    val place_label: String? = null,
    val body: String,
    val created_at: String
)

@Serializable
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude_m: Double? = null,
    val captured_at: String,
    val speed_mps: Double? = null
)

@Serializable
data class TrackSegment(
    val kind: String,
    val points: List<TrackPoint> = emptyList()
)

@Serializable
data class NightStayResponse(
    val night_stay_id: Long,
    val stay_date: String,
    val latitude: Double,
    val longitude: Double,
    val started_at: String,
    val ended_at: String,
    val idle_hours: Double,
    val weather_summary: String? = null,
    val temperature_c: Double? = null
)

@Serializable
data class PlotPointResponse(
    val plot_id: Long,
    val latitude: Double,
    val longitude: Double,
    val first_gps_timestamp: String,
    val last_gps_timestamp: String,
    val total_time_hours: Double = 0.0,
    val visit_count: Int = 1,
    val journal_count: Int = 0,
    val night_stayed: Boolean = false,
    val place_label: String? = null,
    val journals: List<JournalEntryResponse> = emptyList()
)

@Serializable
data class HistoryResponse(
    val days: Int,
    val plot_points: List<PlotPointResponse> = emptyList(),
    val segments: List<TrackSegment> = emptyList(),
    val journal_pins: List<JournalEntryResponse> = emptyList(),
    val night_stays: List<NightStayResponse> = emptyList()
)

@Serializable
data class MapConfigResponse(
    val tile_url_template: String,
    val attribution: String = "",
    val style: String = "alidade_smooth",
    val style_url: String? = null
)
