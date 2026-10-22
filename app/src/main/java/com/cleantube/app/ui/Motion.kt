package com.cleantube.app.ui

import androidx.compose.foundation.layout.size

import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * One place for how the whole app moves, so every screen feels the same:
 * soft springs with a tiny overshoot for things you touch, long "emphasized" ease-outs for screens.
 */
object Motion {
    /** Fast start, very long soft landing. The curve behind most premium-feeling transitions. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For things leaving: slow start, quick exit. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * Press feedback instead of a grey ripple: the element squishes under the finger and springs back
 * with a little bounce. Put it FIRST in the modifier chain, so the background / clip scale with it.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 600f),
        label = "press"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * Fade + rise + tiny zoom the FIRST time [key] shows up. Scrolling an item out and back in does not
 * replay it ([seen] remembers). [delayMs] staggers the first few items of a list.
 */
fun Modifier.entrance(seen: MutableSet<Any>, key: Any, delayMs: Int = 0): Modifier = composed {
    val first = remember(key) { seen.add(key) }
    val p = remember(key) { Animatable(if (first) 0f else 1f) }
    LaunchedEffect(key) {
        if (first) {
            if (delayMs > 0) delay(delayMs.toLong())
            p.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 280f))
        }
    }
    this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 18.dp.toPx()
        val s = 0.975f + 0.025f * v
        scaleX = s
        scaleY = s
    }
}

/** Moving light over a placeholder while something loads. */
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "shimmerX"
    )
    this.drawWithContent {
        drawContent()
        val w = size.width
        val c = x * w
        drawRect(
            Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Bg.copy(alpha = 0.45f), Color.Transparent),
                startX = c - w * 0.35f,
                endX = c + w * 0.35f
            )
        )
    }
}

/** Placeholder with the same shape as a video row, shown while the first page loads. */
@Composable
fun VideoRowSkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Hairline)
                .shimmer()
        )
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Hairline).shimmer())
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(15.dp).clip(RoundedCornerShape(8.dp)).background(Hairline).shimmer())
                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Hairline).shimmer())
            }
        }
    }
}

/**
 * How one screen replaces another.
 * - a video opens like a card rising from the bottom (the page behind sinks back) and closes by sliding down
 * - other pushed screens slide in from the right with the old one drifting left (parallax); back reverses it
 * - tabs slide in the direction you moved in the tab bar, with a soft spring
 * The screen that should be on top is drawn above (depth = size of the back stack).
 */
fun AnimatedContentTransitionScope<Route>.screenTransition(nav: Nav, tabs: List<Route>): ContentTransform {
    val from = initialState
    val to = targetState
    val depth = nav.stack.size.toFloat()
    val enter: EnterTransition
    val exit: ExitTransition
    when {
        nav.lastKind == NavKind.PUSH && to is Route.Video -> {
            enter = slideInVertically(spring(dampingRatio = 0.88f, stiffness = 380f)) { (it * 0.16f).toInt() } +
                fadeIn(tween(280, easing = Motion.Emphasized)) +
                scaleIn(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(0.5f, 0.3f)
                )
            exit = scaleOut(tween(420, easing = Motion.Emphasized), targetScale = 0.94f) +
                fadeOut(tween(320, delayMillis = 40))
        }
        nav.lastKind == NavKind.POP && from is Route.Video -> {
            enter = fadeIn(tween(320)) + scaleIn(tween(420, easing = Motion.Emphasized), initialScale = 0.94f)
            exit = slideOutVertically(tween(340, easing = Motion.EmphasizedAccelerate)) { it } +
                fadeOut(tween(260, delayMillis = 70))
        }
        nav.lastKind == NavKind.PUSH -> {
            enter = slideInHorizontally(tween(440, easing = Motion.Emphasized)) { it } + fadeIn(tween(220))
            exit = slideOutHorizontally(tween(440, easing = Motion.Emphasized)) { -(it * 0.28f).toInt() } +
                fadeOut(tween(320))
        }
        nav.lastKind == NavKind.POP -> {
            enter = slideInHorizontally(tween(420, easing = Motion.Emphasized)) { -(it * 0.28f).toInt() } +
                fadeIn(tween(320))
            exit = slideOutHorizontally(tween(420, easing = Motion.Emphasized)) { it } +
                fadeOut(tween(280, delayMillis = 100))
        }
        else -> {
            val fi = tabs.indexOf(from)
            val ti = tabs.indexOf(to)
            if (fi < 0 || ti < 0) {
                enter = fadeIn(tween(260)) + scaleIn(tween(320, easing = Motion.Emphasized), initialScale = 0.97f)
                exit = fadeOut(tween(160))
            } else {
                val dir = if (ti > fi) 1 else -1
                enter = slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 520f)) { dir * (it * 0.22f).toInt() } +
                    fadeIn(tween(260, delayMillis = 50)) +
                    scaleIn(tween(320, easing = Motion.Emphasized), initialScale = 0.97f)
                exit = slideOutHorizontally(tween(260, easing = Motion.Emphasized)) { -dir * (it * 0.14f).toInt() } +
                    fadeOut(tween(150))
            }
        }
    }
    return ContentTransform(enter, exit, depth, null)
}
