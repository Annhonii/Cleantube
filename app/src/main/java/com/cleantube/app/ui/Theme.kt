package com.cleantube.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

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
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF4F1EC),
    onSurfaceVariant = Color(0xFF5E5A55),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F7F3),
    surfaceContainer = Color(0xFFF4F1EC),
    surfaceContainerHigh = Color(0xFFF4F1EC),
    surfaceContainerHighest = Color(0xFFEBE7E0),
    outline = Color(0xFF8A857F),
    outlineVariant = Color(0xFFE2DED7),
    error = Red,
    inverseSurface = Color(0xFF111111),
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
    onBackground = Color(0xFFF2F2F2),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFA0A0A0),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF262626),
    outline = Color(0xFF6B6B6B),
    outlineVariant = Color(0xFF2E2E2E),
    error = Color(0xFFFF4B4B),
    inverseSurface = Color(0xFFF2F2F2),
    inverseOnSurface = Color.Black
)

/** Current colours as observable state, so reading them anywhere stays in sync. */
private object Palette {
    var bg by mutableStateOf(Color.White)
    var card by mutableStateOf(Color(0xFFF4F1EC))
    var hairline by mutableStateOf(Color(0xFFE2DED7))
    var text by mutableStateOf(Color(0xFF111111))
    var muted by mutableStateOf(Color(0xFF5E5A55))
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
        nav = if (dark) Color(0xFF262626) else Red
        onNav = if (dark) Color(0xFFF2F2F2) else Color.White
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

@Composable
fun CleanTubeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // Set before the content is composed, so the first frame already has the right colours.
    Palette.apply(dark)
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
