package dev.grixo.nomad.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.grixo.nomad.service.TrackingService

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionsToRequest = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // Handle denied permissions (e.g. show a snackbar)
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionsToRequest.toTypedArray())
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Nomad", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(uiState)

            SignalInfoCard(uiState)

            Spacer(modifier = Modifier.weight(1f))

            ActionButtons(
                isTracking = uiState.isTracking,
                onStart = {
                    val intent = Intent(context, TrackingService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                    viewModel.setTrackingStatus(true)
                },
                onStop = {
                    val intent = Intent(context, TrackingService::class.java)
                    context.stopService(intent)
                    viewModel.setTrackingStatus(false)
                },
                onSync = {
                    viewModel.triggerManualSync()
                }
            )
        }
    }
}

@Composable
fun StatusCard(state: MainUiState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Backend", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    if (state.isConnected) "🟢 Connected" else "🔴 Disconnected",
                    color = if (state.isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
fun SignalInfoCard(state: MainUiState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            InfoRow("Latitude", state.latitude?.toString() ?: "N/A")
            InfoRow("Longitude", state.longitude?.toString() ?: "N/A")
            InfoRow("Accuracy", state.accuracy?.let { "${it}m" } ?: "N/A")
            
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            
            InfoRow("Battery", state.batteryPercent?.let { "$it%" } ?: "N/A")
            InfoRow("Network", state.networkType)
            InfoRow("Last Upload", state.lastUploadTime)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ActionButtons(
    isTracking: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSync: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isTracking) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Tracking")
            }
        } else {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Stop Tracking")
            }
        }
        
        OutlinedButton(
            onClick = onSync,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sync Now")
        }
    }
}
