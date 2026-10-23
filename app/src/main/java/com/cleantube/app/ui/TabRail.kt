package com.cleantube.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private val FabSize = 56.dp
private val FabMargin = 16.dp

/**
 * Tab switching without a bottom bar: a round button in the bottom right corner that shows the open tab's icon.
 * Long press it and slide to the left: the other tabs unfold to its left as a pill. The button follows the finger
 * but is pulled onto a tab ("magnet") once it gets close, so it clicks onto each icon; the tab switches while you
 * rest on it. Let go and the pill folds back into the button, which then shows the new tab.
 */
@Composable
fun TabFab(nav: Nav, tabs: List<TabSpec>, enabled: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val navState = rememberUpdatedState(nav)
    val tabsState = rememberUpdatedState(tabs)
    val n = tabs.size

    var open by remember { mutableStateOf(false) }
    // the tab that was open when the pill unfolded; it sits under the finger, the others unfold to its left
    var baseRoute by remember { mutableStateOf<Route>(Route.Home) }
    var fingerDx by remember { mutableFloatStateOf(0f) }   // how far the finger went left of the button's centre

    val pitch = with(density) { FabSize.toPx() }
    val slots = remember(open, baseRoute, tabs) {
        val base = tabs.firstOrNull { it.route == baseRoute }
        if (base == null) tabs else listOf(base) + tabs.filter { it !== base }
    }
    val maxDx = (slots.size - 1).coerceAtLeast(0) * pitch

    // magnet: inside this distance of a tab the button is pulled onto it, stronger the closer it gets
    val reach = pitch * 0.55f
    val slot = (fingerDx / pitch).roundToInt().coerceIn(0, (slots.size - 1).coerceAtLeast(0))
    val magnetDx = run {
        val dx = fingerDx.coerceIn(0f, maxDx + pitch * 0.2f)
        val c = (dx / pitch).roundToInt().coerceIn(0, (slots.size - 1).coerceAtLeast(0)) * pitch
        val d = dx - c
        val pull = (1f - abs(d) / reach).coerceIn(0f, 1f)
        val sm = pull * pull * (3f - 2f * pull)
        dx - d * sm
    }
    val slotsNow by rememberUpdatedState(slots)
    val slotNow by rememberUpdatedState(slot)
    val blobDx by animateFloatAsState(
        if (open) magnetDx else 0f,
        spring(dampingRatio = 0.58f, stiffness = 520f),
        label = "fabBlob"
    )
    val openProg by animateFloatAsState(
        if (open) 1f else 0f,
        spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "fabOpen"
    )

    // a click each time the button locks onto another tab
    LaunchedEffect(slot) {
        if (open) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    // the tab switches once the button has rested on it for a moment (sweeping past skips the ones in between)
    LaunchedEffect(open, slot) {
        if (!open) return@LaunchedEffect
        delay(120)
        val t = slots.getOrNull(slot) ?: return@LaunchedEffect
        if (t.route != navState.value.current) navState.value.selectTab(t.route)
    }
    LaunchedEffect(enabled) { if (!enabled) open = false }

    if (!enabled && openProg < 0.01f) return

    val shownTab = if (open) slots.getOrNull(slot) else tabs.firstOrNull { it.route == nav.current }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pad = Modifier.padding(end = FabMargin, bottom = FabMargin + bottomInset)

    BoxWithConstraints(modifier.fillMaxSize()) {
        // ---- the pill that unfolds to the left
        if (openProg > 0.01f) {
            val full = FabSize * slots.size
            val w = FabSize + (full - FabSize) * openProg
            val shadowColor = Color.Black.copy(alpha = 0.35f)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .then(pad)
                    .width(w)
                    .height(FabSize)
                    .graphicsLayer { alpha = openProg.coerceIn(0f, 1f) }
                    .shadow(12.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                    .clip(CircleShape)
                    .background(NavBg)
            )
            slots.forEachIndexed { k, t ->
                // the icons pop in one after the other, nearest first
                val p = ((openProg * (slots.size + 1)) - k).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = FabMargin + FabSize * k, bottom = FabMargin + bottomInset)
                        .size(FabSize)
                        .graphicsLayer {
                            alpha = p
                            scaleX = 0.6f + 0.4f * p
                            scaleY = 0.6f + 0.4f * p
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(t.icon, contentDescription = t.label, tint = OnNavBg.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                }
            }
        }

        // ---- the button itself (follows the finger with the magnet while the pill is open)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .then(pad)
                .offset { IntOffset(-blobDx.roundToInt(), 0) }
                .size(FabSize)
                .graphicsLayer {
                    val s = 1f + 0.1f * openProg
                    scaleX = s
                    scaleY = s
                }
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Accent),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(shownTab, animationSpec = tween(120), label = "fabIcon") { t ->
                if (t != null) Icon(t.icon, contentDescription = t.label, tint = OnAccent, modifier = Modifier.size(26.dp))
            }
        }

        // ---- the touch area: the resting button. Long press, then slide left.
        if (enabled && n > 1) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .then(pad)
                    .size(FabSize)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val up = withTimeoutOrNull(viewConfig.longPressTimeoutMillis) { waitForUpOrCancellation() }
                            if (up != null) return@awaitEachGesture   // a plain tap or a flick: nothing to do
                            // ---- long press: unfold
                            baseRoute = navState.value.current
                            fingerDx = 0f
                            open = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val cx = size.width / 2f
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                fingerDx = cx - ch.position.x
                            }
                            // ---- finger up: land on the tab the button sits on and fold back
                            val t = slotsNow.getOrNull(slotNow)
                            if (t != null && t.route != navState.value.current) navState.value.selectTab(t.route)
                            open = false
                        }
                    }
            )
        }
    }
}
