package dev.grixo.nomad.ui.history

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject

@Composable
fun HistoryMapRoute(
    onClose: () -> Unit,
    viewModel: HistoryMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryMapScreen(
        state = state,
        onDaysChange = viewModel::setDays,
        onClose = onClose,
        onDismissDetail = viewModel::dismissDetail,
        onSelectJournal = viewModel::selectJournal,
        onSelectNight = viewModel::selectNight
    )
}

@Composable
fun HistoryMapScreen(
    state: HistoryMapUiState,
    onDaysChange: (Int) -> Unit,
    onClose: () -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "History map",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) {
                Text("Done", color = colors.primary)
            }
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.errorMessage != null -> Text(
                    state.errorMessage,
                    color = colors.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
                state.tileUrlTemplate != null && state.historyJson != null -> {
                    StadiaMapWebView(
                        tileUrl = state.tileUrlTemplate,
                        historyJson = state.historyJson,
                        onJournalClick = onSelectJournal,
                        onNightClick = onSelectNight
                    )
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun StadiaMapWebView(
    tileUrl: String,
    historyJson: String,
    onJournalClick: (Long) -> Unit,
    onNightClick: (Long) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun onJournal(id: String) {
                            id.toLongOrNull()?.let(onJournalClick)
                        }

                        @android.webkit.JavascriptInterface
                        fun onNight(id: String) {
                            id.toLongOrNull()?.let(onNightClick)
                        }
                    },
                    "NomadBridge"
                )
            }
        },
        update = { webView ->
            val tag = "${tileUrl.hashCode()}:${historyJson.hashCode()}"
            if (webView.tag != tag) {
                webView.tag = tag
                val html = buildMapHtml(tileUrl, historyJson)
                webView.loadDataWithBaseURL(
                    "https://tiles.stadiamaps.com/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

private fun buildMapHtml(tileUrl: String, historyJson: String): String {
    // Colors: navy track #1E3A8A, travel crimson #DC2626, journal pin #DC2626, night #1E3A8A
    val safeTile = JSONObject.quote(tileUrl)
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
<link href="https://unpkg.com/maplibre-gl@3.6.2/dist/maplibre-gl.css" rel="stylesheet"/>
<script src="https://unpkg.com/maplibre-gl@3.6.2/dist/maplibre-gl.js"></script>
<style>
  html,body,#map{margin:0;padding:0;height:100%;width:100%;background:#0F172A}
  .popup{font:14px/1.35 system-ui,sans-serif;max-width:240px;max-height:160px;overflow:auto}
</style>
</head>
<body>
<div id="map"></div>
<script>
const history = $historyJson;
const tileUrl = $safeTile;
const map = new maplibregl.Map({
  container: 'map',
  style: {
    version: 8,
    sources: {
      stadia: {
        type: 'raster',
        tiles: [tileUrl],
        tileSize: 256,
        attribution: '© Stadia Maps © OpenMapTiles © OpenStreetMap'
      }
    },
    layers: [{ id: 'stadia', type: 'raster', source: 'stadia' }]
  },
  center: [86.22, 22.82],
  zoom: 11
});

function addLine(id, coords, color, width) {
  if (coords.length < 2) return;
  map.addSource(id, { type: 'geojson', data: { type: 'Feature', geometry: { type: 'LineString', coordinates: coords }}});
  map.addLayer({ id: id, type: 'line', source: id, paint: { 'line-color': color, 'line-width': width, 'line-opacity': 0.9 }});
}

map.on('load', () => {
  const bounds = new maplibregl.LngLatBounds();
  let has = false;
  (history.segments || []).forEach((seg, i) => {
    const coords = (seg.points || []).map(p => [p.longitude, p.latitude]);
    coords.forEach(c => { bounds.extend(c); has = true; });
    const color = seg.kind === 'travel' ? '#DC2626' : '#1E3A8A';
    const width = seg.kind === 'travel' ? 4 : 3;
    addLine('seg-' + i, coords, color, width);
  });
  (history.night_stays || []).forEach((n) => {
    bounds.extend([n.longitude, n.latitude]); has = true;
    const el = document.createElement('div');
    el.style.width = '28px'; el.style.height = '28px';
    el.style.borderRadius = '50%';
    el.style.border = '3px solid #1E3A8A';
    el.style.boxShadow = '0 0 0 6px rgba(30,58,138,0.25), 0 0 0 12px rgba(30,58,138,0.12)';
    el.style.background = 'rgba(30,58,138,0.35)';
    el.onclick = () => NomadBridge.onNight(String(n.night_stay_id));
    new maplibregl.Marker({ element: el }).setLngLat([n.longitude, n.latitude]).addTo(map);
  });
  (history.journal_pins || []).forEach((j) => {
    bounds.extend([j.longitude, j.latitude]); has = true;
    const el = document.createElement('div');
    el.style.width = '14px'; el.style.height = '14px';
    el.style.borderRadius = '50%';
    el.style.background = '#DC2626';
    el.style.border = '2px solid #fff';
    el.onclick = () => NomadBridge.onJournal(String(j.entry_id));
    new maplibregl.Marker({ element: el }).setLngLat([j.longitude, j.latitude]).addTo(map);
  });
  if (has) map.fitBounds(bounds, { padding: 48, maxZoom: 14 });
});
</script>
</body>
</html>
""".trimIndent()
}
