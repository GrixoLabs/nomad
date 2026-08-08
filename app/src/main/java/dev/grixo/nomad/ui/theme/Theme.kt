package dev.grixo.nomad.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SurfaceDark = Color(0xFF111827)

private val NomadLightScheme = lightColorScheme(
    primary = MidnightBlue,
    onPrimary = NomadOnPrimary,
    primaryContainer = NomadSurfaceSoft,
    onPrimaryContainer = DeepNavy,
    secondary = Emerald,
    onSecondary = NomadOnPrimary,
    tertiary = SkyBlue,
    onTertiary = NomadOnPrimary,
    background = NomadBackground,
    onBackground = DeepNavy,
    surface = NomadSurface,
    onSurface = DeepNavy,
    surfaceVariant = NomadSurfaceSoft,
    onSurfaceVariant = SlateGray,
    outline = SlateGray.copy(alpha = 0.45f),
    error = NomadDanger,
    onError = NomadOnPrimary
)

private val NomadDarkScheme = darkColorScheme(
    primary = SkyBlue,
    onPrimary = DeepNavy,
    primaryContainer = MidnightBlue,
    onPrimaryContainer = NomadOnPrimary,
    secondary = Emerald,
    onSecondary = DeepNavy,
    tertiary = SkyBlue,
    onTertiary = DeepNavy,
    background = DeepNavy,
    onBackground = NomadOnPrimary,
    surface = SurfaceDark,
    onSurface = NomadOnPrimary,
    surfaceVariant = MidnightBlue.copy(alpha = 0.55f),
    onSurfaceVariant = SlateGray,
    outline = SlateGray.copy(alpha = 0.55f),
    error = NomadDanger,
    onError = NomadOnPrimary
)

@Composable
fun NomadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NomadDarkScheme else NomadLightScheme,
        typography = Typography,
        content = content
    )
}
