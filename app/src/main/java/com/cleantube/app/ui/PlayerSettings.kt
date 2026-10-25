package com.cleantube.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Whether the playback settings card is open under the player (the video page draws it, like the comments card). */
object PlayerSettingsHost {
    var open by mutableStateOf(false)
}

private val SpeedSteps = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(s: Float): String =
    if (s % 1f == 0f) "${s.toInt()}x" else "${s.toString().trimEnd('0')}x"

/**
 * The playback settings card: speed (wheel) and quality (stepper) as rubbery horizontal sliders, then the sound
 * track and the captions. Drag the header down or tap outside to close.
 */
@Composable
fun PlayerSettingsSheet(p: CtPlayer, onClose: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(p) { p.ensureExtras() }

    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val land = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // landscape: a short card (at most ~70% of the screen height) so it doesn't cover the video
    val maxH = if (land) (cfg.screenHeightDp * 0.7f).dp else 600.dp

    var dragY by remember { mutableFloatStateOf(0f) }
    val closeAt = with(LocalDensity.current) { 90.dp.toPx() }
    suspend fun settle(velocity: Float) {
        if (dragY > closeAt || velocity > 1500f) onClose()
        else animate(dragY, 0f) { v, _ -> dragY = v }
    }
    val headerDrag = rememberDraggableState { d -> dragY = (dragY + d).coerceAtLeast(0f) }

    Column(
        modifier
            .offset { IntOffset(0, dragY.roundToInt()) }
            .fillMaxWidth()
            .heightIn(max = maxH)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Card)
            // swallow taps so the page underneath doesn't react
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            Modifier.fillMaxWidth().draggable(
                state = headerDrag,
                orientation = Orientation.Vertical,
                onDragStopped = { v -> settle(v) }
            )
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Hairline)
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 0.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Playback", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Outlined.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        val gapV = if (land) 10.dp else 14.dp

        @Composable fun SpeedBlock() {
            SettingsBlock("Speed", speedLabel(p.speed)) {
                val idx = SpeedSteps.indexOfFirst { abs(it - p.speed) < 0.01f }.coerceAtLeast(0)
                TickSlider(
                    labels = SpeedSteps.map { speedLabel(it) }, index = idx, live = true,
                    resetIndex = SpeedSteps.indexOf(1f), pixel = true, bubble = true,
                    onPick = { p.changeSpeed(SpeedSteps[it]) }
                )
            }
        }

        @Composable fun QualityBlock() {
            val opts = remember(p.heights) { listOf(0) + p.heights }
            SettingsBlock(
                "Quality",
                if (p.quality == 0) (if (p.currentHeight > 0) "Auto \u00b7 ${p.currentHeight}p" else "Auto") else "${p.quality}p"
            ) {
                TickSlider(
                    labels = opts.map { if (it == 0) "Auto" else "${it}p" },
                    index = opts.indexOf(p.quality).coerceAtLeast(0), live = false,
                    resetIndex = 0, pixel = true,
                    onPick = { p.changeQuality(opts[it]) }
                )
            }
        }

        @Composable fun AudioBlock() {
            SettingsBlock("Audio source", null) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.audioSources.forEachIndexed { i, a ->
                        val l = a.label.lowercase()
                        val ic = when {
                            "original" in l -> Icons.Rounded.GraphicEq
                            "enhanc" in l -> Icons.Rounded.AutoAwesome
                            else -> null
                        }
                        SheetChip(a.label, ic, p.audioIndex == i) { p.changeAudio(i) }
                    }
                    if (p.extrasState == 1) {
                        Text("loading\u2026", color = TextMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp))
                    }
                }
            }
        }

        // captions: switch + the language to read
        @Composable fun CaptionsBlock() {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val none = p.extrasState == 2 && p.subtitles.isEmpty()
                val sub = when {
                    p.extrasFailed -> "Couldn't load captions \u00b7 tap to retry"
                    p.extrasState == 1 -> "Loading\u2026"
                    none -> "Not available for this video"
                    p.captionsOn && p.cueState == 1 -> "Loading captions\u2026"
                    p.captionsOn && p.cueState == 3 -> "Couldn't read this caption file"
                    p.captionsOn && p.subtitles.isNotEmpty() -> p.subtitles.getOrNull(p.captionIndex)?.label.orEmpty()
                    else -> "Off"
                }
                ToggleCard(
                    "Captions", sub, p.captionsOn && !none, enabled = !none,
                    onClick = if (p.extrasFailed) ({ p.ensureExtras() }) else null
                ) { p.setCaptions(it) }
                if (p.subtitles.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.subtitles.forEachIndexed { i, t ->
                            SheetChip(t.label, null, p.captionsOn && p.captionIndex == i) { p.pickCaption(i) }
                        }
                    }
                }
            }
        }

        @Composable fun LoopBlock() {
            ToggleCard("Loop video", if (p.loopOn) "On" else "Off", p.loopOn, enabled = true, onClick = null) { p.setLooping(it) }
        }

        if (land) {
            // landscape: two columns so the card stays short and leaves the video visible
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gapV)) {
                    SpeedBlock(); QualityBlock()
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gapV)) {
                    AudioBlock(); CaptionsBlock(); LoopBlock()
                }
            }
        } else {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(gapV)
            ) {
                SpeedBlock(); QualityBlock(); AudioBlock(); CaptionsBlock(); LoopBlock()
            }
        }
    }
}

/** A tonal chip: the chosen one is filled, the others are flat; the colours glide when the choice changes. */
@Composable
private fun SheetChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, selected: Boolean, onClick: () -> Unit) {
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) SecondaryContainer else SurfaceHighest, androidx.compose.animation.core.tween(280), label = "chipBg"
    )
    val fg by androidx.compose.animation.animateColorAsState(
        if (selected) OnSecondaryContainer else TextPrimary, androidx.compose.animation.core.tween(280), label = "chipFg"
    )
    Row(
        Modifier
            .height(38.dp)
            .bouncyClickable(pressedScale = 0.95f, onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A rounded card with a title, a status line and a switch. */
@Composable
private fun ToggleCard(
    title: String,
    status: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceHighest)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(status, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SettingsBlock(title: String, value: String?, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (value != null) Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/**
 * A horizontal picker with a rubbery feel: the values slide under your finger, fade away from the centre, stretch
 * with resistance past either end and spring (with a little overshoot) onto the nearest value when you let go.
 * [live] = report every value you pass (speed); otherwise only the one you land on (quality: it reloads the video).
 */
@Composable
private fun RubberPicker(
    count: Int,
    index: Int,
    itemWidth: Dp,
    height: Dp,
    live: Boolean,
    fade: Float,
    fadeEdges: Boolean,
    onPick: (Int) -> Unit,
    overlay: @Composable BoxScope.(step: (Int) -> Unit, pos: () -> Float) -> Unit = { _, _ -> },
    item: @Composable BoxScope.(i: Int, selected: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val last = (count - 1).coerceAtLeast(0)
    val itemPx = with(LocalDensity.current) { itemWidth.toPx() }
    var pos by remember { mutableFloatStateOf(index.coerceIn(0, last).toFloat()) }
    var committed by remember { mutableIntStateOf(index.coerceIn(0, last)) }
    var job by remember { mutableStateOf<Job?>(null) }
    val sel by remember { derivedStateOf { pos.roundToInt().coerceIn(0, last) } }
    // true while the finger is down or the spring is still settling (outside changes must not fight it)
    var busy by remember { mutableStateOf(false) }
    var gen by remember { mutableIntStateOf(0) }

    // The value you are "on". It only changes once the wheel is clearly closer to another value (a dead zone around
    // the half-way point), so holding it between two values doesn't flip back and forth.
    var held by remember { mutableIntStateOf(index.coerceIn(0, last)) }
    fun updateHeld() {
        val r = pos.roundToInt().coerceIn(0, last)
        if (r != held && abs(pos - r) <= 0.3f) held = r
    }

    // a soft tick every time the value you are on changes
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(held) {
        if (first) first = false else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

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
                animate(pos, target.toFloat(), velocity, spring(dampingRatio = 0.5f, stiffness = 230f)) { v, _ -> pos = v }
            } finally {
                // only the newest spring may clear the flag (an older one being cancelled must not)
                if (g == gen) busy = false
            }
        }
    }

    // changed from outside (e.g. the 2x hold gesture): follow it
    LaunchedEffect(index, busy) {
        val t = index.coerceIn(0, last)
        if (!busy && t != committed) {
            committed = t
            settle(t)
        }
    }

    val drag = rememberDraggableState { d ->
        job?.cancel()
        busy = true
        gen++
        val move = -d / itemPx
        // past either end the finger has to pull harder and harder: that is the rubber
        val over = when {
            pos < 0f && move < 0f -> -pos
            pos > last && move > 0f -> pos - last
            else -> 0f
        }
        pos = (pos + move / (1f + over * 3.5f)).coerceIn(-0.8f, last + 0.8f)
        updateHeld()
        if (live) commit(held)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceHighest)
            .draggable(
                state = drag,
                orientation = Orientation.Horizontal,
                onDragStopped = { v ->
                    val vi = -v / itemPx
                    val t = (pos + vi * 0.16f).roundToInt().coerceIn(0, last)
                    settle(t, vi)
                    commit(t)
                }
            )
    ) {
        val centerPx = constraints.maxWidth / 2f
        for (i in 0..last) {
            Box(
                Modifier
                    .width(itemWidth)
                    .fillMaxHeight()
                    .offset { IntOffset((centerPx + (i - pos) * itemPx - itemPx / 2f).roundToInt(), 0) }
                    .graphicsLayer {
                        val d = abs(i - pos)
                        alpha = (1f - d * fade).coerceIn(0f, 1f)
                        val s = 1f - 0.22f * min(d, 1.5f) / 1.5f
                        scaleX = s
                        scaleY = s
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        settle(i)
                        commit(i)
                    },
                contentAlignment = Alignment.Center
            ) { item(i, i == sel) }
        }
        if (fadeEdges) {
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(44.dp)
                    .background(Brush.horizontalGradient(listOf(SurfaceHighest, Color.Transparent)))
            )
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(44.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, SurfaceHighest)))
            )
        }
        overlay({ delta ->
            val t = (sel + delta).coerceIn(0, last)
            settle(t)
            commit(t)
        }, { pos })
    }
}

/** The caption line over the picture (drawn by the app itself, so it follows the player's position). */
@Composable
internal fun BoxScope.CtCaptions(p: CtPlayer, fullscreen: Boolean) {
    if (!p.captionsOn || p.cues.isEmpty()) return
    val pos = p.position
    val cue = remember(p.cues, pos / 120L) { p.cues.lastOrNull { pos >= it.startMs && pos < it.endMs } }
    if (cue != null) {
        Text(
            cue.text,
            color = Color.White,
            fontSize = if (fullscreen) 18.sp else 14.sp,
            lineHeight = if (fullscreen) 24.sp else 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = if (fullscreen) 56.dp else 30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xB3000000))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
