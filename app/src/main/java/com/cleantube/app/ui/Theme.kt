package com.cleantube.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.cleantube.app.R

// Design tokens taken from the "Nothing" style fork:
// black background, #1C1C1C cards, #323232 hairlines, red as the single accent.
val Bg = Color(0xFF000000)
val Card = Color(0xFF1C1C1C)
val Hairline = Color(0xFF323232)
val TextPrimary = Color(0xFFFFFFFF)
val TextMuted = Color(0xFF9A9A9A)
val Accent = Color(0xFFD71921)

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium)
)

/** Screen titles. Bold maps to the "headline" cut. */
val NType = FontFamily(
    Font(R.font.ntype82_regular, FontWeight.Normal),
    Font(R.font.ntype82_headline, FontWeight.Bold)
)

/** Dot-matrix brand font. */
val NDot = FontFamily(Font(R.font.ndot_77, FontWeight.Normal))

private fun appTypography(): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = NType),
        displayMedium = b.displayMedium.copy(fontFamily = NType),
        displaySmall = b.displaySmall.copy(fontFamily = NType),
        headlineLarge = b.headlineLarge.copy(fontFamily = NType),
        headlineMedium = b.headlineMedium.copy(fontFamily = NType),
        headlineSmall = b.headlineSmall.copy(fontFamily = NType),
        titleLarge = b.titleLarge.copy(fontFamily = Geist),
        titleMedium = b.titleMedium.copy(fontFamily = Geist),
        titleSmall = b.titleSmall.copy(fontFamily = Geist),
        bodyLarge = b.bodyLarge.copy(fontFamily = Geist),
        bodyMedium = b.bodyMedium.copy(fontFamily = Geist),
        bodySmall = b.bodySmall.copy(fontFamily = Geist),
        labelLarge = b.labelLarge.copy(fontFamily = Geist),
        labelMedium = b.labelMedium.copy(fontFamily = Geist),
        labelSmall = b.labelSmall.copy(fontFamily = Geist)
    )
}

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = TextPrimary,
    onSecondary = Bg,
    background = Bg,
    onBackground = TextPrimary,
    surface = Bg,
    onSurface = TextPrimary,
    surfaceVariant = Card,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Card,
    surfaceContainerHigh = Card,
    surfaceContainerHighest = Card,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Accent
)

@Composable
fun CleanTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = appTypography(), content = content)
}
