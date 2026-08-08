package dev.grixo.nomad.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SurfaceDark = Color(0xFF1E293B)
private val TextMutedDark = Color(0xFFCBD5E1)
private val TextMutedLight = Color(0xFF475569)

private val NomadLightScheme = lightColorScheme(
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

private val NomadDarkScheme = darkColorScheme(
    primary = SkyBlue,
    onPrimary = DeepNavy,
    primaryContainer = MidnightBlue,
    onPrimaryContainer = Color.White,
    secondary = Emerald,
    onSecondary = DeepNavy,
    tertiary = SkyBlue,
    onTertiary = DeepNavy,
    background = DeepNavy,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = TextMutedDark,
    outline = TextMutedDark.copy(alpha = 0.55f),
    error = Color(0xFFF87171),
    onError = DeepNavy
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
