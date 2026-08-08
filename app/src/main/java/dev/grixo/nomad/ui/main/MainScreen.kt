package dev.grixo.nomad.ui.main

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.grixo.nomad.R
import dev.grixo.nomad.service.TrackingService
import dev.grixo.nomad.ui.theme.DeepNavy
import dev.grixo.nomad.ui.theme.MidnightBlue
import dev.grixo.nomad.ui.theme.NomadBackground
import dev.grixo.nomad.ui.theme.NomadSurfaceSoft

@Composable
fun MainRoute(viewModel: MainViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val foregroundPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional background grant */ }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setPermissionDenied(!granted)
        if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (granted) {
            val intent = Intent(context, TrackingService::class.java)
            ContextCompat.startForegroundService(context, intent)
            viewModel.setTrackingStatus(true)
        }
    }

    MainScreen(
        state = uiState,
        onStartTracking = {
            foregroundPermissionLauncher.launch(foregroundPermissions)
        },
        onStopTracking = {
            context.stopService(Intent(context, TrackingService::class.java))
            viewModel.setTrackingStatus(false)
        },
        onSync = viewModel::triggerManualSync
    )
}

@Composable
fun MainScreen(
    state: MainUiState,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onSync: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NomadBackground, NomadSurfaceSoft, MidnightBlue.copy(alpha = 0.08f))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    style = MaterialTheme.typography.displayLarge,
                    color = DeepNavy
                )
                Text(
                    text = state.userName?.let { "Welcome, $it" } ?: "Travel quietly. Stay findable.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Clean status panel — text only, no icons
            SoftPanel {
                InfoRow("Backend", if (state.isConnected) "Connected" else "Offline")
                InfoRow("Tracking", if (state.isTracking) "Active" else "Idle")
                InfoRow("Queued signals", state.offlineQueueCount.toString())
                InfoRow("Account", if (state.isRegistered) "Registered" else "Guest")
            }

            SoftPanel {
                Text("Live signal", style = MaterialTheme.typography.titleLarge, color = DeepNavy)
                Spacer(Modifier.height(8.dp))
                InfoRow("Latitude", state.latitude?.let { "%.5f".format(it) } ?: "—")
                InfoRow("Longitude", state.longitude?.let { "%.5f".format(it) } ?: "—")
                InfoRow("Accuracy", state.accuracy?.let { "${it.toInt()} m" } ?: "—")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                InfoRow("Battery", state.batteryPercent?.let { "$it%" } ?: "—")
                InfoRow("Network", state.networkType)
                InfoRow("Last upload", state.lastUploadTime)
            }

            SoftPanel {
                Text(
                    text = stringResource(R.string.journal_coming_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = DeepNavy
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.journalEnabled) {
                        stringResource(R.string.journal_coming_body)
                    } else {
                        stringResource(R.string.journal_locked_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.permissionDenied) {
                Text(
                    text = "Location permission is needed to start tracking.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = state.isTracking,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tracking_cta"
            ) { tracking ->
                if (!tracking) {
                    Button(
                        onClick = onStartTracking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MidnightBlue)
                    ) {
                        Text("Start tracking")
                    }
                } else {
                    Button(
                        onClick = onStopTracking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Stop tracking")
                    }
                }
            }

            OutlinedButton(
                onClick = onSync,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Sync now", color = MidnightBlue)
            }
        }
    }
}

@Composable
private fun SoftPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = { content() }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = DeepNavy
        )
    }
}
