package com.cleantube.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pill-shaped tick slider (like the Pixel photo editor's): the selected value on the left, a row of ticks that
 * scrolls under a fixed needle in the middle, and an optional reset button on the right. Every value of [labels]
 * has a long tick, with short ones between them.
 *
 * Motion:
 *  - Let go (or tap a tick) and the ticks glide to the nearest value on a bouncy spring; a tap never warps.
 *  - The needle grows (taller and fatter) while you touch / drag / glide, and eases back when idle.
 *  - Ticks near the needle react like a magnifier: the long tick under it rises, the short ones next to it
 *    shrink and fade, more strongly while you hold it.
 *  - The needle leans and stretches a little with the drag speed, then settles upright.
 *  - The value badge bounces when the value changes; the reset icon spins a full turn when tapped.
 *  - A haptic tick fires once for every tick mark the ruler crosses (drag or glide), never more.
 *
 * [live] = report every value you pass; otherwise only the one you land on (e.g. quality, which reloads the video).
 * [dark] = colours for slider drawn over video / black panels.
 */
@Composable
fun TickSlider(
    labels: List<String>,
    index: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    live: Boolean = true,
    dark: Boolean = false,
    compact: Boolean = false,
    resetIndex: Int? = null,
    /** Pixel look: grey ticks, a fat white needle, [bubble] puts the value in a round chip at the left. */
    pixel: Boolean = false,
    bubble: Boolean = false,
    /** Optional colours (e.g. the colours of a video); null = the app's normal ones. */
    accentColor: Color? = null,
    trackColor: Color? = null,
    idleColor: Color? = null,
    contentColor: Color? = null
) {
    if (labels.isEmpty()) return
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val last = labels.lastIndex
    val gap: Dp = if (compact) 42.dp else 56.dp
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val barH = if (compact) 40.dp else 48.dp

    val bg = trackColor ?: if (dark) Color(0x33FFFFFF) else SurfaceHighest
    val accent = accentColor ?: if (dark) Color.White else Accent
    val idle = idleColor ?: if (dark) Color(0x80FFFFFF) else TextMuted
    val textColor = accentColor ?: if (dark) Color.White else Accent
    val needleColor = contentColor ?: TextPrimary

    var pos by remember { mutableFloatStateOf(index.coerceIn(0, last).toFloat()) }
    var committed by remember { mutableIntStateOf(index.coerceIn(0, last)) }
    var held by remember { mutableIntStateOf(index.coerceIn(0, last)) }
    var busy by remember { mutableStateOf(false) }
    var gen by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }

    // touch state, drives the needle's grow / magnify animation
    var pressed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    // signed speed of the ruler in ticks per second (drag or spring), drives the needle's lean + stretch
    var motion by remember { mutableFloatStateOf(0f) }
    var lastDragNs by remember { mutableLongStateOf(0L) }

    fun commit(i: Int) {
        if (i != committed) {
            committed = i
            onPick(i)
        }
    }

    /** Spring the ruler to [target]; [velocity] (ticks/s) carries the fling into the spring. */
    fun settle(target: Int, velocity: Float = 0f) {
        job?.cancel()
        busy = true
        held = target
        val g = ++gen
        job = scope.launch {
            try {
                animate(pos, target.toFloat(), velocity, spring(dampingRatio = 0.55f, stiffness = 300f)) { v, vel ->
                    pos = v
                    motion = vel
                }
            } finally {
                if (g == gen) {
                    busy = false
                    motion = 0f
                }
            }
        }
    }

    // changed from outside (the 2x hold, a menu elsewhere): follow it
    LaunchedEffect(index, busy) {
        val t = index.coerceIn(0, last)
        if (!busy && t != committed) {
            committed = t
            settle(t)
        }
    }

    // One haptic per tick mark crossed. A little hysteresis keeps a shaky finger or a spring wobbling around
    // the half-way point from buzzing twice.
    LaunchedEffect(Unit) {
        var tick = pos.roundToInt()
        snapshotFlow { pos }.collect { p ->
            if (abs(p - tick) >= 0.55f) {
                tick = p.roundToInt()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    // a finger that rests on the ruler is not moving: let the lean fade out
    LaunchedEffect(dragging) {
        while (dragging) {
            delay(16)
            if (System.nanoTime() - lastDragNs > 50_000_000L) motion *= 0.7f
        }
    }

    // needle: grows while touched, half-way while gliding
    val act by animateFloatAsState(
        if (pressed || dragging) 1f else if (busy) 0.4f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "tickActive"
    )

    // value badge bounce
    val bump = remember { Animatable(1f) }
    var firstValue by remember { mutableStateOf(true) }
    LaunchedEffect(held) {
        if (firstValue) firstValue = false
        else {
            bump.snapTo(0.82f)
            bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 450f))
        }
    }

    // reset icon: a full turn each tap
    val spin = remember { Animatable(0f) }

    val tapAt by rememberUpdatedState { x: Float, width: Float ->
        val t = (pos + (x - width / 2f) / gapPx).roundToInt().coerceIn(0, last)
        settle(t)
        commit(t)
    }

    val drag = rememberDraggableState { d ->
        job?.cancel()
        busy = true
        gen++
        pos = (pos - d / gapPx).coerceIn(0f, last.toFloat())
        val now = System.nanoTime()
        if (lastDragNs != 0L) {
            val dt = ((now - lastDragNs) / 1e9f).coerceAtLeast(0.004f)
            motion = motion * 0.6f + (-d / gapPx / dt) * 0.4f
        }
        lastDragNs = now
        val r = pos.roundToInt().coerceIn(0, last)
        if (r != held && abs(pos - r) <= 0.3f) {
            held = r
            if (live) commit(r)
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(barH)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pixel && bubble) {
            Box(
                Modifier
                    .size(barH - 10.dp)
                    .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
                    .clip(CircleShape)
                    .background(TextPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(labels[held.coerceIn(0, last)], color = Bg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
        } else {
            Text(
                labels[held.coerceIn(0, last)],
                color = if (pixel) TextMuted else textColor,
                fontSize = if (compact) 13.sp else if (pixel) 18.sp else 16.sp,
                fontWeight = if (pixel) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .width(if (compact) 50.dp else if (pixel) 76.dp else 72.dp)
                    .graphicsLayer {
                        scaleX = bump.value; scaleY = bump.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { o -> tapAt(o.x, size.width.toFloat()) }
                    )
                }
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        dragging = true
                        lastDragNs = 0L
                    },
                    onDragStopped = { v ->
                        dragging = false
                        lastDragNs = 0L
                        val vi = -v / gapPx
                        val t = (pos + vi * 0.12f).roundToInt().coerceIn(0, last)
                        settle(t, vi)
                        commit(t)
                    }
                )
        ) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val minor = 3
                val stepPx = gapPx / (minor + 1)
                val total = last * (minor + 1)
                val longH = size.height * 0.46f
                val shortH = size.height * 0.28f
                val w = 2.2.dp.toPx()
                val touch = act
                // how far the needle's "magnetic field" reaches, and how hard it pulls
                val reach = gapPx * 0.5f
                val pull = 0.6f + 0.4f * touch
                for (n in 0..total) {
                    val x = cx + (n * stepPx - pos * gapPx)
                    if (x < -w || x > size.width + w) continue
                    val major = n % (minor + 1) == 0
                    val edge = (1f - (abs(x - cx) / (size.width / 2f)).coerceIn(0f, 1f)).coerceIn(0f, 1f)
                    var alpha = (edge * 2.2f).coerceIn(0f, 1f) * (if (major) 1f else 0.7f)
                    var h = if (major) longH else shortH

                    // 3D-wave: smoothstep falloff from the needle
                    val t = (1f - abs(x - cx) / reach).coerceIn(0f, 1f)
                    val s = t * t * (3f - 2f * t) * pull
                    if (major) {
                        h *= 1f + 0.5f * s            // the tick under the needle rises
                    } else {
                        h *= 1f - 0.4f * s            // its short neighbours shrink...
                        alpha *= 1f - 0.7f * s        // ...and fade
                    }

                    val passed = x <= cx + 0.5f
                    val c = if (passed && !pixel) accent else idle
                    drawLine(
                        c.copy(alpha = c.alpha * alpha),
                        Offset(x, cy - h / 2f), Offset(x, cy + h / 2f), w, StrokeCap.Round
                    )
                }

                // the needle: fatter + taller while touched, leans and stretches with the speed
                val lean = (motion / 7f).coerceIn(-1f, 1f)
                val stretch = 1f + 0.4f * abs(lean)
                if (pixel) {
                    val nh = size.height * (0.5f + 0.14f * touch)          // 24dp -> 31dp on a 48dp bar
                    val nw = (6.dp.toPx() + 2.5.dp.toPx() * touch) * stretch // 6dp -> 8.5dp
                    rotate(-lean * 8f, Offset(cx, cy)) {
                        drawLine(needleColor, Offset(cx, cy - nh / 2f), Offset(cx, cy + nh / 2f), nw, StrokeCap.Round)
                    }
                } else {
                    val nw = (2.dp.toPx() + 1.dp.toPx() * touch) * stretch
                    rotate(-lean * 6f, Offset(cx, cy)) {
                        drawLine(accent, Offset(cx, 0f), Offset(cx, size.height), nw, StrokeCap.Round)
                    }
                }
            }
        }
        if (resetIndex != null) {
            Box(
                Modifier.size(if (compact) 28.dp else 36.dp).clip(CircleShape).clickable {
                    scope.launch { spin.animateTo(spin.value + 360f, tween(520, easing = FastOutSlowInEasing)) }
                    settle(resetIndex.coerceIn(0, last))
                    commit(resetIndex.coerceIn(0, last))
                },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.RestartAlt, contentDescription = "Reset",
                    tint = contentColor ?: if (dark) Color.White else TextPrimary,
                    modifier = Modifier
                        .size(if (compact) 16.dp else 20.dp)
                        .graphicsLayer { rotationZ = spin.value }
                )
            }
        }
    }
}

// ---------- previews ----------

private val PreviewSpeeds = listOf("0.25x", "0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")

/** The playback settings card, light: speed slider with the round value badge and the reset button. */
@Preview(showBackground = true, widthDp = 380, name = "Playback card, light")
@Composable
private fun TickSliderLightPreview() {
    var i by remember { mutableIntStateOf(3) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Speed  ${PreviewSpeeds[i]}", color = TextPrimary, fontWeight = FontWeight.Bold)
        TickSlider(PreviewSpeeds, i, { i = it }, resetIndex = 3, pixel = true, bubble = true)
        Text("Quality", color = TextPrimary, fontWeight = FontWeight.Bold)
        var q by remember { mutableIntStateOf(0) }
        TickSlider(listOf("Auto", "360p", "480p", "720p", "1080p"), q, { q = it }, live = false, resetIndex = 0, pixel = true)
    }
}

/** The compact version drawn over video (mini player popup, Shorts menu). */
@Preview(showBackground = true, backgroundColor = 0xFF101010, widthDp = 320, name = "Over video, dark compact")
@Composable
private fun TickSliderDarkPreview() {
    var i by remember { mutableIntStateOf(3) }
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TickSlider(PreviewSpeeds, i, { i = it }, dark = true, compact = true, resetIndex = 3)
    }
}
