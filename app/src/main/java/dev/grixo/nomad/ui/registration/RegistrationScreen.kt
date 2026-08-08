package dev.grixo.nomad.ui.registration

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.grixo.nomad.R
import dev.grixo.nomad.domain.model.Gender
import dev.grixo.nomad.ui.components.BrandLogo

@Composable
fun RegistrationRoute(
    onFinished: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: RegistrationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished()
    }

    val handleBack: (() -> Unit)? = when {
        state.step == RegistrationStep.OTP -> viewModel::backFromOtp
        onBack != null -> onBack
        else -> null
    }
    BackHandler(enabled = handleBack != null) { handleBack?.invoke() }

    RegistrationScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPhoneChange = viewModel::onPhoneChange,
        onAgeChange = viewModel::onAgeChange,
        onGenderChange = viewModel::onGenderChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onOtpChange = viewModel::onOtpChange,
        onSubmitProfile = viewModel::submitProfile,
        onVerifyOtp = viewModel::verifyOtp,
        onResendOtp = viewModel::resendOtp,
        onSkip = viewModel::skip,
        onBack = handleBack
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
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onSubmitProfile: () -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onSkip: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outline,
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface,
        focusedLabelColor = colors.primary,
        unfocusedLabelColor = colors.onSurfaceVariant,
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        cursorColor = colors.primary
    )

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text("← Back", color = colors.primary)
                }
            }

            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it / 4 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrandLogo()
                    Text(
                        text = if (state.step == RegistrationStep.OTP) {
                            stringResource(R.string.verify_title)
                        } else {
                            stringResource(R.string.registration_title)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onBackground
                    )
                    Text(
                        text = if (state.step == RegistrationStep.OTP) {
                            state.otpHint.ifBlank { stringResource(R.string.verify_subtitle) }
                        } else {
                            stringResource(R.string.registration_subtitle)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.step == RegistrationStep.PROFILE) {
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
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.password_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.confirm_password_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Text(
                    text = stringResource(R.string.gender_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onBackground
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
                                selectedContainerColor = colors.primaryContainer,
                                selectedLabelColor = colors.onPrimaryContainer,
                                containerColor = colors.surface,
                                labelColor = colors.onSurface
                            )
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.otp,
                    onValueChange = onOtpChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.otp_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }

            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = state.errorMessage.orEmpty(),
                    color = colors.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (state.infoMessage != null && state.errorMessage == null) {
                Text(
                    text = state.infoMessage,
                    color = colors.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.step == RegistrationStep.PROFILE) {
                Button(
                    onClick = onSubmitProfile,
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.register_cta))
                }
                TextButton(
                    onClick = onSkip,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.skip_cta), color = colors.primary)
                }
                Text(
                    text = stringResource(R.string.skip_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            } else {
                Button(
                    onClick = onVerifyOtp,
                    enabled = !state.isSubmitting && state.otp.length >= 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.verify_cta))
                }
                TextButton(
                    onClick = onResendOtp,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.resend_otp), color = colors.primary)
                }
            }
        }
    }
}
