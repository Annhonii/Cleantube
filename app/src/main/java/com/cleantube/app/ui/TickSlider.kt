package com.cleantube.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pill-shaped tick slider (like the Pixel photo editor's): the selected value on the left, a row of ticks that
 * scrolls under a fixed needle in the middle, and an optional reset button on the right. Drag the ticks to change
 * the value; it snaps (with a soft tick) to the nearest value when you let go. Every value of [labels] has a long
 * tick, with short ones between them.
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
    resetIndex: Int? = null
) {
    if (labels.isEmpty()) return
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val last = labels.lastIndex
    val gap: Dp = if (compact) 46.dp else 64.dp
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val barH = if (compact) 44.dp else 58.dp

    val bg = if (dark) Color(0x33FFFFFF) else SurfaceHighest
    val accent = if (dark) Color.White else Accent
    val idle = if (dark) Color(0x80FFFFFF) else TextMuted
    val textColor = if (dark) Color.White else Accent

    var pos by remember { mutableFloatStateOf(index.coerceIn(0, last).toFloat()) }
    var committed by remember { mutableIntStateOf(index.coerceIn(0, last)) }
    var held by remember { mutableIntStateOf(index.coerceIn(0, last)) }
    var busy by remember { mutableStateOf(false) }
    var gen by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun commit(i: Int) {
        if (i != committed) {
            committed = i
            onPick(i)
        }
    }

    fun settle(target: Int, velocity: Float = 0f) {
        job?.cancel()
        busy = true
        held = target
        val g = ++gen
        job = scope.launch {
            try {
                animate(pos, target.toFloat(), velocity, spring(dampingRatio = 0.7f, stiffness = 320f)) { v, _ -> pos = v }
            } finally {
                if (g == gen) busy = false
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

    var first by remember { mutableStateOf(true) }
    LaunchedEffect(held) {
        if (first) first = false else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val drag = rememberDraggableState { d ->
        job?.cancel()
        busy = true
        gen++
        pos = (pos - d / gapPx).coerceIn(0f, last.toFloat())
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
            .then(if (dark) Modifier.glass(CircleShape, 0.35f) else Modifier.glass(CircleShape, 0.6f))
            .padding(horizontal = if (compact) 14.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            labels[held.coerceIn(0, last)],
            color = textColor,
            fontSize = if (compact) 14.sp else 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.width(if (compact) 54.dp else 84.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { v ->
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
                for (n in 0..total) {
                    val x = cx + (n * stepPx - pos * gapPx)
                    if (x < -w || x > size.width + w) continue
                    val major = n % (minor + 1) == 0
                    val h = if (major) longH else shortH
                    val edge = (1f - (abs(x - cx) / (size.width / 2f)).coerceIn(0f, 1f)).coerceIn(0f, 1f)
                    val a = (edge * 2.2f).coerceIn(0f, 1f)
                    val passed = x <= cx + 0.5f
                    val c = if (passed) accent else idle
                    drawLine(
                        c.copy(alpha = c.alpha * a * (if (major) 1f else 0.7f)),
                        Offset(x, cy - h / 2f), Offset(x, cy + h / 2f), w, StrokeCap.Round
                    )
                }
                // the needle
                drawLine(accent, Offset(cx, 0f), Offset(cx, size.height), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        if (resetIndex != null) {
            Box(
                Modifier.size(if (compact) 30.dp else 40.dp).clip(CircleShape).clickable {
                    settle(resetIndex.coerceIn(0, last))
                    commit(resetIndex.coerceIn(0, last))
                },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.RestartAlt, contentDescription = "Reset",
                    tint = if (dark) Color.White else TextPrimary,
                    modifier = Modifier.size(if (compact) 18.dp else 24.dp)
                )
            }
        }
    }
}
