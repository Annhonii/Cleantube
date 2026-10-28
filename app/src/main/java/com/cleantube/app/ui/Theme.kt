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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import android.annotation.SuppressLint
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeTonalSpot
import androidx.palette.graphics.Palette as ImagePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Colours when no video is open:
//  - monochrome: black and white only (greys between them). Roles: surfaces / text = white or black;
//    header card + tiles = neutral container; ACCENT (black in light, white in dark) = selected tab, buttons, progress.
//    Red (YtRed) is kept only as the "danger" colour while a video's colours are active.
// As soon as a video opens, the colours follow that video's thumbnail instead (see "colours from the playing video").

private val YtRed = Color(0xFFFF0000)
private val Live = YtRed

private val LightScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0F0F0),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF3A3A3A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E4E4),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF5E5E5E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6D6D6),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF606060),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F8F8),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFE6E6E6),
    outline = Color(0xFF909090),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color.Black,
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White
)

/** Default dark: a soft greyish black instead of pure black. */
private val DarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF262626),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFD0D0D0),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFA8A8A8),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF404040),
    onTertiaryContainer = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    outline = Color(0xFF6E6E6E),
    outlineVariant = Color(0xFF333333),
    error = Color.White,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black
)

/** Pure black (AMOLED), turned on in Settings > Appearance. */
private val PureBlackScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFD0D0D0),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2C),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFA8A8A8),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3A3A3A),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF262626),
    outline = Color(0xFF6E6E6E),
    outlineVariant = Color(0xFF2A2A2A),
    error = Color.White,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black
)

/** Current colours as observable state, so reading them anywhere stays in sync. */
private object Palette {
    var bg by mutableStateOf(Color(0xFFF2F4F7))
    var card by mutableStateOf(Color(0xFFF3F4F6))
    var hairline by mutableStateOf(Color(0xFFE8EAED))
    var text by mutableStateOf(Color(0xFF0F0F10))
    var muted by mutableStateOf(Color(0xFF6B7280))
    var accent by mutableStateOf(Color.Black)
    var onAccent by mutableStateOf(Color.White)
    var accentSoft by mutableStateOf(Color(0xFFFDE4E4))
    var onAccentSoft by mutableStateOf(Color(0xFF7A0A0E))
    var danger by mutableStateOf(Live)
    var secondary by mutableStateOf(Color(0xFF3A3A3A))
    var secondaryContainer by mutableStateOf(Color(0xFFE4E4E4))
    var onSecondaryContainer by mutableStateOf(Color.Black)
    var tertiary by mutableStateOf(Color(0xFF5E5E5E))
    var tertiaryContainer by mutableStateOf(Color(0xFFD6D6D6))
    var onTertiaryContainer by mutableStateOf(Color.Black)
    var surfaceLow by mutableStateOf(Color(0xFFF8F8F8))
    var surfaceMid by mutableStateOf(Color(0xFFF2F2F2))
    var surfaceHighest by mutableStateOf(Color(0xFFE6E6E6))
    var nav by mutableStateOf(Color(0xFF15181D))
    var onNav by mutableStateOf(Color.White)

    fun apply(s: androidx.compose.material3.ColorScheme) {
        secondary = s.secondary
        secondaryContainer = s.secondaryContainer
        onSecondaryContainer = s.onSecondaryContainer
        tertiary = s.tertiary
        tertiaryContainer = s.tertiaryContainer
        onTertiaryContainer = s.onTertiaryContainer
        surfaceLow = s.surfaceContainerLow
        surfaceMid = s.surfaceContainer
        surfaceHighest = s.surfaceContainerHighest
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

/**
 * The other tones of the scheme, so a video's colour reaches more than the accent: with a video open these come
 * from its thumbnail (secondary = calmer shade of the same hue, tertiary = the shifted hue, containers = soft fills).
 * Idle they are plain greys.
 */
val Secondary: Color get() = Palette.secondary
val SecondaryContainer: Color get() = Palette.secondaryContainer
val OnSecondaryContainer: Color get() = Palette.onSecondaryContainer
val Tertiary: Color get() = Palette.tertiary
val TertiaryContainer: Color get() = Palette.tertiaryContainer
val OnTertiaryContainer: Color get() = Palette.onTertiaryContainer

/** Surface steps from lightest to strongest: [SurfaceLow] < [SurfaceMid] < [Card] < [SurfaceHighest]. */
val SurfaceLow: Color get() = Palette.surfaceLow
val SurfaceMid: Color get() = Palette.surfaceMid
val SurfaceHighest: Color get() = Palette.surfaceHighest

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

// ---------------------------------------------------------------- colours from the playing video
//
// Same idea as music apps that tint themselves with the album art: the thumbnail of the video that is open is
// loaded small, its main colour is picked (Palette: vibrant first, then dominant, muted ...), and a whole Material 3
// colour scheme (accent, backgrounds, cards, text) is generated from that one colour, exactly like ArchiveTune. Every colour fades to the new one, so changing
// the video changes the app's look smoothly. No video open = the normal colours again.

/** Main colour of a thumbnail, or null when it can't be loaded. Runs on a background thread. */
private fun thumbnailSeed(videoId: String): Color? = try {
    val c = java.net.URL("https://i.ytimg.com/vi/$videoId/mqdefault.jpg").openConnection() as java.net.HttpURLConnection
    c.connectTimeout = 6000
    c.readTimeout = 6000
    val bmp = c.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
    c.disconnect()
    if (bmp == null) null else {
        val pal = ImagePalette.from(bmp).maximumColorCount(16).generate()
        val sw = pal.vibrantSwatch ?: pal.dominantSwatch ?: pal.mutedSwatch ?: pal.lightVibrantSwatch
            ?: pal.darkVibrantSwatch ?: pal.lightMutedSwatch ?: pal.darkMutedSwatch
        sw?.rgb?.let { Color(it) }
    }
} catch (_: Exception) {
    null
}

/**
 * A full Material 3 colour scheme from one seed colour, made the way ArchiveTune does it: the seed is turned into
 * an HCT colour and, depending on how colourful it is, a Monochrome / Neutral / TonalSpot scheme is generated.
 */
@SuppressLint("RestrictedApi")
private fun seedScheme(seed: Color, dark: Boolean): ColorScheme {
    val hct = Hct.fromInt(seed.toArgb())
    val sch = when {
        hct.chroma < 4.0 -> SchemeMonochrome(hct, dark, 0.0)
        hct.chroma < 12.0 -> SchemeNeutral(hct, dark, 0.0)
        else -> SchemeTonalSpot(hct, dark, 0.0)
    }
    val m = MaterialDynamicColors()
    fun c(d: DynamicColor) = Color(d.getArgb(sch))
    // The stock Material neutrals are almost grey (chroma ~6), so the video's colour was only visible on buttons and
    // the progress bar. For colourful thumbnails the backgrounds, cards and nav are rebuilt from the seed's own hue
    // with a much higher chroma, so the colour is clearly visible over the whole screen.
    val tint = hct.chroma >= 12.0
    val tp = if (tint) com.google.android.material.color.utilities.TonalPalette.fromHueAndChroma(
        hct.hue, (hct.chroma * 0.55).coerceIn(if (dark) 20.0 else 16.0, if (dark) 34.0 else 28.0)
    ) else null
    fun t(tone: Int, fallback: DynamicColor): Color = if (tp != null) Color(tp.tone(tone)) else c(fallback)
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c(m.primary()), onPrimary = c(m.onPrimary()),
        primaryContainer = c(m.primaryContainer()), onPrimaryContainer = c(m.onPrimaryContainer()),
        inversePrimary = c(m.inversePrimary()),
        secondary = c(m.secondary()), onSecondary = c(m.onSecondary()),
        secondaryContainer = c(m.secondaryContainer()), onSecondaryContainer = c(m.onSecondaryContainer()),
        tertiary = c(m.tertiary()), onTertiary = c(m.onTertiary()),
        tertiaryContainer = c(m.tertiaryContainer()), onTertiaryContainer = c(m.onTertiaryContainer()),
        background = t(if (dark) 12 else 94, m.background()), onBackground = c(m.onBackground()),
        surface = t(if (dark) 12 else 94, m.surface()), onSurface = c(m.onSurface()),
        surfaceVariant = t(if (dark) 26 else 86, m.surfaceVariant()), onSurfaceVariant = c(m.onSurfaceVariant()),
        surfaceTint = c(m.surfaceTint()),
        inverseSurface = c(m.inverseSurface()), inverseOnSurface = c(m.inverseOnSurface()),
        outline = c(m.outline()), outlineVariant = t(if (dark) 30 else 80, m.outlineVariant()),
        surfaceBright = t(if (dark) 30 else 96, m.surfaceBright()), surfaceDim = t(if (dark) 8 else 86, m.surfaceDim()),
        surfaceContainerLowest = t(if (dark) 6 else 98, m.surfaceContainerLowest()),
        surfaceContainerLow = t(if (dark) 15 else 92, m.surfaceContainerLow()),
        surfaceContainer = t(if (dark) 19 else 89, m.surfaceContainer()),
        surfaceContainerHigh = t(if (dark) 24 else 85, m.surfaceContainerHigh()),
        surfaceContainerHighest = t(if (dark) 29 else 81, m.surfaceContainerHighest()),
        error = Live
    )
}

/** Fades every colour to its new value (so switching videos doesn't flash). */
@Composable
private fun animatedScheme(t: ColorScheme): ColorScheme {
    val spec = tween<Color>(600)
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "videoColour").value
    return t.copy(
        primary = a(t.primary), onPrimary = a(t.onPrimary),
        primaryContainer = a(t.primaryContainer), onPrimaryContainer = a(t.onPrimaryContainer),
        secondary = a(t.secondary), onSecondary = a(t.onSecondary),
        secondaryContainer = a(t.secondaryContainer), onSecondaryContainer = a(t.onSecondaryContainer),
        tertiary = a(t.tertiary), onTertiary = a(t.onTertiary),
        tertiaryContainer = a(t.tertiaryContainer), onTertiaryContainer = a(t.onTertiaryContainer),
        inversePrimary = a(t.inversePrimary), surfaceBright = a(t.surfaceBright), surfaceDim = a(t.surfaceDim),
        background = a(t.background), onBackground = a(t.onBackground),
        surface = a(t.surface), onSurface = a(t.onSurface),
        surfaceVariant = a(t.surfaceVariant), onSurfaceVariant = a(t.onSurfaceVariant),
        surfaceContainerLowest = a(t.surfaceContainerLowest), surfaceContainerLow = a(t.surfaceContainerLow),
        surfaceContainer = a(t.surfaceContainer), surfaceContainerHigh = a(t.surfaceContainerHigh),
        surfaceContainerHighest = a(t.surfaceContainerHighest),
        outline = a(t.outline), outlineVariant = a(t.outlineVariant),
        inverseSurface = a(t.inverseSurface), inverseOnSurface = a(t.inverseOnSurface)
    )
}

private val thumbSeedCache = java.util.concurrent.ConcurrentHashMap<String, Color>()

/**
 * The same video-colour scheme as the app theme, but for ONE video (its own thumbnail), so a list can tint every item
 * with the colours of its video. Null while the thumbnail is still being read, or when "colours from the video" is off
 * in Settings: the caller then uses the normal colours.
 */
@Composable
fun rememberThumbScheme(videoId: String): ColorScheme? {
    if (!Graph.store.videoColor || videoId.isEmpty()) return null
    val dark = isSystemInDarkTheme()
    var seed by remember(videoId) { mutableStateOf(thumbSeedCache[videoId]) }
    LaunchedEffect(videoId) {
        if (seed == null) {
            val s = withContext(Dispatchers.IO) { thumbnailSeed(videoId) }
            if (s != null) {
                thumbSeedCache[videoId] = s
                seed = s
            }
        }
    }
    val s = seed ?: return null
    return remember(s, dark) { seedScheme(s, dark) }
}

@Composable
fun CleanTubeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // Set before the content is composed, so the first frame already has the right colours.
    val baseScheme = if (!dark) LightScheme else if (Graph.store.pureBlack) PureBlackScheme else DarkScheme

    // colour of the video that is open (the old colour stays until the new thumbnail is read)
    val videoId = VideoHost.videoId
    val followVideo = Graph.store.videoColor
    var seed by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(videoId, followVideo) {
        seed = if (!followVideo || videoId == null) null
        else withContext(Dispatchers.IO) { thumbnailSeed(videoId) }
    }
    val scheme = animatedScheme(seed?.let { seedScheme(it, dark) } ?: baseScheme)
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
