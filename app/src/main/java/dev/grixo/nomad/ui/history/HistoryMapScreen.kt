package dev.grixo.nomad.ui.history

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.grixo.nomad.data.network.model.HistoryResponse
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
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
        onDateRangeChange = viewModel::setDateRange,
        onClose = onClose,
        onRefresh = viewModel::reload,
        onDismissDetail = viewModel::dismissDetail,
        onSelectPlot = viewModel::selectPlot,
        onSelectJournal = viewModel::selectJournal,
        onPrevJournal = viewModel::prevJournal,
        onNextJournal = viewModel::nextJournal
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryMapScreen(
    state: HistoryMapUiState,
    onDaysChange: (Int) -> Unit,
    onDateRangeChange: (LocalDate, LocalDate) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onDismissDetail: () -> Unit,
    onSelectPlot: (Long) -> Unit,
    onSelectJournal: (Long) -> Unit,
    onPrevJournal: () -> Unit,
    onNextJournal: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val earliest = remember(today) { today.minusDays(89) }
    val dateLabel = DateTimeFormatter.ISO_LOCAL_DATE
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var draftStart by remember { mutableStateOf(state.startDate ?: today.minusDays(6)) }
    var draftEnd by remember { mutableStateOf(state.endDate ?: today) }

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
                "Travel History",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(7, 14, 30, 90).forEach { d ->
                FilterChip(
                    selected = state.startDate == null && state.endDate == null && state.days == d,
                    onClick = { onDaysChange(d) },
                    label = { Text("${d}d") }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    draftStart = state.startDate ?: today.minusDays((state.days - 1).toLong())
                    pickingStart = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "From " + (state.startDate ?: today.minusDays((state.days - 1).toLong()))
                        .format(dateLabel),
                    maxLines = 1
                )
            }
            OutlinedButton(
                onClick = {
                    draftEnd = state.endDate ?: today
                    pickingEnd = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "To " + (state.endDate ?: today).format(dateLabel),
                    maxLines = 1
                )
            }
        }

        if (pickingStart) {
            HistoryDatePickerDialog(
                initial = draftStart,
                minDate = earliest,
                maxDate = draftEnd.coerceAtMost(today),
                onDismiss = { pickingStart = false },
                onConfirm = { picked ->
                    draftStart = picked
                    pickingStart = false
                    onDateRangeChange(picked, draftEnd.coerceAtLeast(picked))
                }
            )
        }
        if (pickingEnd) {
            HistoryDatePickerDialog(
                initial = draftEnd,
                minDate = draftStart.coerceAtLeast(earliest),
                maxDate = today,
                onDismiss = { pickingEnd = false },
                onConfirm = { picked ->
                    draftEnd = picked
                    pickingEnd = false
                    onDateRangeChange(draftStart.coerceAtMost(picked), picked)
                }
            )
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
                            onPlotClick = onSelectPlot,
                            onJournalClick = onSelectJournal
                        )
                    }
                }
            }
        }

        state.detailTitle?.let { title ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .background(colors.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
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
                if (state.journalCarousel.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onPrevJournal) {
                            Text("<", color = colors.primary)
                        }
                        Text(
                            "${state.journalIndex + 1} / ${state.journalCarousel.size}",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge
                        )
                        TextButton(onClick = onNextJournal) {
                            Text(">", color = colors.primary)
                        }
                    }
                }
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
    onPlotClick: (Long) -> Unit,
    onJournalClick: (Long) -> Unit
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
                            handleMapClick(map, point, onPlotClick, onJournalClick)
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
            LineLayer(LAYER_PATH, SOURCE_TRACKS).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#0B1F4A")),
                PropertyFactory.lineWidth(3.5f),
                PropertyFactory.lineOpacity(0.95f)
            )
        )
    }

    if (style.getSource(SOURCE_PLOTS) == null) {
        style.addSource(GeoJsonSource(SOURCE_PLOTS, plotCollection(history)))

        // Day/travel plot points: blue concentric ring + dot (non-night).
        style.addLayer(
            CircleLayer(LAYER_PLOT_RING, SOURCE_PLOTS).withProperties(
                PropertyFactory.circleRadius(13f),
                PropertyFactory.circleColor(Color.TRANSPARENT),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(Color.parseColor("#2563EB"))
            ).withFilter(Expression.eq(Expression.get("night_stayed"), Expression.literal(0)))
        )
        style.addLayer(
            CircleLayer(LAYER_PLOTS, SOURCE_PLOTS).withProperties(
                PropertyFactory.circleRadius(4.5f),
                PropertyFactory.circleColor(Color.parseColor("#1D4ED8")),
                PropertyFactory.circleStrokeWidth(0f)
            ).withFilter(Expression.eq(Expression.get("night_stayed"), Expression.literal(0)))
        )

        // Night stays: amber/gold concentric circles (outer + mid + center).
        style.addLayer(
            CircleLayer(LAYER_NIGHT_OUTER, SOURCE_PLOTS).withProperties(
                PropertyFactory.circleRadius(20f),
                PropertyFactory.circleColor(Color.parseColor("#33F59E0B")),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(Color.parseColor("#B45309"))
            ).withFilter(Expression.eq(Expression.get("night_stayed"), Expression.literal(1)))
        )
        style.addLayer(
            CircleLayer(LAYER_NIGHT_RING, SOURCE_PLOTS).withProperties(
                PropertyFactory.circleRadius(12f),
                PropertyFactory.circleColor(Color.TRANSPARENT),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(Color.parseColor("#D97706"))
            ).withFilter(Expression.eq(Expression.get("night_stayed"), Expression.literal(1)))
        )
        style.addLayer(
            CircleLayer(LAYER_NIGHTS, SOURCE_PLOTS).withProperties(
                PropertyFactory.circleRadius(5f),
                PropertyFactory.circleColor(Color.parseColor("#92400E")),
                PropertyFactory.circleStrokeWidth(0f)
            ).withFilter(Expression.eq(Expression.get("night_stayed"), Expression.literal(1)))
        )
    } else {
        (style.getSource(SOURCE_PLOTS) as? GeoJsonSource)?.setGeoJson(plotCollection(history))
    }

    ensureJournalPinImage(style)
    if (style.getSource(SOURCE_JOURNALS) == null) {
        style.addSource(GeoJsonSource(SOURCE_JOURNALS, journalCollection(history)))
        style.addLayer(
            SymbolLayer(LAYER_JOURNALS, SOURCE_JOURNALS).withProperties(
                PropertyFactory.iconImage(JOURNAL_PIN_IMAGE),
                PropertyFactory.iconSize(1.05f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor("bottom")
            )
        )
    } else {
        (style.getSource(SOURCE_JOURNALS) as? GeoJsonSource)?.setGeoJson(journalCollection(history))
    }
}

private fun updateHistoryLayers(style: Style, history: HistoryResponse) {
    (style.getSource(SOURCE_TRACKS) as? GeoJsonSource)?.setGeoJson(trackCollection(history))
    (style.getSource(SOURCE_PLOTS) as? GeoJsonSource)?.setGeoJson(plotCollection(history))
    ensureJournalPinImage(style)
    (style.getSource(SOURCE_JOURNALS) as? GeoJsonSource)?.setGeoJson(journalCollection(history))
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

private fun plotCollection(history: HistoryResponse): FeatureCollection {
    val features = history.plot_points.map { plot ->
        Feature.fromGeometry(Point.fromLngLat(plot.longitude, plot.latitude)).also {
            it.addNumberProperty("plot_id", plot.plot_id)
            // Number (0/1) filters are more reliable than boolean in MapLibre Android.
            it.addNumberProperty("night_stayed", if (plot.night_stayed) 1 else 0)
            it.addNumberProperty("journal_count", plot.journal_count)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun journalCollection(history: HistoryResponse): FeatureCollection {
    // Prefer dedicated journal_pins so journals show even when dwell < 30 minutes.
    val pins = if (history.journal_pins.isNotEmpty()) {
        history.journal_pins
    } else {
        history.plot_points.flatMap { plot -> plot.journals }
    }
    val features = pins.map { journal ->
        Feature.fromGeometry(Point.fromLngLat(journal.longitude, journal.latitude)).also {
            it.addNumberProperty("entry_id", journal.entry_id)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun ensureJournalPinImage(style: Style) {
    if (style.getImage(JOURNAL_PIN_IMAGE) != null) return
    style.addImage(JOURNAL_PIN_IMAGE, createRedPinBitmap())
}

private fun createRedPinBitmap(): Bitmap {
    val width = 72
    val height = 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.parseColor("#DC2626")
    val cx = width / 2f
    val cy = height * 0.38f
    val radius = width * 0.30f
    canvas.drawCircle(cx, cy, radius, paint)
    val tip = Path().apply {
        moveTo(cx - radius * 0.72f, cy + radius * 0.35f)
        lineTo(cx, height * 0.95f)
        lineTo(cx + radius * 0.72f, cy + radius * 0.35f)
        close()
    }
    canvas.drawPath(tip, paint)
    paint.color = Color.WHITE
    canvas.drawCircle(cx, cy, radius * 0.38f, paint)
    return bitmap
}

private fun fitToHistory(map: org.maplibre.android.maps.MapLibreMap, history: HistoryResponse) {
    val points = buildList {
        addAll(history.plot_points.map { LatLng(it.latitude, it.longitude) })
        addAll(history.journal_pins.map { LatLng(it.latitude, it.longitude) })
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
    onPlotClick: (Long) -> Unit,
    onJournalClick: (Long) -> Unit
): Boolean {
    val screen = map.projection.toScreenLocation(point)
    val journalHits = map.queryRenderedFeatures(screen, LAYER_JOURNALS)
    val journalFeature = journalHits.firstOrNull()
    if (journalFeature != null) {
        journalFeature.getNumberProperty("entry_id")?.toLong()?.let(onJournalClick)
        return true
    }
    val plotHits = map.queryRenderedFeatures(
        screen,
        LAYER_NIGHTS,
        LAYER_NIGHT_RING,
        LAYER_NIGHT_OUTER,
        LAYER_PLOT_RING,
        LAYER_PLOTS
    )
    plotHits.firstOrNull()?.getNumberProperty("plot_id")?.toLong()?.let {
        onPlotClick(it)
        return true
    }
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDatePickerDialog(
    initial: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val initialMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val minMillis = minDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val maxMillis = maxDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis.coerceIn(minMillis, maxMillis),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis in minMillis..maxMillis
            }
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date)
                }
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

private const val SOURCE_TRACKS = "nomad-tracks"
private const val SOURCE_PLOTS = "nomad-plots"
private const val SOURCE_JOURNALS = "nomad-journals"
private const val LAYER_PATH = "nomad-path"
private const val LAYER_PLOT_RING = "nomad-plots-ring"
private const val LAYER_PLOTS = "nomad-plots-dot"
private const val LAYER_JOURNALS = "nomad-journals-layer"
private const val LAYER_NIGHTS = "nomad-nights-dot"
private const val LAYER_NIGHT_RING = "nomad-nights-ring"
private const val LAYER_NIGHT_OUTER = "nomad-nights-outer"
private const val JOURNAL_PIN_IMAGE = "nomad-journal-pin"
