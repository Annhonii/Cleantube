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

// Colours: Material You (Monet, from the wallpaper) on Android 12+; on older phones the mint scheme below.

private val Mint = Color(0xFF3DF5A0)
private val MintLight = Color(0xFF14C97A)
private val OnMint = Color(0xFF04150C)
private val Live = Color(0xFFFF4B4B)

private val LightScheme = lightColorScheme(
    primary = MintLight,
    onPrimary = OnMint,
    primaryContainer = Color(0xFFD9FBE9),
    onPrimaryContainer = Color(0xFF05361F),
    secondary = MintLight,
    onSecondary = OnMint,
    background = Color(0xFFF2F4F7),
    onBackground = Color(0xFF0B0D10),
    surface = Color(0xFFF2F4F7),
    onSurface = Color(0xFF0B0D10),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF6A7380),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE6E9EE),
    outline = Color(0xFF9AA3AF),
    outlineVariant = Color(0xFFE3E6EB),
    error = Live,
    inverseSurface = Color(0xFF0B0D10),
    inverseOnSurface = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = Mint,
    onPrimary = OnMint,
    primaryContainer = Color(0xFF12352A),
    onPrimaryContainer = Color(0xFFA7FFD3),
    secondary = Mint,
    onSecondary = OnMint,
    background = Color(0xFF0A0B0E),
    onBackground = Color(0xFFF3F5F7),
    surface = Color(0xFF0A0B0E),
    onSurface = Color(0xFFF3F5F7),
    surfaceVariant = Color(0xFF15181D),
    onSurfaceVariant = Color(0xFF98A0AB),
    surfaceContainerLowest = Color(0xFF0A0B0E),
    surfaceContainerLow = Color(0xFF101216),
    surfaceContainer = Color(0xFF15181D),
    surfaceContainerHigh = Color(0xFF1B1F25),
    surfaceContainerHighest = Color(0xFF262B33),
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFF242830),
    error = Live,
    inverseSurface = Color(0xFFF3F5F7),
    inverseOnSurface = Color(0xFF0A0B0E)
)

/** Current colours as observable state, so reading them anywhere stays in sync. */
private object Palette {
    var bg by mutableStateOf(Color(0xFFF2F4F7))
    var card by mutableStateOf(Color(0xFFF3F4F6))
    var hairline by mutableStateOf(Color(0xFFE8EAED))
    var text by mutableStateOf(Color(0xFF0F0F10))
    var muted by mutableStateOf(Color(0xFF6B7280))
    var accent by mutableStateOf(MintLight)
    var onAccent by mutableStateOf(OnMint)
    var accentSoft by mutableStateOf(Color(0xFFFDE4E4))
    var onAccentSoft by mutableStateOf(Color(0xFF7A0A0E))
    var danger by mutableStateOf(Live)
    var nav by mutableStateOf(Color(0xFF15181D))
    var onNav by mutableStateOf(Color.White)

    fun apply(s: androidx.compose.material3.ColorScheme) {
        nav = s.surfaceContainerHigh
        onNav = s.onSurface
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scheme = when {
        android.os.Build.VERSION.SDK_INT >= 31 ->
            if (dark) androidx.compose.material3.dynamicDarkColorScheme(ctx) else androidx.compose.material3.dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    Palette.apply(scheme)
    MaterialTheme(
        colorScheme = scheme,
        typography = typographyWith(AppFont),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}
