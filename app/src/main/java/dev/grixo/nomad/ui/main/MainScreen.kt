package dev.grixo.nomad.ui.main

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.grixo.nomad.R
import dev.grixo.nomad.service.TrackingService
import dev.grixo.nomad.ui.components.BrandLogo
import java.util.Locale

@Composable
fun MainRoute(
    onOpenJournal: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRegister: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) {
            context.stopService(Intent(context, TrackingService::class.java))
            viewModel.consumeLogout()
            onLoggedOut()
        }
    }

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
        onSync = viewModel::triggerManualSync,
        onRefresh = viewModel::refreshAll,
        onNearby = viewModel::loadNearbyPlaces,
        onHideNearby = viewModel::hideNearby,
        onNearbySort = viewModel::setNearbySort,
        onOpenJournal = onOpenJournal,
        onOpenHistory = onOpenHistory,
        onOpenRegister = onOpenRegister,
        onLogout = viewModel::logout
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onSync: () -> Unit,
    onRefresh: () -> Unit,
    onNearby: () -> Unit,
    onHideNearby: () -> Unit,
    onNearbySort: (String) -> Unit,
    onOpenJournal: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRegister: () -> Unit,
    onLogout: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        colors.surfaceVariant.copy(alpha = 0.55f),
                        colors.primary.copy(alpha = 0.12f)
                    )
                )
            )
    ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
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
                    color = colors.onBackground
                )
                Text(
                    text = stringResource(R.string.tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant
                )
                if (state.userName != null) {
                    Text(
                        text = "Welcome, ${state.userName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface
                    )
                }
            }

            SoftPanel {
                InfoRow("Backend", if (state.isConnected) "Connected" else "Offline")
                InfoRow("Tracking", if (state.isTracking) "Active" else "Idle")
                InfoRow("Queued signals", state.offlineQueueCount.toString())
                InfoRow("Account", if (state.isRegistered) "Registered" else "Guest")
            }

            SoftPanel {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(end = 140.dp)
                    ) {
                        TextButton(
                            onClick = {
                                when {
                                    state.showNearby -> onHideNearby()
                                    !state.isRegistered -> onOpenRegister()
                                    else -> onOpenHistory()
                                }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("← Back", color = colors.primary)
                        }
                        Text(
                            "Tracking",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.contextLoading) "Updating…" else "Live",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    BrandLogo(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .height(56.dp)
                            .widthIn(max = 160.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow(
                    "Coordinates",
                    when {
                        state.latitude != null && state.longitude != null ->
                            "${MainViewModel.formatCoord(state.latitude)}, ${MainViewModel.formatCoord(state.longitude)}"
                        else -> "Acquiring GPS…"
                    }
                )
                InfoRow("Area", state.placeLabel.ifBlank { "—" })
                InfoRow(
                    "Weather",
                    buildString {
                        append(state.weatherSummary.ifBlank { "—" })
                        state.temperatureC?.let {
                            append(" · ${"%.0f".format(Locale.US, it)}°C")
                        }
                    }
                )
                if (state.altitudeM != null) {
                    InfoRow("Altitude", "${"%.0f".format(Locale.US, state.altitudeM)} m")
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = colors.outline.copy(alpha = 0.45f)
                )
                InfoRow("Accuracy", state.accuracy?.let { "${it.toInt()} m" } ?: "—")
                InfoRow("Battery", state.batteryPercent?.let { "$it%" } ?: "—")
                InfoRow("Network", state.networkType)
                InfoRow("Last upload", state.lastUploadTime)

                if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.errorMessage,
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNearby,
                    enabled = state.latitude != null && state.longitude != null && !state.nearbyLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                        disabledContainerColor = colors.surfaceVariant,
                        disabledContentColor = colors.onSurfaceVariant
                    )
                ) {
                    Text(
                        if (state.nearbyLoading) "Finding places…" else "Top 10 places to visit",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(visible = state.showNearby) {
                SoftPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Places to visit",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onHideNearby) {
                            Text("Hide", color = colors.primary)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.nearbySort == "popularity",
                            onClick = { onNearbySort("popularity") },
                            label = { Text("Popular") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primaryContainer,
                                selectedLabelColor = colors.onPrimaryContainer
                            )
                        )
                        FilterChip(
                            selected = state.nearbySort == "distance",
                            onClick = { onNearbySort("distance") },
                            label = { Text("Distance") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primaryContainer,
                                selectedLabelColor = colors.onPrimaryContainer
                            )
                        )
                    }
                    if (state.nearbyPlaces.isEmpty() && !state.nearbyLoading) {
                        Text(
                            "No places yet. Tap the button above to load.",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        state.nearbyPlaces.forEachIndexed { index, place ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "${index + 1}.",
                                    color = colors.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        place.name,
                                        color = colors.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        listOfNotNull(
                                            place.category,
                                            place.distanceKm?.let {
                                                "%.1f km".format(Locale.US, it)
                                            }
                                        ).joinToString(" · "),
                                        color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (index < state.nearbyPlaces.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = colors.outline.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }
            }

            SoftPanel {
                Text(
                    text = stringResource(R.string.journal_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (state.isRegistered && state.journalEnabled) {
                        stringResource(R.string.journal_hint)
                    } else {
                        stringResource(R.string.journal_locked_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (state.isRegistered) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenJournal,
                            enabled = state.journalEnabled,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.secondary,
                                contentColor = colors.onSecondary
                            )
                        ) { Text("Write") }
                        OutlinedButton(
                            onClick = onOpenHistory,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("History map") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenRegister,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary
                            )
                        ) { Text("Register") }
                        OutlinedButton(
                            onClick = onOpenRegister,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Sign in") }
                    }
                }
            }

            if (state.permissionDenied) {
                Text(
                    text = "Location permission is needed to start tracking.",
                    color = colors.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) { Text("Start tracking") }
                } else {
                    Button(
                        onClick = onStopTracking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.error,
                            contentColor = colors.onError
                        )
                    ) { Text("Stop tracking") }
                }
            }

            OutlinedButton(
                onClick = onSync,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Sync now", color = colors.primary)
            }

            if (state.isRegistered) {
                TextButton(
                    onClick = onLogout,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.logout), color = colors.error)
                }
            }
        }
        }
    }
}

@Composable
private fun SoftPanel(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.surface, shape = RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = { content() }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
