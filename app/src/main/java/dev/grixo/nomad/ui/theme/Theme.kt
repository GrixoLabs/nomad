package dev.grixo.nomad.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TextMutedLight = Color(0xFF475569)

private val NomadColorScheme = lightColorScheme(
    primary = MidnightBlue,
    onPrimary = Color.White,
    primaryContainer = NomadSurfaceSoft,
    onPrimaryContainer = DeepNavy,
    secondary = Emerald,
    onSecondary = Color.White,
    tertiary = SkyBlue,
    onTertiary = Color.White,
    background = NomadBackground,
    onBackground = DeepNavy,
    surface = Color.White,
    onSurface = DeepNavy,
    surfaceVariant = NomadSurfaceSoft,
    onSurfaceVariant = TextMutedLight,
    outline = SlateGray.copy(alpha = 0.45f),
    error = NomadDanger,
    onError = Color.White
)

/** Single light theme — ignores system dark mode. */
@Composable
fun NomadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NomadColorScheme,
        typography = Typography,
        content = content
    )
}
