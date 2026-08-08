package dev.grixo.nomad.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.grixo.nomad.R

val NomadDisplayFont = FontFamily(
    Font(R.font.literata_regular, FontWeight.Normal),
    Font(R.font.literata_semibold, FontWeight.SemiBold)
)

val NomadBodyFont = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = NomadDisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NomadDisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    titleLarge = TextStyle(
        fontFamily = NomadBodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = NomadBodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NomadBodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NomadBodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NomadBodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = NomadBodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)
