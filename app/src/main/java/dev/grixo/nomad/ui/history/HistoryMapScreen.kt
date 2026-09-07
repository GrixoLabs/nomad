package dev.grixo.nomad.ui.history

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.animation.AccelerateDecelerateInterpolator
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
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
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
                            liveLocation = state.liveLocation,
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
    liveLocation: HistoryLiveLocation?,
    onPlotClick: (Long) -> Unit,
    onJournalClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    var mapReady by remember { mutableStateOf(false) }
    var fittedOnce by remember { mutableStateOf(false) }
    val blinkAnimator = remember {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 750L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    if (!blinkAnimator.isStarted) blinkAnimator.start()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    blinkAnimator.cancel()
                    mapView.onPause()
                }
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        blinkAnimator.start()
        onDispose {
            blinkAnimator.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    DisposableEffect(blinkAnimator, mapView) {
        val listener = ValueAnimator.AnimatorUpdateListener { anim ->
            val t = anim.animatedValue as Float
            // Pulse opacity + halo size so current location clearly blinks red.
            val dotOpacity = 0.45f + (0.55f * t)
            val haloOpacity = 0.15f + (0.40f * t)
            val haloRadius = 12f + (10f * t)
            mapView.getMapAsync { map ->
                map.getStyle { style ->
                    (style.getLayer(LAYER_LIVE_DOT) as? CircleLayer)?.setProperties(
                        PropertyFactory.circleOpacity(dotOpacity)
                    )
                    (style.getLayer(LAYER_LIVE_HALO) as? CircleLayer)?.setProperties(
                        PropertyFactory.circleOpacity(haloOpacity),
                        PropertyFactory.circleRadius(haloRadius)
                    )
                }
            }
        }
        blinkAnimator.addUpdateListener(listener)
        onDispose { blinkAnimator.removeUpdateListener(listener) }
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
                        addHistoryLayers(style, history, liveLocation)
                        if (!fittedOnce) {
                            fitToHistory(map, history, liveLocation)
                            fittedOnce = true
                        }
                        map.addOnMapClickListener { point ->
                            handleMapClick(map, point, onPlotClick, onJournalClick)
                        }
                    }
                }
            } else {
                view.getMapAsync { map ->
                    map.getStyle { style ->
                        updateHistoryLayers(style, history, liveLocation)
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

private fun addHistoryLayers(
    style: Style,
    history: HistoryResponse,
    liveLocation: HistoryLiveLocation?
) {
    val (longStays, overnight) = HistoryMapGeometry.stayMarkers(history)

    // Thick light-blue travel path (great-circle arcs for >1000 km jumps).
    if (style.getSource(SOURCE_TRACKS) == null) {
        style.addSource(GeoJsonSource(SOURCE_TRACKS, HistoryMapGeometry.trackCollection(history)))
        style.addLayer(
            LineLayer(LAYER_PATH, SOURCE_TRACKS).withProperties(
                PropertyFactory.lineColor(Color.parseColor(COLOR_PATH)),
                PropertyFactory.lineWidth(6.5f),
                PropertyFactory.lineOpacity(0.95f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round")
            )
        )
    }

    // ≥1 h within ~500 m — solid dark-red highlight (non-overnight).
    if (style.getSource(SOURCE_STAYS) == null) {
        style.addSource(
            GeoJsonSource(SOURCE_STAYS, HistoryMapGeometry.longStayCollection(longStays))
        )
        style.addLayer(
            CircleLayer(LAYER_STAY_HALO, SOURCE_STAYS).withProperties(
                PropertyFactory.circleRadius(11f),
                PropertyFactory.circleColor(Color.parseColor(COLOR_STAY_HALO)),
                PropertyFactory.circleOpacity(0.35f)
            )
        )
        style.addLayer(
            CircleLayer(LAYER_STAYS, SOURCE_STAYS).withProperties(
                PropertyFactory.circleRadius(6.5f),
                PropertyFactory.circleColor(Color.parseColor(COLOR_STAY)),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        )
    } else {
        (style.getSource(SOURCE_STAYS) as? GeoJsonSource)
            ?.setGeoJson(HistoryMapGeometry.longStayCollection(longStays))
    }

    // Overnight stays — tightened logic, dark-red concentric rings.
    if (style.getSource(SOURCE_PLOTS) == null) {
        style.addSource(
            GeoJsonSource(SOURCE_PLOTS, HistoryMapGeometry.overnightCollection(overnight))
        )
        style.addLayer(
            CircleLayer(LAYER_NIGHT_OUTER, SOURCE_PLOTS).withProperties(
                PropertyFactory.circleRadius(14f),
                PropertyFactory.circleColor(Color.parseColor(COLOR_NIGHT_HALO)),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleStrokeColor(Color.parseColor(COLOR_STAY))
            )
        )
        style.addLayer(
            CircleLayer(LAYER_NIGHT_RING, SOURCE_PLOTS).withProperties(
                PropertyFactory.circleRadius(8.5f),
                PropertyFactory.circleColor(Color.TRANSPARENT),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(Color.parseColor(COLOR_STAY))
            )
        )
        style.addLayer(
            CircleLayer(LAYER_NIGHTS, SOURCE_PLOTS).withProperties(
                PropertyFactory.circleRadius(3.5f),
                PropertyFactory.circleColor(Color.parseColor(COLOR_STAY)),
                PropertyFactory.circleStrokeWidth(0f)
            )
        )
    } else {
        (style.getSource(SOURCE_PLOTS) as? GeoJsonSource)
            ?.setGeoJson(HistoryMapGeometry.overnightCollection(overnight))
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

    ensureLiveLayers(style, liveLocation)
}

private fun updateHistoryLayers(
    style: Style,
    history: HistoryResponse,
    liveLocation: HistoryLiveLocation?
) {
    val (longStays, overnight) = HistoryMapGeometry.stayMarkers(history)
    (style.getSource(SOURCE_TRACKS) as? GeoJsonSource)
        ?.setGeoJson(HistoryMapGeometry.trackCollection(history))
    (style.getSource(SOURCE_STAYS) as? GeoJsonSource)
        ?.setGeoJson(HistoryMapGeometry.longStayCollection(longStays))
    (style.getSource(SOURCE_PLOTS) as? GeoJsonSource)
        ?.setGeoJson(HistoryMapGeometry.overnightCollection(overnight))
    ensureJournalPinImage(style)
    (style.getSource(SOURCE_JOURNALS) as? GeoJsonSource)?.setGeoJson(journalCollection(history))
    ensureLiveLayers(style, liveLocation)
}

private fun ensureLiveLayers(style: Style, liveLocation: HistoryLiveLocation?) {
    val collection = liveCollection(liveLocation)
    if (style.getSource(SOURCE_LIVE) == null) {
        style.addSource(GeoJsonSource(SOURCE_LIVE, collection))
        style.addLayer(
            CircleLayer(LAYER_LIVE_HALO, SOURCE_LIVE).withProperties(
                PropertyFactory.circleRadius(16f),
                PropertyFactory.circleColor(Color.parseColor(COLOR_LIVE)),
                PropertyFactory.circleOpacity(0.35f)
            )
        )
        style.addLayer(
            CircleLayer(LAYER_LIVE_DOT, SOURCE_LIVE).withProperties(
                PropertyFactory.circleRadius(7.5f),
                PropertyFactory.circleColor(Color.parseColor(COLOR_LIVE)),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleOpacity(1f)
            )
        )
    } else {
        (style.getSource(SOURCE_LIVE) as? GeoJsonSource)?.setGeoJson(collection)
    }
}

private fun liveCollection(live: HistoryLiveLocation?): FeatureCollection {
    if (live == null) return FeatureCollection.fromFeatures(emptyArray())
    val feature = Feature.fromGeometry(Point.fromLngLat(live.longitude, live.latitude))
    return FeatureCollection.fromFeatures(arrayOf(feature))
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

private fun fitToHistory(
    map: org.maplibre.android.maps.MapLibreMap,
    history: HistoryResponse,
    liveLocation: HistoryLiveLocation?
) {
    val points = buildList {
        addAll(history.plot_points.map { LatLng(it.latitude, it.longitude) })
        addAll(history.journal_pins.map { LatLng(it.latitude, it.longitude) })
        liveLocation?.let { add(LatLng(it.latitude, it.longitude)) }
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
        LAYER_STAYS,
        LAYER_STAY_HALO
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
private const val SOURCE_STAYS = "nomad-long-stays"
private const val SOURCE_PLOTS = "nomad-plots"
private const val SOURCE_JOURNALS = "nomad-journals"
private const val SOURCE_LIVE = "nomad-live"
private const val LAYER_PATH = "nomad-path"
private const val LAYER_STAY_HALO = "nomad-stay-halo"
private const val LAYER_STAYS = "nomad-stays-dot"
private const val LAYER_JOURNALS = "nomad-journals-layer"
private const val LAYER_NIGHTS = "nomad-nights-dot"
private const val LAYER_NIGHT_RING = "nomad-nights-ring"
private const val LAYER_NIGHT_OUTER = "nomad-nights-outer"
private const val LAYER_LIVE_HALO = "nomad-live-halo"
private const val LAYER_LIVE_DOT = "nomad-live-dot"
private const val JOURNAL_PIN_IMAGE = "nomad-journal-pin"
// Brand-aligned map colors (Color.kt)
private const val COLOR_PATH = "#38BDF8" // light sky blue
private const val COLOR_STAY = "#7F1D1D" // dark red
private const val COLOR_STAY_HALO = "#991B1B"
private const val COLOR_NIGHT_HALO = "#337F1D1D"
private const val COLOR_LIVE = "#DC2626" // NomadDanger
