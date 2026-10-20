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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
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

/** Thumbnail shown (slowly "breathing") with the spinner until the first picture of the video is on screen. */
@Composable
fun CtPoster(videoId: String, player: CtPlayer, modifier: Modifier = Modifier, fit: Boolean = false) {
    AnimatedVisibility(
        visible = !player.firstFrame,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(320)),
        modifier = modifier.fillMaxSize()
    ) {
        val t = rememberInfiniteTransition(label = "ctPoster")
        val scale by t.animateFloat(
            1f, 1.06f,
            infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath"
        )
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                contentDescription = null,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }
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
        Text(message, color = Color(0xFFDDDDDD), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Retry", onClick = onRetry)
            if (onOpen != null) Pill("Open in YouTube", onClick = onOpen, active = true)
        }
    }
}

/**
 * Slim seek bar: grey track, lighter "buffered" part, red played part. Tap or drag anywhere on it.
 * The touch area is taller than the line so it is easy to hit.
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
    val line by animateDpAsState(if (active) 6.dp else 3.dp, label = "line")
    val thumb by animateDpAsState(if (active) 16.dp else if (showThumb) 11.dp else 0.dp, label = "thumb")
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val accent = Accent

    Box(
        modifier
            .height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val f = (off.x / size.width).coerceIn(0f, 1f)
                    start(); end(f)
                }
            }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        active = true
                        last = (off.x / size.width).coerceIn(0f, 1f)
                        start(); move(last)
                    },
                    onDragEnd = { active = false; end(last) },
                    onDragCancel = { active = false; end(last) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        last = (change.position.x / size.width).coerceIn(0f, 1f)
                        move(last)
                    }
                )
            }
            .drawBehind {
                val cy = size.height / 2
                val th = line.toPx()
                val r = CornerRadius(th / 2, th / 2)
                val top = cy - th / 2
                drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, top), Size(size.width, th), r)
                drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(0f, top), Size(size.width * buffered.coerceIn(0f, 1f), th), r)
                drawRoundRect(accent, Offset(0f, top), Size(size.width * fraction.coerceIn(0f, 1f), th), r)
                if (thumb > 0.dp) drawCircle(accent, thumb.toPx() / 2, Offset(size.width * fraction.coerceIn(0f, 1f), cy))
            }
    )
}

/** The hairline shown at the very bottom while the controls are hidden. */
@Composable
fun CtProgressLine(fraction: Float, buffered: Float, modifier: Modifier = Modifier) {
    val accent = Accent
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                drawRect(Color.White.copy(alpha = 0.2f))
                drawRect(Color.White.copy(alpha = 0.35f), size = Size(size.width * buffered.coerceIn(0f, 1f), size.height))
                drawRect(accent, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
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
    iconSize: Dp = 22.dp
) {
    Box(
        modifier
            .size(size)
            .bouncyClickable(pressedScale = 0.9f, onClick = onClick)
            .clip(CircleShape)
            .background(Color(0x59000000)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun CtChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .bouncyClickable(pressedScale = 0.93f, onClick = onClick)
            .clip(CircleShape)
            .background(if (selected) Color.White else Color(0x26FFFFFF))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, color = if (selected) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun CtSettingsPanel(p: CtPlayer, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x73000000))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xEB161616))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val autoLabel = if (p.quality == 0 && p.currentHeight > 0) "Auto · ${p.currentHeight}p" else "Auto"
            Text("Quality", color = Color(0xFFAAAAAA), fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CtChip(autoLabel, p.quality == 0) { p.changeQuality(0) }
                p.heights.reversed().forEach { h -> CtChip("${h}p", p.quality == h) { p.changeQuality(h) } }
            }
            Text("Speed", color = Color(0xFFAAAAAA), fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    val label = if (s == 1f) "Normal" else (if (s % 1f == 0f) "${s.toInt()}x" else "${s}x")
                    CtChip(label, p.speed == s) { p.changeSpeed(s) }
                }
            }
        }
    }
}

/**
 * Minimal controls on top of the picture: tap = show / hide, double tap on the left / right = back / forward 10 s,
 * big play-pause in the middle, seek bar + time at the bottom, a settings sheet for quality and speed.
 * While the controls are hidden only a hairline of progress remains.
 */
@Composable
fun CtOverlay(
    p: CtPlayer,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = ""
) {
    var visible by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var panel by remember { mutableStateOf(false) }
    var skip by remember { mutableIntStateOf(0) }
    var skipTick by remember { mutableIntStateOf(0) }

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
    val showControls = visible || p.ended || !p.wantPlay

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
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
        // soft shading behind the controls
        AnimatedVisibility(showControls, enter = fadeIn(tween(160)), exit = fadeOut(tween(220))) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x8C000000), 0.3f to Color.Transparent,
                        0.65f to Color.Transparent, 1f to Color(0xA6000000)
                    )
                )
            )
        }

        // top row
        AnimatedVisibility(
            showControls,
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
                GlassButton(Icons.Rounded.Settings, "Quality and speed", { panel = true })
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

        // centre: spinner while loading / stalled, otherwise the play-pause button
        if (p.buffering && p.firstFrame) {
            CtSpinner(Modifier.align(Alignment.Center))
        } else {
            AnimatedVisibility(
                showControls && p.firstFrame,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f)
            ) {
                GlassButton(
                    icon = when {
                        p.ended -> Icons.Rounded.Replay
                        p.wantPlay -> Icons.Rounded.Pause
                        else -> Icons.Rounded.PlayArrow
                    },
                    description = "Play / pause",
                    onClick = { p.togglePlay(); if (p.wantPlay) visible = true },
                    size = 62.dp,
                    iconSize = 34.dp
                )
            }
        }

        // bottom: seek bar and time
        AnimatedVisibility(
            showControls,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(220))
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, bottom = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val shown = if (dragging) (dragFraction * dur).toLong() else p.position
                    Text(
                        "${ctTime(shown)}  /  ${ctTime(p.duration)}",
                        color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        "Fullscreen", onFullscreen, size = 36.dp, iconSize = 22.dp
                    )
                }
                CtSeekBar(
                    fraction = frac,
                    buffered = bufFrac,
                    onDragStart = { dragging = true; dragFraction = frac },
                    onDrag = { dragFraction = it },
                    onDragEnd = { f -> p.seekTo((f * dur).toLong()); dragging = false },
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                )
            }
        }

        if (!showControls) {
            CtProgressLine(frac, bufFrac, Modifier.align(Alignment.BottomCenter))
        }

        if (panel) CtSettingsPanel(p) { panel = false }
    }
}
