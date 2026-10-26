package com.cleantube.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.TextureView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Plays [player]'s picture. Only the view with [active] = true is connected to the player. */
@Composable
fun CtVideoSurface(player: CtPlayer, modifier: Modifier = Modifier, active: Boolean = true, fill: Boolean = false) {
    BoxWithConstraints(
        modifier.background(Color.Black).clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val r = player.videoRatio.coerceIn(0.2f, 5f)
        val mode = player.resizeMode
        // cover: scale the picture up until it fills the whole area (edges beyond it are cut off), no black left over
        val cover = mode == CtResize.Zoom || (fill && r < 1f)
        val view = when {
            mode == CtResize.Fill -> Modifier.fillMaxSize()
            cover -> {
                val boxRatio = maxWidth / maxHeight
                if (r > boxRatio) Modifier.requiredSize(maxHeight * r, maxHeight) else Modifier.requiredSize(maxWidth, maxWidth / r)
            }
            else -> Modifier.aspectRatio(r)
        }
        AndroidView(
            factory = { c -> TextureView(c) },
            update = { tv -> if (active) player.exo.setVideoTextureView(tv) },
            modifier = view
        )
    }
}

/** The buffering animation: a slim arc that grows and shrinks while it turns, on a flat dark disc. */
@Composable
fun CtSpinner(modifier: Modifier = Modifier, size: Dp = 62.dp, disc: Boolean = true) {
    val t = rememberInfiniteTransition(label = "ctSpin")
    val rot by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "rot"
    )
    val sweep by t.animateFloat(
        28f, 250f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep"
    )
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (disc) Color(0x59000000) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size * 0.58f)) {
            val stroke = 3.dp.toPx()
            drawArc(
                color = Color.White,
                startAngle = rot - 90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/** Thumbnail shown (still, no zooming) with the spinner until the first picture of the video is on screen. */
@Composable
fun CtPoster(videoId: String, player: CtPlayer, modifier: Modifier = Modifier, fit: Boolean = false, image: Boolean = true) {
    AnimatedVisibility(
        visible = !player.firstFrame,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(320)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (image) {
                AsyncImage(
                    model = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                    contentDescription = null,
                    contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color(0x66000000)))
            }
            if (player.error == null) CtSpinner(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun CtErrorView(message: String, onRetry: () -> Unit, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Color(0xE6000000)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = Color(0xFFDDDDDD), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 9, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Retry", onClick = onRetry)
            if (onOpen != null) Pill("Open in YouTube", onClick = onOpen, active = true)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Design tokens of the player overlay (Material 3, flat: no blur, no gradient, no glow, no shadow)
// ---------------------------------------------------------------------------------------------------------------

/** The one dim layer over the picture while the controls are shown: flat 60% black. */
private val CtScrim = Color(0x99000000)

/** Circular tonal buttons (skip back / forward, back arrow). */
private val CtTonalButton = Color(0x33FFFFFF)

/** Pill-shaped tonal chips and badges. */
private val CtTonalChipColor = Color(0x26FFFFFF)

/** Seek bar: track that is still to be played, and the part that is already loaded. */
private val CtTrackInactive = Color(0x33FFFFFF)
private val CtTrackBuffered = Color(0x66FFFFFF)

/** Small status pills (volume, brightness, 2x, skip, aspect): flat dark pill. */
private val CtHudBg = Color(0xB3000000)

/** The dark icon colour on the white play button. */
private val CtOnWhite = Color(0xFF1B1B1F)

/** Thickness of the progress line shown while the controls are hidden. */
val CtLineHeight = 3.dp

/** Height of the seek bar track. */
private val CtTrackHeight = 6.dp

/** The overlay hides itself after this long without a touch. */
private const val CtAutoHideMs = 3000L

/** Fade of the whole control layer. */
private const val CtFadeMs = 200

/** Speeds the speed chip steps through. */
private val CtSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** "Show the time that is left instead of the time played" (tap the time), remembered while the app is open. */
object CtPrefs {
    var showRemaining by mutableStateOf(false)
}

/** Tabular (equal width) digits, so the time never jitters while it counts. */
@Composable
private fun ctNumStyle(): TextStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")

/**
 * Flat M3 linear seek bar: 6 dp track with fully rounded ends, a white played part, a subtle loaded part and a
 * vertical-pill thumb with a small gap on both sides. Touch it anywhere: the thumb jumps to the finger and follows
 * it (it scales up while held); the video itself seeks when the finger is lifted, so a tap just jumps to that place.
 */
@Composable
fun CtSeekBar(
    fraction: Float,
    buffered: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var active by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        if (active) 1.4f else 1f,
        spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "thumbScale"
    )
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)

    Box(
        modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    active = true
                    var last = (down.position.x / size.width).coerceIn(0f, 1f)
                    start(); move(last)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        ch.consume()
                        if (!ch.pressed) break
                        last = (ch.position.x / size.width).coerceIn(0f, 1f)
                        move(last)
                    }
                    active = false
                    end(last)
                }
            }
            .drawBehind {
                val th = CtTrackHeight.toPx()
                val cy = size.height / 2f
                val top = cy - th / 2f
                val x = size.width * fraction.coerceIn(0f, 1f)
                val thumbW = 4.dp.toPx() * thumbScale
                val thumbH = 24.dp.toPx() * thumbScale
                val gap = 4.dp.toPx()
                val cap = CornerRadius(th / 2f, th / 2f)

                // still to be played (+ loaded part on top of it), starts after the thumb's gap
                val restStart = x + thumbW / 2f + gap
                if (restStart < size.width) {
                    drawRoundRect(CtTrackInactive, Offset(restStart, top), Size(size.width - restStart, th), cap)
                    val bufEnd = size.width * buffered.coerceIn(0f, 1f)
                    if (bufEnd > restStart) {
                        drawRoundRect(CtTrackBuffered, Offset(restStart, top), Size(bufEnd - restStart, th), cap)
                    }
                }
                // played part, ends before the thumb's gap
                val activeEnd = x - thumbW / 2f - gap
                if (activeEnd > 0f) {
                    drawRoundRect(Color.White, Offset(0f, top), Size(activeEnd, th), cap)
                }
                // thumb: vertical pill
                drawRoundRect(
                    Color.White,
                    Offset(x - thumbW / 2f, cy - thumbH / 2f),
                    Size(thumbW, thumbH),
                    CornerRadius(thumbW / 2f, thumbW / 2f)
                )
            }
    )
}

/** Selectable chip used by the Shorts topic row (flat: white when selected, tonal otherwise). */
@Composable
internal fun CtChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .bouncyClickable(pressedScale = 0.93f, onClick = onClick)
            .clip(CircleShape)
            .background(if (selected) Color.White else CtTonalChipColor)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(text, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The hairline shown at the very bottom while the controls are hidden (same colours as the seek bar). */
@Composable
fun CtProgressLine(fraction: Float, buffered: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(CtLineHeight)
            .drawBehind {
                drawRect(CtTrackInactive)
                drawRect(CtTrackBuffered, size = Size(size.width * buffered.coerceIn(0f, 1f), size.height))
                drawRect(Color.White, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
            }
    )
}

fun ctTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}

/** "1.0x", "1.25x": one decimal when it is enough, two when it is not. */
private fun ctSpeedText(s: Float): String =
    if (abs(s * 10f - (s * 10f).roundToInt()) < 0.01f) String.format(Locale.US, "%.1fx", s)
    else String.format(Locale.US, "%.2fx", s)

private tailrec fun Context.ctActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.ctActivity()
    else -> null
}

// ---------------------------------------------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------------------------------------------

/** Round icon button. Flat: [container] is a plain colour, transparent by default. */
@Composable
private fun CtIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    container: Color = Color.Transparent,
    tint: Color = Color.White,
    enabled: Boolean = true
) {
    Box(
        modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .bouncyClickable(pressedScale = 0.9f, enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (scaleIn(tween(150), initialScale = 0.85f) + fadeIn(tween(150))) togetherWith
                    (scaleOut(tween(100), targetScale = 0.85f) + fadeOut(tween(100)))
            },
            label = "ctIcon"
        ) { ic ->
            Icon(ic, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

/** Compact pill-shaped tonal chip: 32 dp high, 8 dp side padding. */
@Composable
private fun CtChipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = CtTonalChipColor,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .bouncyClickable(pressedScale = 0.94f, onClick = onClick)
            .height(32.dp)
            .defaultMinSize(minWidth = 32.dp)
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

/**
 * The big play / pause button: a white 72 dp squircle (24 dp corners) with a dark icon. While it is pressed its
 * corners relax towards a circle and spring back, so the shape "breathes" with the tap.
 */
@Composable
private fun CtPlayButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val radius by animateDpAsState(
        if (pressed) 36.dp else 24.dp,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "playRadius"
    )
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.68f, stiffness = 600f),
        label = "playScale"
    )
    Box(
        modifier
            .size(72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(radius.value.coerceAtLeast(0f).dp))
            .background(Color.White)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (scaleIn(tween(150), initialScale = 0.8f) + fadeIn(tween(150))) togetherWith
                    (scaleOut(tween(100), targetScale = 0.8f) + fadeOut(tween(100)))
            },
            label = "playIcon"
        ) { ic ->
            Icon(ic, contentDescription = description, tint = CtOnWhite, modifier = Modifier.size(40.dp))
        }
    }
}

/** Flat pill used for the volume / brightness / aspect / 2x / skip indicators. */
@Composable
private fun CtHudPill(icon: ImageVector?, label: String, fraction: Float?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(CtHudBg)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        if (fraction != null) {
            Box(Modifier.width(72.dp).height(4.dp).clip(CircleShape).background(CtTrackInactive)) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = ctNumStyle())
    }
}

private class CtHud(val icon: ImageVector?, val label: String, val fraction: Float?)

// ---------------------------------------------------------------------------------------------------------------
// The overlay
// ---------------------------------------------------------------------------------------------------------------

/**
 * On-screen controls (OSD) on top of the picture, Material 3 flat style, in four zones:
 *  1. top bar: back, title + "1080p . Stream", captions, quality badge, settings
 *  2. centre: -10 s, big play / pause squircle, +10 s
 *  3. scrubber: current time / total above a 6 dp seek bar
 *  4. utility row: lock, speed, audio and subtitles on the left; picture-in-picture, aspect ratio, fullscreen right
 *
 * Gestures: tap = show / hide (controls fade after 3 s without a touch), double tap on the left / right = back /
 * forward 10 s, long press = 2x while held. In fullscreen a vertical swipe on the left half changes the brightness,
 * on the right half the volume. A locked screen ignores everything except a tap that brings up one "Unlock" pill.
 *
 * Short players (the 16:9 picture on the video page) have no room for all four zones, so there the utility row is
 * dropped and its fullscreen button moves up into the top bar.
 */
@Composable
fun CtOverlay(
    p: CtPlayer,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = ""
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val activity = remember(ctx) { ctx.ctActivity() }

    var visible by remember { mutableStateOf(true) }
    // bumped by every touch on a control: the 3 s hide timer starts over
    var poke by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var unlockShown by remember { mutableStateOf(false) }
    var unlockPoke by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    // fullscreen: the settings card is drawn right here; on the video page the page draws it under the player
    var panel by remember { mutableStateOf(false) }
    val settingsOpen = if (fullscreen) panel else PlayerSettingsHost.open
    var skip by remember { mutableIntStateOf(0) }
    var skipTick by remember { mutableIntStateOf(0) }
    // long press = play at 2x while the finger is down (like YouTube), back to the old speed on release
    var holdFast by remember { mutableStateOf(false) }
    var speedBeforeHold by remember { mutableFloatStateOf(1f) }
    // long press on the left half = rewind at 2x (ExoPlayer can't play backwards: pause and step back), right half = 2x forward
    var holdBack by remember { mutableStateOf(false) }
    var playBeforeHold by remember { mutableStateOf(true) }
    // volume / brightness / aspect indicator
    var hud by remember { mutableStateOf(CtHud(null, "", null)) }
    var hudOn by remember { mutableStateOf(false) }
    var hudTick by remember { mutableIntStateOf(0) }

    // paused (by the button, or from outside): the controls show; a tap then hides them again, like YouTube
    LaunchedEffect(p.wantPlay) { if (!p.wantPlay) visible = true }

    // controls fade away by themselves after 3 s without a touch while the video plays
    LaunchedEffect(visible, p.isPlaying, dragging, settingsOpen, locked, poke) {
        if (visible && !locked && p.isPlaying && !dragging && !settingsOpen) {
            delay(CtAutoHideMs)
            visible = false
        }
    }
    // the lone "Unlock" pill goes away after 3 s too
    LaunchedEffect(locked, unlockShown, unlockPoke) {
        if (locked && unlockShown) {
            delay(CtAutoHideMs)
            unlockShown = false
        }
    }
    LaunchedEffect(holdFast, holdBack) {
        if (holdFast && holdBack) {
            while (true) {
                p.seekBy(-200L) // 200 ms every 100 ms = 2x backwards
                delay(100)
            }
        }
    }
    LaunchedEffect(skipTick) {
        if (skipTick > 0) {
            delay(600)
            skip = 0
        }
    }
    LaunchedEffect(hudTick) {
        if (hudTick > 0) {
            delay(900)
            hudOn = false
        }
    }

    // ---- volume / brightness helpers (fullscreen swipes) ----
    val audio = remember(ctx) { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val brightnessTouched = remember { booleanArrayOf(false) }
    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: -1f }
    DisposableEffect(activity) {
        onDispose {
            // leaving the player gives the phone's own brightness back
            if (brightnessTouched[0]) {
                activity?.window?.let { w ->
                    val a = w.attributes
                    a.screenBrightness = originalBrightness
                    w.attributes = a
                }
            }
        }
    }
    fun showHud(icon: ImageVector?, label: String, fraction: Float?) {
        hud = CtHud(icon, label, fraction)
        hudOn = true
        hudTick++
    }
    fun dialogOrActivityWindow() = (view.parent as? DialogWindowProvider)?.window ?: activity?.window
    fun readBrightness(): Float {
        val b = dialogOrActivityWindow()?.attributes?.screenBrightness ?: -1f
        if (b >= 0f) return b
        return try {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Exception) {
            0.5f
        }
    }
    fun writeBrightness(v: Float) {
        val b = v.coerceIn(0.02f, 1f)
        brightnessTouched[0] = true
        for (w in listOfNotNull(dialogOrActivityWindow(), activity?.window).distinct()) {
            val a = w.attributes
            a.screenBrightness = b
            w.attributes = a
        }
    }
    fun readVolume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }
    fun writeVolume(v: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).roundToInt().coerceIn(0, max), 0)
    }
    fun volumeIcon(v: Float): ImageVector = when {
        v <= 0.001f -> Icons.Rounded.VolumeOff
        v < 0.5f -> Icons.Rounded.VolumeDown
        else -> Icons.Rounded.VolumeUp
    }

    fun setSettings(on: Boolean) {
        poke++
        if (fullscreen) panel = on else PlayerSettingsHost.open = on
    }

    val dur = p.duration.coerceAtLeast(1L)
    val frac = if (dragging) dragFraction else (p.position.toFloat() / dur).coerceIn(0f, 1f)
    val bufFrac = (p.buffered.toFloat() / dur).coerceIn(0f, 1f)
    val shownPos = if (dragging) (dragFraction * dur).toLong() else p.position
    // while the video is still loading, everything stays visible (the centre buttons are replaced by the spinner)
    val showControls = !locked && !PipHost.active && (visible || p.ended || (!p.firstFrame && p.error == null))
    val chromeAlpha by animateFloatAsState(if (dragging) 0f else 1f, tween(CtFadeMs), label = "chromeAlpha")

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // outer: taps, double taps, long press
            .pointerInput(locked, settingsOpen) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        if (holdFast) {
                            holdFast = false
                            if (holdBack) {
                                holdBack = false
                                p.exo.playWhenReady = playBeforeHold
                            } else {
                                p.changeSpeed(speedBeforeHold)
                            }
                        }
                    },
                    onLongPress = { off ->
                        if (!locked && p.error == null) {
                            if (off.x < size.width / 2f) {
                                playBeforeHold = p.wantPlay && !p.ended
                                p.exo.playWhenReady = false
                                holdBack = true
                                holdFast = true
                            } else if (p.wantPlay && !p.ended) {
                                speedBeforeHold = p.speed
                                holdBack = false
                                holdFast = true
                                p.changeSpeed(2f)
                            }
                        }
                    },
                    onTap = {
                        when {
                            locked -> { unlockShown = true; unlockPoke++ }
                            settingsOpen -> setSettings(false)
                            else -> { visible = !visible; poke++ }
                        }
                    },
                    onDoubleTap = { off ->
                        if (!locked) {
                            val right = off.x > size.width / 2
                            p.seekBy(if (right) 10_000L else -10_000L)
                            skip = if (right) 1 else -1
                            skipTick++
                        }
                    }
                )
            }
            // inner (sees the finger first, so a swipe cancels the tap): brightness left, volume right. Fullscreen only,
            // on the video page the same swipe belongs to the page (drag down = minimise).
            .pointerInput(fullscreen, locked) {
                if (fullscreen && !locked) {
                    var left = true
                    var base = 0f
                    var moved = 0f
                    detectVerticalDragGestures(
                        onDragStart = { off ->
                            left = off.x < size.width / 2f
                            base = if (left) readBrightness() else readVolume()
                            moved = 0f
                        },
                        onDragEnd = { hudTick++ },
                        onDragCancel = { hudTick++ },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            moved += -dy / size.height * 1.3f
                            val v = (base + moved).coerceIn(0f, 1f)
                            val pct = "${(v * 100f).roundToInt()}%"
                            if (left) {
                                writeBrightness(v)
                                showHud(Icons.Rounded.BrightnessMedium, pct, v)
                            } else {
                                writeVolume(v)
                                showHud(volumeIcon(v), pct, v)
                            }
                        }
                    )
                }
            }
    ) {
        // short player (the 16:9 picture on the page): no room for the utility row
        val full = maxHeight >= 240.dp
        val inset = if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier

        // ===== the control layer: one flat scrim + four zones, one 200 ms fade for all of it =====
        AnimatedVisibility(
            showControls,
            enter = fadeIn(tween(CtFadeMs)),
            exit = fadeOut(tween(CtFadeMs))
        ) {
            Box(Modifier.fillMaxSize().background(CtScrim)) {
                Column(Modifier.fillMaxSize().then(inset)) {

                    // ---- 1. top header bar ----
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = chromeAlpha }
                            .padding(horizontal = 12.dp, vertical = if (full) 8.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CtIconButton(
                            Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                            onClick = { if (fullscreen) onFullscreen() else VideoHost.requestCollapse() },
                            container = CtTonalChipColor
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            if (title.isNotBlank()) {
                                Text(
                                    title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            val res = if (p.currentHeight > 0) "${p.currentHeight}p" else "Auto"
                            Text(
                                "$res • Stream", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                                fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CtIconButton(
                                if (p.captionsOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                                if (p.captionsOn) "Turn captions off" else "Turn captions on",
                                onClick = { poke++; p.setCaptions(!p.captionsOn) },
                                container = if (p.captionsOn) Color.White else Color.Transparent,
                                tint = if (p.captionsOn) CtOnWhite else Color.White
                            )
                            // resolution badge (the short player shows it in the subtitle line instead)
                            if (full) {
                                CtChipButton(onClick = { setSettings(true) }, modifier = Modifier.padding(horizontal = 2.dp)) {
                                    Text(
                                        if (p.currentHeight > 0) "${p.currentHeight}p" else "Auto",
                                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                            CtIconButton(
                                Icons.Outlined.Settings, "Playback settings",
                                onClick = { setSettings(!settingsOpen) }
                            )
                            if (!full) {
                                CtIconButton(
                                    if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    "Fullscreen", onClick = { poke++; onFullscreen() }
                                )
                            }
                        }
                    }

                    // ---- 2. centre playback controls ----
                    Box(
                        Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = chromeAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        if (p.firstFrame && !(p.buffering)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(32.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CtIconButton(
                                    Icons.Rounded.Replay10, "Back 10 seconds",
                                    onClick = { poke++; p.seekBy(-10_000L) },
                                    size = 56.dp, iconSize = 32.dp, container = CtTonalButton
                                )
                                CtPlayButton(
                                    icon = when {
                                        p.ended -> Icons.Rounded.Replay
                                        p.wantPlay -> Icons.Rounded.Pause
                                        else -> Icons.Rounded.PlayArrow
                                    },
                                    description = "Play / pause",
                                    onClick = { p.togglePlay(); visible = true; poke++ }
                                )
                                CtIconButton(
                                    Icons.Rounded.Forward10, "Forward 10 seconds",
                                    onClick = { poke++; p.seekBy(10_000L) },
                                    size = 56.dp, iconSize = 32.dp, container = CtTonalButton
                                )
                            }
                        }
                    }

                    // ---- 3. scrubber + 4. utility row ----
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = if (full) 12.dp else 4.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            // single tap on the time: remaining time <-> time played (played is the default)
                            val toggle = Modifier
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    poke++; CtPrefs.showRemaining = !CtPrefs.showRemaining
                                }
                                .padding(vertical = 8.dp)
                            val first = if (CtPrefs.showRemaining) "-" + ctTime((p.duration - shownPos).coerceAtLeast(0L)) else ctTime(shownPos)
                            Text(
                                first, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                style = ctNumStyle(), maxLines = 1, modifier = toggle
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                ctTime(p.duration), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                style = ctNumStyle(), maxLines = 1, modifier = toggle
                            )
                        }
                        CtSeekBar(
                            fraction = frac,
                            buffered = bufFrac,
                            onDragStart = { dragging = true; dragFraction = frac; poke++ },
                            onDrag = { dragFraction = it },
                            onDragEnd = { f -> p.seekTo((f * dur).toLong()); dragging = false; poke++ },
                            modifier = Modifier.fillMaxWidth().height(if (full) 40.dp else 32.dp)
                        )

                        if (full) {
                            Row(
                                Modifier.fillMaxWidth().graphicsLayer { alpha = chromeAlpha },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // lock controls
                                    CtChipButton(onClick = { locked = true; visible = false; unlockShown = true; unlockPoke++ }) {
                                        Icon(Icons.Rounded.Lock, contentDescription = "Lock controls", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    // playback speed: tap steps to the next speed
                                    CtChipButton(onClick = {
                                        poke++
                                        p.changeSpeed(CtSpeeds.firstOrNull { it > p.speed + 0.01f } ?: CtSpeeds.first())
                                    }) {
                                        Icon(Icons.Rounded.Speed, contentDescription = "Playback speed", tint = Color.White, modifier = Modifier.size(18.dp))
                                        Text(
                                            ctSpeedText(p.speed), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                            style = ctNumStyle(), maxLines = 1, modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                                        )
                                    }
                                    // audio track + subtitles: the settings card has both lists
                                    CtChipButton(onClick = { setSettings(true) }) {
                                        Icon(Icons.Rounded.Subtitles, contentDescription = "Audio and subtitles", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    CtIconButton(
                                        Icons.Rounded.PictureInPictureAlt, "Picture in picture",
                                        onClick = { poke++; PipHost.enter?.invoke() }
                                    )
                                    CtIconButton(
                                        when (p.resizeMode) {
                                            CtResize.Fit -> Icons.Rounded.FitScreen
                                            CtResize.Fill -> Icons.Rounded.AspectRatio
                                            CtResize.Zoom -> Icons.Rounded.ZoomOutMap
                                        },
                                        "Aspect ratio: ${p.resizeMode.label}",
                                        onClick = {
                                            poke++
                                            val m = p.cycleResize()
                                            showHud(null, m.label, null)
                                        }
                                    )
                                    CtIconButton(
                                        if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                        "Fullscreen", onClick = { poke++; onFullscreen() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // while the thumb is held: the time it points at, big in the middle
        AnimatedVisibility(
            dragging,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(CtFadeMs))
        ) {
            Row(
                Modifier.clip(CircleShape).background(CtHudBg).padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(ctTime((dragFraction * dur).toLong()), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium, style = ctNumStyle())
                Text("  /  ${ctTime(p.duration)}", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, style = ctNumStyle(), modifier = Modifier.padding(bottom = 2.dp))
            }
        }

        // top indicators: volume, brightness, aspect ratio (below the header bar while that is shown)
        val topGap = if (showControls) 60.dp else 16.dp
        AnimatedVisibility(
            hudOn,
            modifier = Modifier.align(Alignment.TopCenter).then(inset).padding(top = topGap),
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(CtFadeMs))
        ) {
            CtHudPill(hud.icon, hud.label, hud.fraction)
        }

        // "2x" while the finger is held down
        AnimatedVisibility(
            holdFast,
            modifier = Modifier.align(Alignment.TopCenter).then(inset).padding(top = topGap),
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(160))
        ) {
            CtHudPill(if (holdBack) Icons.Rounded.FastRewind else Icons.Rounded.FastForward, "2x", null)
        }

        // double-tap feedback
        AnimatedVisibility(
            skip != 0,
            modifier = Modifier.align(if (skip < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 36.dp),
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(CtFadeMs)) + scaleOut(targetScale = 0.92f)
        ) {
            CtHudPill(null, if (skip < 0) "−10 s" else "+10 s", null)
        }

        // spinner while the video stalls (the centre buttons make room for it)
        if (p.buffering && p.firstFrame && !PipHost.active) {
            CtSpinner(Modifier.align(Alignment.Center))
        }

        // controls hidden or locked: only the thin progress line
        if (!showControls) {
            CtProgressLine(frac, bufFrac, Modifier.align(Alignment.BottomCenter))
        }

        // ===== locked: nothing reacts, one tap brings up a single "Unlock" pill =====
        AnimatedVisibility(
            locked && unlockShown,
            modifier = Modifier.align(Alignment.BottomCenter).then(inset).padding(bottom = if (full) 32.dp else 16.dp),
            enter = fadeIn(tween(CtFadeMs)),
            exit = fadeOut(tween(CtFadeMs))
        ) {
            Row(
                Modifier
                    .bouncyClickable(pressedScale = 0.94f, onClick = { locked = false; unlockShown = false; visible = true; poke++ })
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.LockOpen, contentDescription = null, tint = CtOnWhite, modifier = Modifier.size(18.dp))
                Text("Unlock", color = CtOnWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }

        // fullscreen only: the settings card slides up from the bottom (on the video page it sits under the player)
        if (fullscreen) {
            AnimatedVisibility(
                panel,
                enter = androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.86f, stiffness = 320f)) { it } + fadeIn(tween(CtFadeMs)),
                exit = androidx.compose.animation.slideOutVertically(tween(260)) { it } + fadeOut(tween(CtFadeMs))
            ) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { panel = false } }) {
                    PlayerSettingsSheet(
                        p, onClose = { panel = false },
                        modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 680.dp)
                    )
                }
            }
        }
    }
}
