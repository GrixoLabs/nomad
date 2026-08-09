package dev.grixo.nomad.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.grixo.nomad.R

@Composable
fun JournalRoute(
    onClose: () -> Unit,
    viewModel: JournalViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    JournalScreen(
        state = state,
        onBodyChange = viewModel::onBodyChange,
        onSave = viewModel::save,
        onClose = onClose
    )
}

@Composable
fun JournalScreen(
    state: JournalUiState,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.background, colors.surfaceVariant.copy(alpha = 0.5f))
                )
            ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(
                onClick = onClose,
                modifier = Modifier
            ) {
                Text("← Back", color = colors.primary)
            }
        }
        item {
            Text(
                stringResource(R.string.journal_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onBackground
            )
        }
        item {
            Text(
                state.placeLabel ?: stringResource(R.string.journal_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
        item {
            OutlinedTextField(
                value = state.body,
                onValueChange = onBodyChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                supportingText = {
                    Text("${state.body.length}/500", color = colors.onSurfaceVariant)
                },
                isError = state.body.length > 500
            )
        }
        if (state.errorMessage != null) {
            item {
                Text(state.errorMessage, color = colors.error)
            }
        }
        if (state.savedMessage != null) {
            item {
                Text(state.savedMessage, color = colors.secondary)
            }
        }
        item {
            Button(
                onClick = onSave,
                enabled = !state.saving && state.body.isNotBlank() && state.body.length <= 500,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            ) {
                Text(if (state.saving) "Saving…" else stringResource(R.string.journal_save))
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your entries",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            JournalEntriesTable(
                rows = state.entries,
                loading = state.loadingEntries
            )
        }
    }
}

@Composable
private fun JournalEntriesTable(
    rows: List<JournalTableRow>,
    loading: Boolean
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .background(colors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Date",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.width(104.dp)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(colors.outline.copy(alpha = 0.4f))
            )
            Text(
                "Journal entry",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
        }
        HorizontalDivider(color = colors.outline.copy(alpha = 0.35f))

        when {
            loading && rows.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            rows.isEmpty() -> {
                Text(
                    "No journal entries yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                rows.forEachIndexed { index, row ->
                    if (row.startsDayGroup && index > 0) {
                        HorizontalDivider(
                            thickness = 2.dp,
                            color = colors.outline.copy(alpha = 0.45f)
                        )
                    } else if (index > 0) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = colors.outline.copy(alpha = 0.18f)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (row.startsDayGroup) {
                                    colors.primary.copy(alpha = 0.04f)
                                } else {
                                    colors.surface
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = row.dateLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (row.showDate) FontWeight.SemiBold else FontWeight.Normal,
                            color = colors.onSurface,
                            modifier = Modifier.width(104.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(18.dp)
                                .background(colors.outline.copy(alpha = 0.25f))
                        )
                        Text(
                            text = row.entryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
