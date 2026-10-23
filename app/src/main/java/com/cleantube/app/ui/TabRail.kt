package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tab switching without a bottom bar. Put a finger on the left screen edge and slide it a little to the right: a slim
 * rail with one icon per tab slides in. Without lifting the finger, slide up or down: a springy highlight follows the
 * finger, the tab switches, and a small rounded card with the tab's icon and name flashes in the middle of the screen.
 * Lift the finger and the rail slides away.
 */
@Composable
fun TabRailOverlay(nav: Nav, tabs: List<TabSpec>, enabled: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val navState = rememberUpdatedState(nav)
    val tabsState = rememberUpdatedState(tabs)

    var open by remember { mutableStateOf(false) }
    var baseIdx by remember { mutableIntStateOf(0) }
    var startY by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var railTop by remember { mutableFloatStateOf(0f) }
    var toast by remember { mutableStateOf<TabSpec?>(null) }
    var toastKey by remember { mutableIntStateOf(0) }
    var toastOn by remember { mutableStateOf(false) }

    val n = tabs.size
    val itemPx = with(density) { 52.dp.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }
    val padPx = with(density) { 6.dp.toPx() }
    val pitch = itemPx + gapPx
    val railH = padPx * 2 + n * itemPx + (n - 1).coerceAtLeast(0) * gapPx
    val sel by remember(n) {
        derivedStateOf { (baseIdx + ((fingerY - startY) / pitch).roundToInt()).coerceIn(0, (n - 1).coerceAtLeast(0)) }
    }

    // a soft tick each time the finger moves onto another tab
    LaunchedEffect(sel) {
        if (open) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    // the tab switches once the finger rests on it for a moment (sweeping past the ones in between skips them)
    LaunchedEffect(open, sel) {
        if (!open) return@LaunchedEffect
        delay(110)
        val t = tabsState.value.getOrNull(sel) ?: return@LaunchedEffect
        if (t.route != navState.value.current) {
            navState.value.selectTab(t.route)
            toast = t
            toastKey++
        }
    }
    LaunchedEffect(toastKey) {
        if (toastKey == 0) return@LaunchedEffect
        toastOn = true
        delay(850)
        toastOn = false
    }
    LaunchedEffect(enabled) { if (!enabled) open = false }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenH = constraints.maxHeight.toFloat()
        val topInset = WindowInsets.statusBars.getTop(density).toFloat()
        val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()
        val margin = with(density) { 8.dp.toPx() }
        val firstCenter = railTop + padPx + itemPx / 2f

        // where the highlight wants to be: on the current tab at first, then following the finger (inside the rail)
        val target = (firstCenter + baseIdx * pitch + (fingerY - startY))
            .coerceIn(firstCenter, firstCenter + (n - 1).coerceAtLeast(0) * pitch)
        val hl by animateFloatAsState(target, spring(dampingRatio = 0.5f, stiffness = 380f), label = "railHighlight")

        // small handle on the edge so the gesture can be found
        val handleA by animateFloatAsState(if (enabled && !open) 0.4f else 0f, tween(200), label = "railHandle")
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
                .size(width = 4.dp, height = 44.dp)
                .graphicsLayer { alpha = handleA }
                .clip(CircleShape)
                .background(TextMuted)
        )

        if (enabled && n > 1) {
            // keeps the system's back gesture off the middle of the edge
            Box(Modifier.align(Alignment.CenterStart).width(26.dp).height(260.dp).systemGestureExclusion())
            // the edge strip that starts the gesture
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(26.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val slop = 14.dp.toPx()
                            val x0 = down.position.x
                            val y0 = down.position.y
                            var opened = false
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                val dx = ch.position.x - x0
                                val dy = ch.position.y - y0
                                if (!opened) {
                                    if (dx > slop && dx > abs(dy)) {
                                        opened = true
                                        val list = tabsState.value
                                        baseIdx = list.indexOfFirst { it.route == navState.value.current }.coerceAtLeast(0)
                                        startY = ch.position.y
                                        fingerY = ch.position.y
                                        val want = ch.position.y - (padPx + baseIdx * pitch + itemPx / 2f)
                                        railTop = want.coerceIn(
                                            topInset + margin,
                                            (screenH - bottomInset - margin - railH).coerceAtLeast(topInset + margin)
                                        )
                                        open = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else if (abs(dy) > slop * 2 && abs(dy) > dx) {
                                        break
                                    }
                                }
                                if (opened) {
                                    ch.consume()
                                    fingerY = ch.position.y
                                }
                            }
                            if (opened) {
                                // finger up: settle on the tab under it right away and let the rail go
                                val t = tabsState.value.getOrNull(sel)
                                if (t != null && t.route != navState.value.current) {
                                    navState.value.selectTab(t.route)
                                    toast = t
                                    toastKey++
                                }
                                open = false
                            }
                        }
                    }
            )
        }

        // ---- the rail
        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally(spring(dampingRatio = 0.62f, stiffness = 330f)) { -it } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(220)) { -it } + fadeOut(tween(180))
        ) {
            val shadowColor = Color.Black.copy(alpha = 0.35f)
            Box(
                Modifier
                    .offset { IntOffset(margin.roundToInt(), railTop.roundToInt()) }
                    .width(60.dp)
                    .height(with(density) { railH.toDp() })
                    .shadow(12.dp, RoundedCornerShape(30.dp), ambientColor = shadowColor, spotColor = shadowColor)
                    .clip(RoundedCornerShape(30.dp))
                    .background(NavBg)
            ) {
                // the springy highlight
                Box(
                    Modifier
                        .offset { IntOffset(with(density) { 6.dp.roundToPx() }, (hl - railTop - itemPx / 2f).roundToInt()) }
                        .size(48.dp, 52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Accent)
                )
                Column(
                    Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { i, t ->
                        val center = railTop + padPx + itemPx / 2f + i * pitch
                        val near = (1f - min(abs(center - hl) / pitch, 1f))
                        Box(Modifier.size(48.dp, 52.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                t.icon, contentDescription = t.label,
                                tint = if (near > 0.5f) OnAccent else OnNavBg.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        val s = 1f + 0.3f * near
                                        scaleX = s
                                        scaleY = s
                                    }
                            )
                        }
                    }
                }
            }
        }

        // ---- the card that flashes in the middle of the screen
        AnimatedVisibility(
            visible = toastOn,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(120)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.86f),
            exit = fadeOut(tween(260)) + scaleOut(tween(260), targetScale = 0.94f)
        ) {
            val t = toast
            if (t != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(NavBg.copy(alpha = 0.94f))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(t.icon, contentDescription = null, tint = OnNavBg, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(t.label, color = OnNavBg, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}
