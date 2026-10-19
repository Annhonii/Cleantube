package com.cleantube.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Colours: Material You (Monet). On Android 12+ they come from the wallpaper and follow the
// system light / dark setting; older phones get the fixed red schemes below.
// The names below are the colours the screens use; [CleanTubeTheme] points them at the current scheme.

/** Current colours as observable state, so reading them anywhere (also outside composables) stays in sync. */
private object Palette {
    var bg by mutableStateOf(Color.White)
    var card by mutableStateOf(Color(0xFFEFEFEF))
    var hairline by mutableStateOf(Color(0xFFD0D0D0))
    var text by mutableStateOf(Color.Black)
    var muted by mutableStateOf(Color.Gray)
    var accent by mutableStateOf(Color(0xFFD71921))
    var onAccent by mutableStateOf(Color.White)
    var accentSoft by mutableStateOf(Color(0xFFFFDAD6))
    var onAccentSoft by mutableStateOf(Color(0xFF410002))
    var danger by mutableStateOf(Color(0xFFBA1A1A))

    fun apply(s: ColorScheme) {
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

/** Main colour (buttons, progress, selected states) and the colour for text / icons on top of it. */
val Accent: Color get() = Palette.accent
val OnAccent: Color get() = Palette.onAccent

/** Soft tinted background of the selected tab and the colour for its icon / label. */
val AccentSoft: Color get() = Palette.accentSoft
val OnAccentSoft: Color get() = Palette.onAccentSoft

/** Errors and destructive actions. */
val Danger: Color get() = Palette.danger

private val FallbackLight = lightColorScheme(
    primary = Color(0xFFD71921),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    onSecondary = Color.White,
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    surfaceContainer = Color(0xFFF8EBE9),
    surfaceContainerHigh = Color(0xFFF2E5E3),
    surfaceContainerHighest = Color(0xFFECDFDD),
    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD8C2BF),
    error = Color(0xFFBA1A1A)
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442926),
    background = Color(0xFF201A19),
    onBackground = Color(0xFFEDE0DD),
    surface = Color(0xFF201A19),
    onSurface = Color(0xFFEDE0DD),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
    surfaceContainer = Color(0xFF2D2423),
    surfaceContainerHigh = Color(0xFF382E2D),
    surfaceContainerHighest = Color(0xFF433937),
    outline = Color(0xFFA08C8A),
    outlineVariant = Color(0xFF534341),
    error = Color(0xFFFFB4AB)
)

@Composable
fun CleanTubeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = remember(dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        } else {
            if (dark) FallbackDark else FallbackLight
        }
    }
    // Set before the content is composed, so the first frame already has the right colours.
    Palette.apply(scheme)
    // No custom typography: the system font is used everywhere.
    MaterialTheme(colorScheme = scheme, content = content)
}
