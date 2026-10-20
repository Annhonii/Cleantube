package com.cleantube.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.cleantube.app.R
import com.cleantube.app.data.Graph

// Fixed palette, no Monet / wallpaper colours.
// Light: white, off-white, red.   Dark: pure black, grey, red.

private val Red = Color(0xFFD71921)
private val RedDark = Color(0xFFE5252D)

private val LightScheme = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDE4E4),
    onPrimaryContainer = Color(0xFF7A0A0E),
    secondary = Red,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF0F0F10),
    surface = Color.White,
    onSurface = Color(0xFF0F0F10),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerHigh = Color(0xFFF3F4F6),
    surfaceContainerHighest = Color(0xFFE5E7EB),
    outline = Color(0xFF9CA3AF),
    outlineVariant = Color(0xFFE8EAED),
    error = Red,
    inverseSurface = Color(0xFF0F0F10),
    inverseOnSurface = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = RedDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1214),
    onPrimaryContainer = Color(0xFFFFB4B0),
    secondary = RedDark,
    onSecondary = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFF4F4F5),
    surface = Color.Black,
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF1A1A1D),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E10),
    surfaceContainer = Color(0xFF151518),
    surfaceContainerHigh = Color(0xFF1C1C20),
    surfaceContainerHighest = Color(0xFF27272C),
    outline = Color(0xFF71717A),
    outlineVariant = Color(0xFF2A2A2F),
    error = Color(0xFFFF4B4B),
    inverseSurface = Color(0xFFF4F4F5),
    inverseOnSurface = Color.Black
)

/** Current colours as observable state, so reading them anywhere stays in sync. */
private object Palette {
    var bg by mutableStateOf(Color.White)
    var card by mutableStateOf(Color(0xFFF3F4F6))
    var hairline by mutableStateOf(Color(0xFFE8EAED))
    var text by mutableStateOf(Color(0xFF0F0F10))
    var muted by mutableStateOf(Color(0xFF6B7280))
    var accent by mutableStateOf(Red)
    var onAccent by mutableStateOf(Color.White)
    var accentSoft by mutableStateOf(Color(0xFFFDE4E4))
    var onAccentSoft by mutableStateOf(Color(0xFF7A0A0E))
    var danger by mutableStateOf(Red)
    var nav by mutableStateOf(Red)
    var onNav by mutableStateOf(Color.White)

    fun apply(dark: Boolean) {
        val s = if (dark) DarkScheme else LightScheme
        // bottom pill: red in light mode, dark grey in dark mode
        nav = if (dark) Color(0xFF27272C) else Red
        onNav = if (dark) Color(0xFFF4F4F5) else Color.White
        bg = s.background
        card = s.surfaceContainerHigh
        hairline = s.outlineVariant
        text = s.onSurface
        muted = s.onSurfaceVariant
        accent = s.primary
        onAccent = s.onPrimary
        accentSoft = s.primaryContainer
        onAccentSoft = s.onPrimaryContainer
        danger = s.error
    }
}

/** Screen background. */
val Bg: Color get() = Palette.bg

/** Cards, chips and the search bar. */
val Card: Color get() = Palette.card

/** Thin dividers and empty image tiles. */
val Hairline: Color get() = Palette.hairline

val TextPrimary: Color get() = Palette.text
val TextMuted: Color get() = Palette.muted

/** Main colour (buttons, progress, selected states) and the colour on top of it. */
val Accent: Color get() = Palette.accent
val OnAccent: Color get() = Palette.onAccent

/** Soft tinted background of the selected tab and the colour for its icon / label. */
val AccentSoft: Color get() = Palette.accentSoft
val OnAccentSoft: Color get() = Palette.onAccentSoft

/** Bottom navigation pill, and the colour of the icons / selected chip on it. */
val NavBg: Color get() = Palette.nav
val OnNavBg: Color get() = Palette.onNav

/** Errors and destructive actions. */
val Danger: Color get() = Palette.danger

/** Poppins, bundled in res/font (SemiBold falls back to the Bold file). */
val PoppinsFamily = FontFamily(
    Font(R.font.poppins_light, FontWeight.Light),
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

/** The font the user picked in Settings (Poppins by default, or the phone's system font). */
val AppFont: FontFamily
    get() = if (Graph.store.appFont == "system") FontFamily.Default else PoppinsFamily

private fun typographyWith(f: FontFamily): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = f),
        displayMedium = t.displayMedium.copy(fontFamily = f),
        displaySmall = t.displaySmall.copy(fontFamily = f),
        headlineLarge = t.headlineLarge.copy(fontFamily = f),
        headlineMedium = t.headlineMedium.copy(fontFamily = f),
        headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f),
        titleMedium = t.titleMedium.copy(fontFamily = f),
        titleSmall = t.titleSmall.copy(fontFamily = f),
        bodyLarge = t.bodyLarge.copy(fontFamily = f),
        bodyMedium = t.bodyMedium.copy(fontFamily = f),
        bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f),
        labelMedium = t.labelMedium.copy(fontFamily = f),
        labelSmall = t.labelSmall.copy(fontFamily = f)
    )
}

@Composable
fun CleanTubeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // Set before the content is composed, so the first frame already has the right colours.
    Palette.apply(dark)
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = typographyWith(AppFont),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
