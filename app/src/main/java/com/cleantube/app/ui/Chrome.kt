package com.cleantube.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.DlStatus
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Session
import com.cleantube.app.data.Store
import com.cleantube.app.data.Downloads

/** Top bar of a pushed screen: back arrow + title. */
@Composable
fun ScreenTitle(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(68.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Card)
                    .bouncyClickable(pressedScale = 0.88f, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
        } else {
            Spacer(Modifier.width(6.dp))
        }
        Text(
            title,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/** One entry of the top tab bar. [id] is what Settings saves for the order / hidden list. */
data class TabSpec(val id: String, val route: Route, val label: String, val icon: ImageVector)

/** Every tab the app has, in the default order. */
val AllTabs = listOf(
    TabSpec("shorts", Route.Shorts, "Shorts", Icons.Rounded.LocalFireDepartment),
    TabSpec("home", Route.Home, "Home", Icons.Rounded.Home),
    TabSpec("feed", Route.Feed, "New", Icons.Rounded.Subscriptions),
    TabSpec("library", Route.Library, "Library", Icons.Rounded.VideoLibrary)
)

/** All tabs in the user's order, hidden ones included (tabs the saved order doesn't know yet go to the end). */
fun orderedTabs(store: Store): List<TabSpec> {
    val byId = AllTabs.associateBy { it.id }
    val saved = store.tabOrder.mapNotNull { byId[it] }.distinct()
    // Shorts is always the first (leftmost) tab, whatever order was saved
    return (saved + AllTabs.filter { it !in saved }).sortedBy { it.id != "shorts" }
}

/** The tabs shown in the top bar. Never empty: if everything was turned off, the first tab stays. */
fun visibleTabs(store: Store): List<TabSpec> {
    val hidden = store.hiddenTabs
    return orderedTabs(store).filter { it.id !in hidden }.ifEmpty { listOf(AllTabs.first()) }
}

/** The visible tab [delta] steps after [from] (negative = before), or null at either end / when [from] is hidden. */
fun adjacentTab(store: Store, from: Route, delta: Int): Route? {
    val tabs = visibleTabs(store)
    val i = tabs.indexOfFirst { it.route == from }
    if (i < 0) return null
    val j = (i + delta).coerceIn(0, tabs.lastIndex)
    return if (j == i) null else tabs[j].route
}

/** The selected tab's index as an animated number; created once above the screens, see [TabTopBar]. */
val LocalTabPos = staticCompositionLocalOf<Animatable<Float, AnimationVector1D>> { error("TabPos not provided") }

/** How much wider than a normal cell the selected tab is (room for its name next to the icon). */
private const val SELECTED_WEIGHT = 2.6f

/** Height of the top bar (52dp search bar + 10dp above and below). */
val TabPillSpace = 72.dp

/**
 * Top of a tab screen: account photo (opens Settings), a small greeting over a big screen title, the screen's
 * own [actions] (refresh...) and round Search / Downloads buttons. The old floating search bar is only
 * used on the Shorts tab now, where the video fills the screen.
 */
@Composable
fun TabTopBar(
    title: String,
    brand: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val nav = LocalNav.current
    val thumb = Session.thumb
    Row(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (brand) "voidstream" else title,
            color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium,
            letterSpacing = (-0.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions?.invoke(this)
        HeaderButton(Icons.Rounded.Search, "Search", onClick = { nav.push(Route.Search()) })
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Card)
                .bouncyClickable(pressedScale = 0.9f, onClick = { if (nav.current != Route.Settings) nav.push(Route.Settings) }),
            contentAlignment = Alignment.Center
        ) {
            if (thumb.isNotEmpty()) AppImage(thumb, Modifier.fillMaxSize(), CircleShape)
            else Icon(Icons.Rounded.PersonOutline, contentDescription = "Account", tint = TextPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Card)
            .bouncyClickable(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = TextPrimary, modifier = Modifier.size(22.dp))
    }
}

/** Surface of the search bar and round buttons: light grey in light mode (so it shows on white), dark grey in dark mode. */
private val PillSurface: Color
    @Composable get() = Card

private val TopButtonSize = 52.dp

/** Round white button with an icon, used for the bell and settings in the header. */
@Composable
private fun RoundIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, dot: Boolean = false) {
    Box(
        Modifier
            .size(TopButtonSize)
            .shadow(6.dp, CircleShape, clip = false, ambientColor = Color(0x1A000000), spotColor = Color(0x26000000))
            .clip(CircleShape)
            .background(PillSurface)
            .bouncyClickable(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = TextPrimary, modifier = Modifier.size(26.dp))
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(13.dp).size(8.dp).clip(CircleShape).background(Accent))
    }
}

/** Top of the tab screens: search bar (opens the search page), bell (downloads) and the account photo. */
@Composable
fun TopBar(modifier: Modifier = Modifier, overVideo: Boolean = false) {
    val nav = LocalNav.current
    val thumb = Session.thumb
    val downloading = Downloads.items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }
    Row(
        modifier
            .fillMaxWidth()
            .background(
                if (overVideo) Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))
                else Brush.verticalGradient(listOf(Bg, Bg))
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = RoundedCornerShape(26.dp)
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(shape)
                .background(PillSurface)
                .bouncyClickable(pressedScale = 0.98f, onClick = { nav.push(Route.Search()) })
                .padding(start = 10.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 40.dp, height = 30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = OnAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text("Search", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = TextPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        RoundIconButton(Icons.Rounded.Download, "Downloads", onClick = { if (nav.current != Route.Downloads) nav.push(Route.Downloads) }, dot = downloading)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(TopButtonSize)
                .clip(CircleShape)
                .bouncyClickable(pressedScale = 0.9f, onClick = { if (nav.current != Route.Settings) nav.push(Route.Settings) }),
            contentAlignment = Alignment.Center
        ) {
            if (thumb.isNotEmpty()) AppImage(thumb, Modifier.fillMaxSize(), CircleShape)
            else Box(Modifier.fillMaxSize().background(PillSurface), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PersonOutline, contentDescription = "Account", tint = TextPrimary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** The YouTube Shorts glyph (Material "shorts" symbol, Apache 2.0) with the play triangle cut out, drawn in [color]. */
private val ShortsGlyph by lazy {
    PathParser().parsePathString(
        "M10 14.65v-5.3L15 12l-5 2.65zm7.77-4.33c-.77-.32-1.2-.5-1.2-.5L18 9.06c1.84-.96 2.53-3.23 1.56-5.06" +
        "s-3.24-2.53-5.07-1.56L6 8.02c-1.14.6-1.93 1.68-2.15 2.95-.22 1.27.2 2.55 1.14 3.46.55.54 1.2.89 1.9 1.05" +
        "l1.2.5L6 15.94c-1.84.96-2.53 3.23-1.56 5.06.97 1.83 3.24 2.53 5.07 1.56l8.5-4.5c1.13-.6 1.92-1.68 2.14-2.95" +
        ".23-1.27-.2-2.55-1.14-3.46-.55-.54-1.2-.89-1.9-1.05z"
    ).toPath().apply { fillType = PathFillType.EvenOdd }
}

@Composable
private fun ShortsIcon(color: () -> Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) { drawPath(ShortsGlyph, color()) }
    }
}

/**
 * Bottom dock: a floating rounded bar of icon buttons with ONE capsule (the open tab) that behaves like a water
 * drop: it stretches towards the tab it is travelling to, squashes a little, overshoots and wobbles into place.
 * Tap a tab, or hold the capsule and slide it: the tabs switch as it passes over them.
 * Tabs and their order still come from Settings.
 */
@Composable
fun BottomNavPill(position: () -> Float, shortsIndex: Int = -1, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val tabs = visibleTabs(Graph.store)
    val n = tabs.size
    if (n == 0) return
    val dark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val selected by remember(n) { androidx.compose.runtime.derivedStateOf { position().roundToInt().coerceIn(0, n - 1) } }
    val plain = TextMuted
    val shadowColor = Color.Black.copy(alpha = if (dark) 0.45f else 0.14f)
    // 0 = full dock .. 1 = slightly slimmer dock over a Short. Follows the pager so it shrinks / grows with the swipe.
    val s = if (shortsIndex < 0) 0f else (1f - abs(position() - shortsIndex)).coerceIn(0f, 1f)
    val shape = RoundedCornerShape((34f - 3f * s).dp)
    val itemH = (52f - 3f * s).dp

    // where the capsule is, in tab units (0 = first tab). While a finger moves it, dragPos is used instead.
    val blob = remember { Animatable(selected.toFloat()) }
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    var dragPos by remember { androidx.compose.runtime.mutableFloatStateOf(selected.toFloat()) }
    LaunchedEffect(selected, dragging) {
        if (!dragging) blob.animateTo(selected.toFloat(), spring(dampingRatio = 1f, stiffness = 420f))
    }

    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = (18f + 8f * s).dp, vertical = (12f - 4f * s).dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(shape)
            .background(NavBg)
            .padding((6f - 1f * s).dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(itemH)) {
            val density = LocalDensity.current
            val wPx = constraints.maxWidth.toFloat()
            val gap = with(density) { (4f - 1f * s).dp.toPx() }
            val p = if (dragging) dragPos else blob.value
            val ws = FloatArray(n) { 1f }
            val unit = (wPx - gap * (n - 1)) / ws.sum()
            val xs = FloatArray(n)
            var acc = 0f
            for (i in 0 until n) { xs[i] = acc; acc += ws[i] * unit + gap }

            // the drop: between the two tabs it is over, stretched towards where it is heading
            val i0 = floor(p).toInt().coerceIn(0, n - 1)
            val i1 = (i0 + 1).coerceAtMost(n - 1)
            val f = (p - i0).coerceIn(0f, 1f)
            var left = xs[i0] + (xs[i1] - xs[i0]) * f
            var dropW = ws[i0] * unit + (ws[i1] * unit - ws[i0] * unit) * f
            // past either end (overshoot) it just keeps going a little
            left += (p - p.coerceIn(0f, (n - 1).toFloat())) * unit
            val target = if (dragging) p else selected.toFloat()
            val d = target - p
            val stretch = 0f
            if (d > 0f) dropW += stretch else { left -= stretch; dropW += stretch }
            val squash = 1f

            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(left.roundToInt(), 0) }
                    .size(with(density) { dropW.toDp() }, itemH)
                    .graphicsLayer {
                        scaleY = squash * (if (dragging) 1.06f else 1f)
                        scaleX = if (dragging) 1.04f else 1f
                    }
                    .clip(RoundedCornerShape((26f - 2f * s).dp))
                    .background(Accent)
            )

            tabs.forEachIndexed { i, t ->
                val near = (1f - abs(p - i) * 1.5f).coerceIn(0f, 1f)
                val fg = lerp(plain, OnAccent, near)
                Box(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(xs[i].roundToInt(), 0) }
                        .size(with(density) { (ws[i] * unit).toDp() }, itemH),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        if (t.route == Route.Shorts) ShortsIcon({ fg }, Modifier.size((22f - 1f * s).dp))
                        else Icon(t.icon, contentDescription = t.label, tint = fg, modifier = Modifier.size((24f - 1f * s).dp))
                    }
                }
            }

            // touch layer: tap a tab, or grab the capsule and slide
            val layoutNow = rememberUpdatedState(Triple(xs, ws, unit))
            val gapNow = rememberUpdatedState(gap)
            val blobNow = rememberUpdatedState(blob.value)
            val selNow = rememberUpdatedState(selected)
            Box(
                Modifier.matchParentSize().pointerInput(n) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val x0 = down.position.x
                        val slop = viewConfiguration.touchSlop
                        var started = false
                        var pos0 = 0f
                        var lastRound = -1
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val dx = ch.position.x - x0
                            if (!started && abs(dx) > slop) {
                                started = true
                                pos0 = blobNow.value
                                dragPos = pos0
                                dragging = true
                                lastRound = pos0.roundToInt().coerceIn(0, n - 1)
                            }
                            if (started) {
                                ch.consume()
                                val np = (pos0 + dx / (size.width / n.toFloat())).coerceIn(-0.2f, n - 1 + 0.2f)
                                dragPos = np
                                val r = np.roundToInt().coerceIn(0, n - 1)
                                if (r != lastRound) {
                                    lastRound = r
                                    // only a soft tick while sliding; the screen changes once, when you let go (no lag)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                        if (started) {
                            val end = dragPos
                            val r = end.roundToInt().coerceIn(0, n - 1)
                            if (r != selNow.value) nav.selectTab(tabs[r].route)
                            scope.launch {
                                blob.snapTo(end)
                                dragging = false
                            }
                        } else {
                            // a tap: the tab under the finger
                            val (xsNow, wsNow, unitNow) = layoutNow.value
                            var hit = n - 1
                            for (i in 0 until n) {
                                if (x0 < xsNow[i] + wsNow[i] * unitNow + gapNow.value / 2f) { hit = i; break }
                            }
                            if (hit != selNow.value) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            nav.selectTab(tabs[hit].route)
                        }
                    }
                }
            )
        }
    }
}
