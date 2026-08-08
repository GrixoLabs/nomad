package dev.grixo.nomad.ui.history

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
                    state.loading && state.tileUrlTemplate == null -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.errorMessage != null && state.tileUrlTemplate == null -> {
                        Text(
                            state.errorMessage,
                            color = colors.error,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        )
                    }
                    state.tileUrlTemplate != null && state.historyJson != null -> {
                        HistoryMapWebView(
                            tileUrl = state.tileUrlTemplate,
                            historyJson = state.historyJson,
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HistoryMapWebView(
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
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
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
                val html = buildMapHtml(normalizeTileUrl(tileUrl), historyJson)
                webView.loadDataWithBaseURL(
                    "https://cdn.jsdelivr.net/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

/** Stadia @2x tiles are 512px; Leaflet expects standard 256 tiles unless detectRetina is used. */
private fun normalizeTileUrl(tileUrl: String): String =
    tileUrl
        .replace("@2x.png", ".png")
        .replace("@2x.jpg", ".jpg")

private fun buildMapHtml(tileUrl: String, historyJson: String): String {
    val safeTile = JSONObject.quote(tileUrl)
    val safeHistory = JSONObject.quote(historyJson)
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  html,body,#map{margin:0;padding:0;height:100%;width:100%;background:#0F172A}
  .journal-pin{width:14px;height:14px;border-radius:50%;background:#DC2626;border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)}
  .night-pin{width:28px;height:28px;border-radius:50%;border:3px solid #1E3A8A;background:rgba(30,58,138,.35);box-shadow:0 0 0 6px rgba(30,58,138,.25)}
</style>
</head>
<body>
<div id="map"></div>
<script>
const history = JSON.parse($safeHistory);
const tileUrl = $safeTile;
const map = L.map('map', { zoomControl: true }).setView([22.82, 86.22], 12);
L.tileLayer(tileUrl, {
  maxZoom: 19,
  attribution: '© OpenStreetMap / Stadia'
}).addTo(map);

const bounds = [];
(history.segments || []).forEach((seg) => {
  const latlngs = (seg.points || []).map(p => [p.latitude, p.longitude]);
  latlngs.forEach(ll => bounds.push(ll));
  if (latlngs.length >= 2) {
    L.polyline(latlngs, {
      color: seg.kind === 'travel' ? '#DC2626' : '#1E3A8A',
      weight: seg.kind === 'travel' ? 4 : 3,
      opacity: 0.9
    }).addTo(map);
  } else if (latlngs.length === 1) {
    L.circleMarker(latlngs[0], {
      radius: 4,
      color: '#1E3A8A',
      fillColor: '#1E3A8A',
      fillOpacity: 0.9
    }).addTo(map);
  }
});
(history.night_stays || []).forEach((n) => {
  const ll = [n.latitude, n.longitude];
  bounds.push(ll);
  const el = L.divIcon({ className: '', html: '<div class="night-pin"></div>', iconSize: [28,28], iconAnchor: [14,14] });
  L.marker(ll, { icon: el }).addTo(map).on('click', () => NomadBridge.onNight(String(n.night_stay_id)));
});
(history.journal_pins || []).forEach((j) => {
  const ll = [j.latitude, j.longitude];
  bounds.push(ll);
  const el = L.divIcon({ className: '', html: '<div class="journal-pin"></div>', iconSize: [14,14], iconAnchor: [7,7] });
  L.marker(ll, { icon: el }).addTo(map).on('click', () => NomadBridge.onJournal(String(j.entry_id)));
});
if (bounds.length) {
  map.fitBounds(bounds, { padding: [48, 48], maxZoom: 14 });
}
setTimeout(() => map.invalidateSize(), 120);
</script>
</body>
</html>
""".trimIndent()
}
