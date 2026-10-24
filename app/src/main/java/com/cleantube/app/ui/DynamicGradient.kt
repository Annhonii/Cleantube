package com.cleantube.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette as ImagePalette
import com.cleantube.app.data.TabCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The colour behind Home / Subscriptions / Library: taken from the thumbnail of the video that is at the top of the
 * screen. Each tab remembers its own colour; the background glides to it (see [DynamicGradientBackground]).
 */
object DynamicGradient {
    private val DefaultSeed = Color(0xFF9C2740)
    private val seeds = mutableStateMapOf<Route, Color>()
    private val cache = ConcurrentHashMap<String, Color>()
    private val latest = ConcurrentHashMap<Route, String>()

    fun seedFor(route: Route?): Color = (route?.let { seeds[it] }) ?: DefaultSeed

    /** [route]'s screen now shows the video [id] at its top: pick its colour (cached, loaded in the background). */
    fun show(route: Route, id: String) {
        if (latest[route] == id) return
        latest[route] = id
        cache[id]?.let { seeds[route] = it; return }
        TabCache.appScope.launch {
            val c = withContext(Dispatchers.IO) { extract(id) } ?: return@launch
            cache[id] = c
            if (latest[route] == id) seeds[route] = c
        }
    }

    /** Vibrant swatch of the thumbnail (dominant as fallback), boosted so it never looks grey or dull. */
    private fun extract(id: String): Color? = try {
        val con = java.net.URL("https://i.ytimg.com/vi/$id/mqdefault.jpg").openConnection() as java.net.HttpURLConnection
        con.connectTimeout = 6000
        con.readTimeout = 6000
        val bmp = con.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
        con.disconnect()
        if (bmp == null) null else {
            val pal = ImagePalette.from(bmp).maximumColorCount(16).generate()
            val sw = pal.vibrantSwatch ?: pal.dominantSwatch ?: pal.lightVibrantSwatch ?: pal.mutedSwatch
            sw?.rgb?.let { boost(it) }
        }
    } catch (_: Exception) {
        null
    }

    /** Dark or grey colours get saturation and brightness lifted (HSV) so the glow is rich, like the picture. */
    private fun boost(argb: Int): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        if (hsv[1] < 0.12f) {            // really grey: a soft light grey-blue glow instead of a dirty one
            hsv[1] = 0.12f; hsv[0] = 215f; hsv[2] = 0.75f
        } else {
            hsv[1] = hsv[1].coerceAtLeast(0.7f)
            hsv[2] = hsv[2].coerceAtLeast(0.88f)
        }
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}

/** Reports the video at the top of a list to [DynamicGradient]. [headerItems] = list items above the first video. */
@Composable
fun TrackGradient(route: Route, state: LazyListState, headerItems: Int, idAt: (Int) -> String?) {
    val idAtNow = rememberUpdatedState(idAt)
    LaunchedEffect(state, route) {
        snapshotFlow { idAtNow.value((state.firstVisibleItemIndex - headerItems).coerceAtLeast(0)) }
            .distinctUntilChanged()
            .collect { id -> if (!id.isNullOrEmpty()) DynamicGradient.show(route, id) }
    }
}

/** HSL lightness of [c] moved to [l] (0..1), hue and saturation kept. */
private fun withLightness(c: Color, l: Float): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(c.toArgb(), hsl)
    hsl[2] = l
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/**
 * Top-centred radial glow: bright and rich at the top, a deeper tone of the same colour half way, and pure black
 * (the screen colour in light mode) from 85% on, so the bottom corners curve into it. The colour glides (700 ms)
 * whenever [seed] changes; [visible] fades the whole thing in / out.
 */
@Composable
fun DynamicGradientBackground(seed: Color, visible: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(seed, tween(700, easing = FastOutSlowInEasing), label = "gradSeed")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "gradAlpha")
    val dark = Bg.luminance() < 0.5f
    val end = if (dark) Color.Black else Bg
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .drawBehind {
                if (alpha <= 0.01f) return@drawBehind
                val top = if (dark) 0.9f else 0.65f
                val bright = color.copy(alpha = top)
                val deep = withLightness(color, 0.32f).copy(alpha = if (dark) 1f else 0.55f)
                drawRect(end)
                drawRect(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to bright,
                            0.25f to bright.copy(alpha = top * 0.95f),
                            0.55f to deep,
                            0.85f to end,
                            1f to end
                        ),
                        center = Offset(size.width / 2f, -size.height * 0.05f),
                        radius = size.height * 0.90f
                    )
                )
            }
    )
}
