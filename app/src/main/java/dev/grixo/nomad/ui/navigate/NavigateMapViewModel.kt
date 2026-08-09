package dev.grixo.nomad.ui.navigate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.grixo.nomad.data.network.NomadApi
import dev.grixo.nomad.data.network.model.RoutePoint
import dev.grixo.nomad.data.network.model.RouteRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

data class NavigateMapUiState(
    val placeName: String = "",
    val originLat: Double? = null,
    val originLon: Double? = null,
    val destLat: Double? = null,
    val destLon: Double? = null,
    val styleUrl: String? = null,
    val tileUrlTemplate: String? = null,
    val attribution: String = "",
    val routePoints: List<RoutePoint> = emptyList(),
    val distanceM: Int? = null,
    val durationSeconds: Int? = null,
    val loading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class NavigateMapViewModel @Inject constructor(
    private val api: NomadApi,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(NavigateMapUiState())
    val uiState: StateFlow<NavigateMapUiState> = _uiState.asStateFlow()

    init {
        val destLat = savedStateHandle.get<String>("destLat")?.toDoubleOrNull()
        val destLon = savedStateHandle.get<String>("destLon")?.toDoubleOrNull()
        val originLat = savedStateHandle.get<String>("originLat")?.toDoubleOrNull()
        val originLon = savedStateHandle.get<String>("originLon")?.toDoubleOrNull()
        val rawName = savedStateHandle.get<String>("name").orEmpty()
        val placeName = runCatching {
            URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
        }.getOrDefault(rawName)

        _uiState.update {
            it.copy(
                placeName = placeName.ifBlank { "Destination" },
                destLat = destLat,
                destLon = destLon,
                originLat = originLat,
                originLon = originLon
            )
        }
        load()
    }

    fun reload() = load()

    private fun load() {
        val state = _uiState.value
        val oLat = state.originLat
        val oLon = state.originLon
        val dLat = state.destLat
        val dLon = state.destLon
        if (oLat == null || oLon == null || dLat == null || dLon == null) {
            _uiState.update {
                it.copy(loading = false, errorMessage = "Missing origin or destination")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            val mapConfig = runCatching { api.mapConfig() }.getOrNull()
            val config = mapConfig?.body()
            if (mapConfig == null || !mapConfig.isSuccessful || config == null) {
                _uiState.update {
                    it.copy(loading = false, errorMessage = "Map unavailable")
                }
                return@launch
            }

            try {
                val response = api.computeRoute(
                    RouteRequest(
                        origin_lat = oLat,
                        origin_lon = oLon,
                        dest_lat = dLat,
                        dest_lon = dLon,
                        travel_mode = "DRIVE"
                    )
                )
                if (!response.isSuccessful || response.body() == null) {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            styleUrl = config.style_url,
                            tileUrlTemplate = config.tile_url_template,
                            attribution = config.attribution,
                            errorMessage = "Could not compute route (${response.code()})"
                        )
                    }
                    return@launch
                }
                val route = response.body()!!
                _uiState.update {
                    it.copy(
                        loading = false,
                        styleUrl = config.style_url,
                        tileUrlTemplate = config.tile_url_template,
                        attribution = config.attribution,
                        routePoints = route.points,
                        distanceM = route.distance_m,
                        durationSeconds = route.duration_seconds,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        styleUrl = config.style_url,
                        tileUrlTemplate = config.tile_url_template,
                        attribution = config.attribution,
                        errorMessage = e.message ?: "Route request failed"
                    )
                }
            }
        }
    }
}
