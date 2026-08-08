package dev.grixo.nomad.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NomadLightScheme = lightColorScheme(
    primary = NomadTeal,
    onPrimary = Color.White,
    primaryContainer = NomadTealSoft,
    onPrimaryContainer = NomadInk,
    secondary = NomadInk,
    onSecondary = Color.White,
    background = NomadMist,
    onBackground = NomadInk,
    surface = NomadSurface,
    onSurface = NomadInk,
    surfaceVariant = NomadSand,
    onSurfaceVariant = NomadMuted,
    outline = NomadStone,
    error = NomadDanger,
    onError = Color.White
)

@Composable
fun NomadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Brand stays light and calm for V1; ignore system dark for a consistent look.
    MaterialTheme(
        colorScheme = NomadLightScheme,
        typography = Typography,
        content = content
    )
}
