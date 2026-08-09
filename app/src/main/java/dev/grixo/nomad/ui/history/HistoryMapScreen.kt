package dev.grixo.nomad.ui.history

import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.grixo.nomad.data.network.model.HistoryResponse
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
fun HistoryMapRoute(
    onClose: () -> Unit,
    viewModel: HistoryMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    HistoryMapScreen(
        state = state,
        onDaysChange = viewModel::setDays,
        onClose = onClose,
        onRefresh = viewModel::reload,
        onDismissDetail = viewModel::dismissDetail,
        onSelectJournal = viewModel::selectJournal,
        onSelectNight = viewModel::selectNight
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryMapScreen(
    state: HistoryMapUiState,
    onDaysChange: (Int) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onDismissDetail: () -> Unit,
    onSelectJournal: (Long) -> Unit,
    onSelectNight: (Long) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) {
                Text("← Back", color = colors.primary)
            }
            Text(
                "History map",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 3, 7, 14).forEach { d ->
                FilterChip(
                    selected = state.days == d,
                    onClick = { onDaysChange(d) },
                    label = { Text("${d}d") }
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading && state.history == null -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.errorMessage != null && state.history == null -> {
                        Text(
                            state.errorMessage,
                            color = colors.error,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        )
                    }
                    state.styleUrl != null || state.tileUrlTemplate != null -> {
                        MapLibreHistoryMap(
                            styleUrl = state.styleUrl,
                            tileUrlTemplate = state.tileUrlTemplate,
                            attribution = state.attribution,
                            history = state.history ?: HistoryResponse(days = state.days),
                            onJournalClick = onSelectJournal,
                            onNightClick = onSelectNight
                        )
                    }
                }
            }
        }

        state.detailTitle?.let { title ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .background(colors.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissDetail) {
                        Text("Close", color = colors.primary)
                    }
                }
                Text(
                    state.detailBody.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
            }
        }
    }
}

@Composable
private fun MapLibreHistoryMap(
    styleUrl: String?,
    tileUrlTemplate: String?,
    attribution: String,
    history: HistoryResponse,
    onJournalClick: (Long) -> Unit,
    onNightClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    var mapReady by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // If already past CREATE, bring MapView up.
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { view ->
            if (!mapReady) {
                view.getMapAsync { map ->
                    mapReady = true
                    val styleBuilder = when {
                        !styleUrl.isNullOrBlank() -> Style.Builder().fromUri(styleUrl)
                        !tileUrlTemplate.isNullOrBlank() -> Style.Builder().fromJson(
                            rasterStyleJson(tileUrlTemplate, attribution)
                        )
                        else -> return@getMapAsync
                    }
                    map.setStyle(styleBuilder) { style ->
                        addHistoryLayers(style, history)
                        fitToHistory(map, history)
                        map.addOnMapClickListener { point ->
                            handleMapClick(map, point, onJournalClick, onNightClick)
                        }
                    }
                }
            } else {
                view.getMapAsync { map ->
                    map.getStyle { style ->
                        updateHistoryLayers(style, history)
                        fitToHistory(map, history)
                    }
                }
            }
        }
    )
}

private fun rasterStyleJson(tileUrl: String, attribution: String): String {
    val safeTiles = tileUrl.replace("\\", "\\\\").replace("\"", "\\\"")
    val safeAttr = attribution.replace("\\", "\\\\").replace("\"", "\\\"")
    return """
    {
      "version": 8,
      "name": "Nomad Stadia Raster",
      "sources": {
        "stadia": {
          "type": "raster",
          "tiles": ["$safeTiles"],
          "tileSize": 256,
          "attribution": "$safeAttr"
        }
      },
      "layers": [
        { "id": "stadia", "type": "raster", "source": "stadia" }
      ]
    }
    """.trimIndent()
}

private fun addHistoryLayers(style: Style, history: HistoryResponse) {
    if (style.getSource(SOURCE_TRACKS) == null) {
        style.addSource(GeoJsonSource(SOURCE_TRACKS, trackCollection(history)))
        style.addLayer(
            LineLayer(LAYER_IDLE, SOURCE_TRACKS).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#1E3A8A")),
                PropertyFactory.lineWidth(3.5f),
                PropertyFactory.lineOpacity(0.9f)
            ).withFilter(
                Expression.eq(Expression.get("kind"), Expression.literal("idle"))
            )
        )
        style.addLayer(
            LineLayer(LAYER_TRAVEL, SOURCE_TRACKS).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#DC2626")),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineOpacity(0.95f)
            ).withFilter(
                Expression.eq(Expression.get("kind"), Expression.literal("travel"))
            )
        )
    } else {
        updateHistoryLayers(style, history)
        return
    }

    if (style.getSource(SOURCE_JOURNALS) == null) {
        style.addSource(GeoJsonSource(SOURCE_JOURNALS, journalCollection(history)))
        style.addLayer(
            CircleLayer(LAYER_JOURNALS, SOURCE_JOURNALS).withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(Color.parseColor("#DC2626")),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        )
    }

    if (style.getSource(SOURCE_NIGHTS) == null) {
        style.addSource(GeoJsonSource(SOURCE_NIGHTS, nightCollection(history)))
        style.addLayer(
            CircleLayer(LAYER_NIGHTS, SOURCE_NIGHTS).withProperties(
                PropertyFactory.circleRadius(12f),
                PropertyFactory.circleColor(Color.parseColor("#661E3A8A")),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(Color.parseColor("#1E3A8A"))
            )
        )
    }
}

private fun updateHistoryLayers(style: Style, history: HistoryResponse) {
    (style.getSource(SOURCE_TRACKS) as? GeoJsonSource)?.setGeoJson(trackCollection(history))
    (style.getSource(SOURCE_JOURNALS) as? GeoJsonSource)?.setGeoJson(journalCollection(history))
    (style.getSource(SOURCE_NIGHTS) as? GeoJsonSource)?.setGeoJson(nightCollection(history))
}

private fun trackCollection(history: HistoryResponse): FeatureCollection {
    val features = history.segments.mapNotNull { seg ->
        if (seg.points.size < 2) return@mapNotNull null
        val pts = seg.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        Feature.fromGeometry(LineString.fromLngLats(pts)).also {
            it.addStringProperty("kind", seg.kind)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun journalCollection(history: HistoryResponse): FeatureCollection {
    val features = history.journal_pins.map { pin ->
        Feature.fromGeometry(Point.fromLngLat(pin.longitude, pin.latitude)).also {
            it.addNumberProperty("entry_id", pin.entry_id)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun nightCollection(history: HistoryResponse): FeatureCollection {
    val features = history.night_stays.map { night ->
        Feature.fromGeometry(Point.fromLngLat(night.longitude, night.latitude)).also {
            it.addNumberProperty("night_stay_id", night.night_stay_id)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun fitToHistory(map: org.maplibre.android.maps.MapLibreMap, history: HistoryResponse) {
    val points = buildList {
        history.segments.forEach { seg ->
            seg.points.forEach { add(LatLng(it.latitude, it.longitude)) }
        }
        history.journal_pins.forEach { add(LatLng(it.latitude, it.longitude)) }
        history.night_stays.forEach { add(LatLng(it.latitude, it.longitude)) }
    }
    if (points.isEmpty()) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(22.82, 86.22), 11.0))
        return
    }
    if (points.size == 1) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 13.0))
        return
    }
    val bounds = LatLngBounds.Builder().includes(points).build()
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 72))
}

private fun handleMapClick(
    map: org.maplibre.android.maps.MapLibreMap,
    point: LatLng,
    onJournalClick: (Long) -> Unit,
    onNightClick: (Long) -> Unit
): Boolean {
    val screen = map.projection.toScreenLocation(point)
    val journalHits = map.queryRenderedFeatures(screen, LAYER_JOURNALS)
    journalHits.firstOrNull()?.getNumberProperty("entry_id")?.toLong()?.let {
        onJournalClick(it)
        return true
    }
    val nightHits = map.queryRenderedFeatures(screen, LAYER_NIGHTS)
    nightHits.firstOrNull()?.getNumberProperty("night_stay_id")?.toLong()?.let {
        onNightClick(it)
        return true
    }
    return false
}

private const val SOURCE_TRACKS = "nomad-tracks"
private const val SOURCE_JOURNALS = "nomad-journals"
private const val SOURCE_NIGHTS = "nomad-nights"
private const val LAYER_IDLE = "nomad-tracks-idle"
private const val LAYER_TRAVEL = "nomad-tracks-travel"
private const val LAYER_JOURNALS = "nomad-journals-layer"
private const val LAYER_NIGHTS = "nomad-nights-layer"
