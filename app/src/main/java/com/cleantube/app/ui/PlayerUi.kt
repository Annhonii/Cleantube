package com.cleantube.app.ui

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.cleantube.app.data.VideoItem
import kotlinx.coroutines.delay
import java.util.Locale

/** Plays [player]'s picture. Only the view with [active] = true is connected to the player. */
@Composable
fun CtVideoSurface(player: CtPlayer, modifier: Modifier = Modifier, active: Boolean = true) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { c -> TextureView(c) },
            update = { tv -> if (active) player.exo.setVideoTextureView(tv) },
            modifier = Modifier.aspectRatio(player.videoRatio.coerceIn(0.2f, 5f))
        )
    }
}

/** The buffering animation: a slim arc that grows and shrinks while it turns, on a soft glass disc. */
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
fun CtPoster(videoId: String, player: CtPlayer, modifier: Modifier = Modifier, fit: Boolean = false) {
    AnimatedVisibility(
        visible = !player.firstFrame,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(320)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                contentDescription = null,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
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

/** Thickness of the progress line (the same while the controls are shown and hidden). */
val CtLineHeight = 3.dp

/** The red of YouTube's progress bar (the app itself is black / white, the player keeps YouTube's look). */
val CtRed = Color(0xFFFF0000)

/** "Show the time that is left instead of the time played" (tap the time), remembered while the app is open. */
object CtPrefs {
    var showRemaining by mutableStateOf(false)
}

/**
 * YouTube-style seek bar along the bottom edge of the picture: grey track, lighter buffered part, red played part
 * and a red dot. Touch it anywhere: the dot jumps to the finger and follows it (it grows while it is held);
 * the video itself seeks when the finger is lifted. A simple tap therefore just jumps to that place.
 */
@Composable
fun CtSeekBar(
    fraction: Float,
    buffered: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showThumb: Boolean = true
) {
    var active by remember { mutableStateOf(false) }
    val line by animateDpAsState(if (active) 4.dp else CtLineHeight, label = "line")
    val thumb by animateDpAsState(if (active) 20.dp else if (showThumb) 13.dp else 0.dp, label = "thumb")
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)

    Box(
        modifier
            .height(30.dp)
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
                // the line sits on the very bottom edge of the picture, exactly where the hairline is while the controls
                // are hidden, so nothing moves when the controls appear or go away (the dot may reach below the edge)
                val th = line.toPx()
                val top = size.height - th
                val cy = size.height - th / 2
                val f = fraction.coerceIn(0f, 1f)
                drawRect(Color.White.copy(alpha = 0.3f), Offset(0f, top), Size(size.width, th))
                drawRect(Color.White.copy(alpha = 0.5f), Offset(0f, top), Size(size.width * buffered.coerceIn(0f, 1f), th))
                drawRect(CtRed, Offset(0f, top), Size(size.width * f, th))
                if (thumb > 0.dp) drawCircle(CtRed, thumb.toPx() / 2, Offset(size.width * f, cy))
            }
    )
}

/** The hairline shown at the very bottom while the controls are hidden. */
@Composable
fun CtProgressLine(fraction: Float, buffered: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(CtLineHeight)
            .drawBehind {
                drawRect(Color.White.copy(alpha = 0.3f))
                drawRect(Color.White.copy(alpha = 0.5f), size = Size(size.width * buffered.coerceIn(0f, 1f), size.height))
                drawRect(CtRed, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
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

@Composable
private fun GlassButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true
) {
    Box(
        modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .bouncyClickable(pressedScale = 0.9f, onClick = { if (enabled) onClick() })
            .clip(CircleShape)
            .background(Color(0x59000000)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
internal fun CtChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .bouncyClickable(pressedScale = 0.93f, onClick = onClick)
            .clip(CircleShape)
            .background(if (selected) Color.White else Color(0x26FFFFFF))
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(text, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Small popup that grows out of the settings pill (top right): quality and speed as two rows of chips.
 * No dimming, no full-screen sheet: the video stays visible while you pick.
 */
@Composable
private fun CtSettingsPopup(p: CtPlayer, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } }) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 50.dp, end = 10.dp)
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF0181818))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val autoLabel = if (p.quality == 0 && p.currentHeight > 0) "Auto ${p.currentHeight}p" else "Auto"
            Text("Quality", color = Color(0xFF9A9A9A), fontSize = 11.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CtChip(autoLabel, p.quality == 0) { p.changeQuality(0) }
                p.heights.reversed().forEach { h -> CtChip("${h}p", p.quality == h) { p.changeQuality(h) } }
            }
            Text("Speed", color = Color(0xFF9A9A9A), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    val label = if (s == 1f) "1x" else if (s % 1f == 0f) "${s.toInt()}x" else "${s}x"
                    CtChip(label, p.speed == s) { p.changeSpeed(s) }
                }
            }
        }
    }
}

/**
 * Controls on top of the picture, laid out like the YouTube app: tap = show / hide, double tap on the left / right =
 * back / forward 10 s, previous / play-pause / next in the middle, the time (tap it to switch between "played" and
 * "time left") and fullscreen above a red seek bar at the bottom edge, a settings gear (quality + speed) at the top
 * right. While the controls are hidden only the thin red progress line remains.
 */
@Composable
fun CtOverlay(
    p: CtPlayer,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = ""
) {
    val nav = LocalNav.current
    var visible by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var panel by remember { mutableStateOf(false) }
    var skip by remember { mutableIntStateOf(0) }
    var skipTick by remember { mutableIntStateOf(0) }
    // long press = play at 2x while the finger is down (like YouTube), back to the old speed on release
    var holdFast by remember { mutableStateOf(false) }
    var speedBeforeHold by remember { mutableFloatStateOf(1f) }

    // paused (by the button, or from outside): the controls show; a tap then hides them again, like YouTube
    LaunchedEffect(p.wantPlay) { if (!p.wantPlay) visible = true }

    // controls fade away by themselves while the video plays
    LaunchedEffect(visible, p.isPlaying, dragging, panel) {
        if (visible && p.isPlaying && !dragging && !panel) {
            delay(3000)
            visible = false
        }
    }
    LaunchedEffect(skipTick) {
        if (skipTick > 0) {
            delay(600)
            skip = 0
        }
    }

    val dur = p.duration.coerceAtLeast(1L)
    val frac = if (dragging) dragFraction else (p.position.toFloat() / dur).coerceIn(0f, 1f)
    val bufFrac = (p.buffered.toFloat() / dur).coerceIn(0f, 1f)
    val showControls = visible || p.ended
    // in fullscreen the bar keeps a little distance from the screen edge (its dot would be cut off there)
    val barInset = if (fullscreen) 12.dp else 0.dp
    val prevItem = VideoHost.prevItem
    val nextItem = VideoHost.nextItem

    fun openVideo(v: VideoItem) {
        if (fullscreen) onFullscreen()
        nav.push(Route.Video(v.id, v))
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        if (holdFast) {
                            holdFast = false
                            p.changeSpeed(speedBeforeHold)
                        }
                    },
                    onLongPress = {
                        if (p.wantPlay && !p.ended && p.error == null) {
                            speedBeforeHold = p.speed
                            holdFast = true
                            p.changeSpeed(2f)
                        }
                    },
                    onTap = { if (panel) panel = false else visible = !visible },
                    onDoubleTap = { off ->
                        val right = off.x > size.width / 2
                        p.seekBy(if (right) 10_000L else -10_000L)
                        skip = if (right) 1 else -1
                        skipTick++
                    }
                )
            }
    ) {
        // soft shading over the whole picture behind the controls
        AnimatedVisibility(showControls, enter = fadeIn(tween(160)), exit = fadeOut(tween(220))) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x73000000), 0.35f to Color(0x4D000000),
                        0.65f to Color(0x4D000000), 1f to Color(0x8C000000)
                    )
                )
            )
        }

        // top row: only the settings gear (and, in fullscreen, close + title)
        AnimatedVisibility(
            showControls && !dragging,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(220))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (fullscreen) {
                    GlassButton(Icons.Rounded.Close, "Close", onFullscreen)
                    Text(
                        title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .bouncyClickable(pressedScale = 0.9f, onClick = { panel = !panel }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Quality and speed", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }

        // "2x" while the finger is held down
        AnimatedVisibility(
            holdFast,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(160))
        ) {
            Row(
                Modifier.clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("2x", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Rounded.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.padding(start = 4.dp).size(18.dp))
            }
        }

        // double-tap feedback
        AnimatedVisibility(
            skip != 0,
            modifier = Modifier.align(if (skip < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 36.dp),
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f)
        ) {
            Box(
                Modifier.clip(CircleShape).background(Color(0x66000000)).padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(if (skip < 0) "−10 s" else "+10 s", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        // centre: spinner while loading / stalled, otherwise previous / play-pause / next
        if (p.buffering && p.firstFrame) {
            CtSpinner(Modifier.align(Alignment.Center))
        } else {
            AnimatedVisibility(
                showControls && p.firstFrame && !dragging,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    // like YouTube: restarts the video when it already played a few seconds, else the video before this one
                    GlassButton(
                        Icons.Rounded.SkipPrevious, "Previous",
                        onClick = {
                            if (p.position > 3000L || prevItem == null) p.seekTo(0L) else openVideo(prevItem)
                        },
                        size = 56.dp, iconSize = 32.dp,
                        enabled = prevItem != null || p.position > 3000L
                    )
                    GlassButton(
                        icon = when {
                            p.ended -> Icons.Rounded.Replay
                            p.wantPlay -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        description = "Play / pause",
                        onClick = { p.togglePlay(); visible = true },
                        size = 68.dp,
                        iconSize = 38.dp
                    )
                    GlassButton(
                        Icons.Rounded.SkipNext, "Next",
                        onClick = { nextItem?.let { openVideo(it) } },
                        size = 56.dp, iconSize = 32.dp,
                        enabled = nextItem != null
                    )
                }
            }
        }

        // while the red dot is held: the time it points at, big in the middle
        AnimatedVisibility(
            dragging,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(160))
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(ctTime((dragFraction * dur).toLong()), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                Text("  /  ${ctTime(p.duration)}", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }

        // bottom: time + fullscreen, and the red seek bar along the bottom edge
        AnimatedVisibility(
            showControls,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(220))
        ) {
            // the seek bar is at the very bottom; the time and the fullscreen button sit just above it, on top of the
            // bar's touch area (their own taps win, the rest of that area still seeks)
            Box(Modifier.fillMaxWidth().padding(bottom = barInset)) {
                CtSeekBar(
                    fraction = frac,
                    buffered = bufFrac,
                    onDragStart = { dragging = true; dragFraction = frac },
                    onDrag = { dragFraction = it },
                    onDragEnd = { f -> p.seekTo((f * dur).toLong()); dragging = false },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                )
                if (!dragging) {
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(start = 14.dp, end = 10.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // tap the time: first tap = time left, next tap = time played again (like YouTube)
                        Row(
                            Modifier
                                .pointerInput(Unit) { detectTapGestures { CtPrefs.showRemaining = !CtPrefs.showRemaining } }
                                .padding(end = 16.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            val first = if (CtPrefs.showRemaining) "-" + ctTime((p.duration - p.position).coerceAtLeast(0L)) else ctTime(p.position)
                            Text(first, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(" / ${ctTime(p.duration)}", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        GlassButton(
                            if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            "Fullscreen", onFullscreen, size = 36.dp, iconSize = 22.dp
                        )
                    }
                }
            }
        }

        if (!showControls) {
            CtProgressLine(frac, bufFrac, Modifier.align(Alignment.BottomCenter).padding(bottom = barInset))
        }

        AnimatedVisibility(
            panel,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f))
        ) {
            CtSettingsPopup(p, onClose = { panel = false })
        }
    }
}
