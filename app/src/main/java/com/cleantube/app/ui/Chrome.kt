package com.cleantube.app.ui

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
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
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
    TabSpec("feed", Route.Feed, "Subscription", Icons.Rounded.Subscriptions),
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
 * Top of a tab screen: an empty strip the size of the pill (the pill itself is drawn once, above the pager,
 * see [TabPill], so it stays put while the pages slide under it) and, below it, the screen's [actions]
 * (refresh, clear...). [title] and [brand] are kept for the callers; the selected tab already says where you are.
 */
@Composable
fun TabTopBar(
    title: String,
    brand: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .statusBarsPadding()
    ) {
        Spacer(Modifier.height(TabPillSpace))
        if (actions != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}

/** Surface of the search bar and round buttons: light grey in light mode (so it shows on white), dark grey in dark mode. */
private val PillSurface: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1C1C1C) else Color(0xFFEFEFEF)

private val TopButtonSize = 52.dp

/** Round white button with an icon, used for the bell and settings in the header. */
@Composable
private fun RoundIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, dot: Boolean = false) {
    Box(
        Modifier
            .size(TopButtonSize)
            .shadow(4.dp, CircleShape, clip = false)
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
                .border(1.dp, Hairline, shape)
                .bouncyClickable(pressedScale = 0.98f, onClick = { nav.push(Route.Search()) })
                .padding(start = 10.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 40.dp, height = 30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
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

private val NavHeight = 46.dp

/**
 * Width of every cell of the pill in px. All text tabs get exactly the same width (so the white chip is the same
 * size on every tab and the gaps between tabs are equal, whether there are 3, 4 or 5 of them); Shorts has no cell
 * (it is the round button).
 */
private fun cellWidths(tabs: List<TabSpec>, totalPx: Float): FloatArray {
    val textCount = tabs.count { it.route != Route.Shorts }.coerceAtLeast(1)
    val each = totalPx / textCount
    return FloatArray(tabs.size) { i -> if (tabs[i].route == Route.Shorts) 0f else each }
}

/** The labels of the pill, all in one colour. Drawn twice: plain on the pill, and inverted inside the chip. */
@Composable
private fun PillLabels(
    tabs: List<TabSpec>,
    widths: FloatArray,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    dotOn: Boolean,
    onTab: ((Route) -> Unit)?
) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        var x = 0f
        tabs.forEachIndexed { i, t ->
            if (t.route != Route.Shorts) {
                Box(
                    Modifier
                        .offset(x = with(density) { x.toDp() })
                        .width(with(density) { widths[i].toDp() })
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .then(if (onTab != null) Modifier.bouncyClickable(pressedScale = 0.94f, onClick = { onTab(t.route) }) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        t.label,
                        color = color,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                    if (dotOn && t.route == Route.Downloads) {
                        Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 10.dp).size(7.dp).clip(CircleShape).background(Accent))
                    }
                }
            }
            x += widths[i]
        }
    }
}

/**
 * Bottom bar: a round Shorts button on the left and, next to it, a floating pill with the other visible tabs
 * (Home, Subscription, Notification, Downloads by default) in the order chosen in Settings. Dark in dark mode, light
 * in light mode. The chip marks the open tab; [position] comes straight from the pager (index + fraction), so it
 * slides along while you swipe a page, and dragging the chip itself changes tabs too.
 *
 * Nothing here recomposes while the chip moves: [position] is only read in layout / draw, and the label colour
 * flips by drawing a second, inverted copy of the labels clipped to the chip.
 */
@Composable
fun BottomNavPill(position: () -> Float, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val tabs = visibleTabs(Graph.store)
    val n = tabs.size
    val downloading = Downloads.items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }
    val dark = isSystemInDarkTheme()
    val surface = if (dark) Color(0xFF1C1C1E) else Color(0xFFF4F4F5)
    val chip = if (dark) Color.White else Color(0xFF111111)
    val onChip = if (dark) Color(0xFF111111) else Color.White
    val plain = if (dark) Color(0xFFEDEDED) else Color(0xFF3A3A3C)
    val rim = if (dark) Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0DFFFFFF)))
              else Brush.verticalGradient(listOf(Color(0x1A000000), Color(0x0F000000)))
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val navState = rememberUpdatedState(nav)
    val tabsState = rememberUpdatedState(tabs)
    val textTabs = tabs.count { it.route != Route.Shorts }
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = LocalTextStyle.current
    val shortsIndex = tabs.indexOfFirst { it.route == Route.Shorts }
    val shadowColor = Color.Black.copy(alpha = if (dark) 0.55f else 0.22f)

    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // round Shorts button, outside the pill
        if (shortsIndex >= 0) {
            val bump: () -> Float = { (1f - abs(position() - shortsIndex)).coerceIn(0f, 1f) }
            Box(
                Modifier
                    .size(NavHeight)
                    .shadow(18.dp, CircleShape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
                    .clip(CircleShape)
                    .drawBehind { drawRect(lerp(surface, chip, bump())) }
                    .border(1.dp, rim, CircleShape)
                    .bouncyClickable(pressedScale = 0.88f, onClick = { nav.selectTab(Route.Shorts) }),
                contentAlignment = Alignment.Center
            ) {
                ShortsIcon({ lerp(plain, onChip, bump()) }, Modifier.size(22.dp))
            }
        }

        val shape = RoundedCornerShape(percent = 50)
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .shadow(18.dp, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
                .clip(shape)
                .background(surface)
                .border(1.dp, rim, shape)
        ) {
            val innerPx = with(density) { (maxWidth - 6.dp).toPx() }
            val widths = cellWidths(tabs, innerPx)

            // Cells are equal, so shrink the label size (never below 9sp) until the longest label fits its cell.
            val cellDp = if (textTabs > 0) (maxWidth - 6.dp) / textTabs else maxWidth
            val fontSize = remember(tabs, cellDp, baseStyle) {
                var size = 12.5f
                while (size > 9f) {
                    val widest = tabs.filter { it.route != Route.Shorts }.maxOfOrNull {
                        textMeasurer.measure(
                            it.label,
                            baseStyle.copy(fontSize = size.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
                            softWrap = false
                        ).size.width
                    } ?: 0
                    if (with(density) { widest.toDp() } <= cellDp - 10.dp) break
                    size -= 0.5f
                }
                size.sp
            }

            // where the chip is (start, width in px), from the pager position; read only in layout
            fun chipSpan(): Pair<Float, Float> {
                val pos = position()
                if (n == 0 || textTabs == 0 || pos < -0.5f) return 0f to 0f
                val i0 = floor(pos).toInt().coerceIn(0, n - 1)
                val i1 = (i0 + 1).coerceAtMost(n - 1)
                val f = (pos - i0).coerceIn(0f, 1f)
                val s0 = widths.take(i0).sum()
                val s1 = widths.take(i1).sum()
                // "Liquid" chip: the edge it moves towards runs ahead, the other edge follows, so it
                // stretches while travelling and settles back to the tab's width.
                val lead = 1f - (1f - f).pow(1.5f)
                val trail = f.pow(1.5f)
                val start = s0 + (s1 - s0) * trail
                val end = (s0 + widths[i0]) + ((s1 + widths[i1]) - (s0 + widths[i0])) * lead
                return start to (end - start).coerceAtLeast(0f)
            }

            Box(
                Modifier
                    .padding(3.dp)
                    .fillMaxWidth()
                    .height(NavHeight - 6.dp)
                    .pointerInput(Unit) {
                        var cursor = -1
                        detectHorizontalDragGestures(
                            onDragStart = { cursor = -1 },
                            onDragCancel = { cursor = -1 },
                            onDragEnd = { cursor = -1 },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val list = tabsState.value
                                val w = cellWidths(list, size.width.toFloat())
                                var acc = 0f
                                var idx = w.lastIndex
                                for (i in w.indices) {
                                    acc += w[i]
                                    if (w[i] > 0f && change.position.x < acc) { idx = i; break }
                                }
                                if (list[idx].route == Route.Shorts) return@detectHorizontalDragGestures
                                if (idx == cursor) return@detectHorizontalDragGestures
                                cursor = idx
                                val r = list[idx].route
                                if (navState.value.current != r) {
                                    navState.value.selectTab(r)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        )
                    }
            ) {
                // labels on the pill
                PillLabels(tabs, widths, plain, fontSize, downloading) { nav.selectTab(it) }

                // the chip, carrying its own inverted copy of the labels (clipped to the chip, shifted back so they line up)
                Box(
                    Modifier
                        .fillMaxSize()
                        .layout { measurable, c ->
                            val (start, w) = chipSpan()
                            val p = measurable.measure(Constraints.fixed(w.roundToInt().coerceAtLeast(0), c.maxHeight))
                            layout(c.maxWidth, c.maxHeight) { p.place(start.roundToInt(), 0) }
                        }
                        .shadow(6.dp, CircleShape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
                        .clip(CircleShape)
                        .background(chip)
                ) {
                    Box(
                        Modifier.layout { measurable, c ->
                            val (start, _) = chipSpan()
                            val p = measurable.measure(Constraints.fixed(innerPx.roundToInt(), c.maxHeight))
                            layout(c.maxWidth, c.maxHeight) { p.place(-start.roundToInt(), 0) }
                        }
                    ) {
                        PillLabels(tabs, widths, onChip, fontSize, false, null)
                    }
                }
            }
        }
    }
}
