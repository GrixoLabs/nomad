package dev.grixo.nomad.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.background, colors.surfaceVariant.copy(alpha = 0.5f))
                )
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("← Back", color = colors.primary)
        }
        Text(
            stringResource(R.string.journal_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground
        )
        Text(
            state.placeLabel ?: stringResource(R.string.journal_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.body,
            onValueChange = onBodyChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            supportingText = {
                Text("${state.body.length}/500", color = colors.onSurfaceVariant)
            },
            isError = state.body.length > 500
        )
        if (state.errorMessage != null) {
            Text(state.errorMessage, color = colors.error)
        }
        if (state.savedMessage != null) {
            Text(state.savedMessage, color = colors.secondary)
        }
        Spacer(modifier = Modifier.height(8.dp))
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
}
