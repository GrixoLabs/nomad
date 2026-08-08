package dev.grixo.nomad.ui.registration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import dev.grixo.nomad.R
import dev.grixo.nomad.domain.model.Gender
import dev.grixo.nomad.ui.theme.NomadMist
import dev.grixo.nomad.ui.theme.NomadSand
import dev.grixo.nomad.ui.theme.NomadTealSoft

@Composable
fun RegistrationRoute(
    onFinished: () -> Unit,
    viewModel: RegistrationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished()
    }
    RegistrationScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPhoneChange = viewModel::onPhoneChange,
        onAgeChange = viewModel::onAgeChange,
        onGenderChange = viewModel::onGenderChange,
        onSubmit = viewModel::submit,
        onSkip = viewModel::skip
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegistrationScreen(
    state: RegistrationUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NomadMist, NomadSand, NomadTealSoft.copy(alpha = 0.45f))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it / 4 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.registration_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.registration_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.email_label)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.phone_label)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            OutlinedTextField(
                value = state.age,
                onValueChange = onAgeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.age_label)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text(
                text = stringResource(R.string.gender_label),
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Gender.entries.forEach { gender ->
                    FilterChip(
                        selected = state.gender == gender,
                        onClick = { onGenderChange(gender) },
                        label = { Text(gender.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onSubmit,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.register_cta))
            }

            TextButton(
                onClick = onSkip,
                enabled = !state.isSubmitting,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.skip_cta))
            }

            Text(
                text = stringResource(R.string.skip_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
