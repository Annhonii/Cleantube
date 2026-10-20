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
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
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
    TabSpec("history", Route.History, "History", Icons.Rounded.History),
    TabSpec("downloads", Route.Downloads, "Downloads", Icons.Rounded.Download),
    TabSpec("trending", Route.Trending, "Trending", Icons.Rounded.TrendingUp),
    TabSpec("library", Route.Library, "Library", Icons.Rounded.VideoLibrary)
)

/** All tabs in the user's order, hidden ones included (tabs the saved order doesn't know yet go to the end). */
fun orderedTabs(store: Store): List<TabSpec> {
    val byId = AllTabs.associateBy { it.id }
    val saved = store.tabOrder.mapNotNull { byId[it] }.distinct()
    return saved + AllTabs.filter { it !in saved }
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
fun TopBar(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val thumb = Session.thumb
    val downloading = Downloads.items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }
    Row(
        modifier
            .fillMaxWidth()
            .background(Bg)
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
        RoundIconButton(Icons.Rounded.Notifications, "Downloads", onClick = { nav.selectTab(Route.Downloads) }, dot = downloading)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(TopButtonSize)
                .clip(CircleShape)
                .bouncyClickable(pressedScale = 0.9f, onClick = { nav.push(if (Session.loggedIn) Route.Settings else Route.Login) }),
            contentAlignment = Alignment.Center
        ) {
            if (thumb.isNotEmpty()) AppImage(thumb, Modifier.fillMaxSize(), CircleShape)
            else Box(Modifier.fillMaxSize().background(PillSurface), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PersonOutline, contentDescription = "Account", tint = TextPrimary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** Shorts-style icon: a tall rounded clip with a play triangle cut out of it, drawn in [color] on [bg]. */
@Composable
private fun ShortsIcon(color: Color, bg: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rw = w * 0.52f
        val rh = w * 0.86f
        drawRoundRect(
            color,
            topLeft = Offset(cx - rw / 2f, cy - rh / 2f),
            size = Size(rw, rh),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(rw * 0.42f)
        )
        val tri = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - w * 0.09f, cy - w * 0.15f)
            lineTo(cx - w * 0.09f, cy + w * 0.15f)
            lineTo(cx + w * 0.15f, cy)
            close()
        }
        drawPath(tri, bg)
    }
}

private val ShortsCell = 44.dp

/** Width of every cell of the pill in px: the Shorts button is fixed, the text tabs share the rest by label length. */
private fun cellWidths(tabs: List<TabSpec>, totalPx: Float, shortsPx: Float): FloatArray {
    val shorts = tabs.count { it.route == Route.Shorts }
    val units = tabs.filter { it.route != Route.Shorts }.sumOf { it.label.length + 4 }.toFloat().coerceAtLeast(1f)
    val rest = (totalPx - shortsPx * shorts).coerceAtLeast(0f)
    return FloatArray(tabs.size) { i ->
        if (tabs[i].route == Route.Shorts) shortsPx else rest * (tabs[i].label.length + 4) / units
    }
}

/**
 * Floating pill at the bottom, as wide as the phone, dark in dark mode and light in light mode. The visible tabs sit
 * in it in the order chosen in Settings: Shorts is the round button with the Shorts icon, the others are text.
 * The chip marks the open tab. [position] comes straight from the pager (index + fraction), so the chip slides
 * along while you swipe a page; dragging the chip itself changes tabs too.
 */
@Composable
fun BottomNavPill(position: () -> Float, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val tabs = visibleTabs(Graph.store)
    val n = tabs.size
    val downloading = Downloads.items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }
    val dark = isSystemInDarkTheme()
    val surface = if (dark) Color(0xFF1E1E1E) else Color(0xFFEFEFEF)
    val chip = if (dark) Color.White else Color(0xFF111111)
    val onChip = if (dark) Color(0xFF111111) else Color.White
    val plain = if (dark) Color.White else Color(0xFF111111)
    val pos = position()
    val shape = RoundedCornerShape(30.dp)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val navState = rememberUpdatedState(nav)
    val tabsState = rememberUpdatedState(tabs)
    val textTabs = tabs.count { it.route != Route.Shorts }
    val fontSize = if (textTabs > 4) 11.5.sp else 13.5.sp

    BoxWithConstraints(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .background(surface)
            .border(1.dp, if (dark) Color.Transparent else Hairline, shape)
    ) {
        val inner: Dp = maxWidth - 8.dp
        val widths = cellWidths(tabs, with(density) { inner.toPx() }, with(density) { ShortsCell.toPx() })

        Box(
            Modifier
                .padding(4.dp)
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(Unit) {
                    var cursor = -1
                    detectHorizontalDragGestures(
                        onDragStart = { cursor = -1 },
                        onDragCancel = { cursor = -1 },
                        onDragEnd = { cursor = -1 },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val list = tabsState.value
                            val w = cellWidths(list, size.width.toFloat(), ShortsCell.toPx())
                            var acc = 0f
                            var idx = w.lastIndex
                            for (i in w.indices) {
                                acc += w[i]
                                if (change.position.x < acc) { idx = i; break }
                            }
                            // Shorts hides the pill, so a drag never lands on it
                            if (list[idx].route == Route.Shorts) idx = list.indexOfFirst { it.route != Route.Shorts }
                            if (idx < 0 || idx == cursor) return@detectHorizontalDragGestures
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
            // Shorts button
            var x = 0f
            tabs.forEachIndexed { i, t ->
                if (t.route == Route.Shorts) {
                    Box(
                        Modifier
                            .offset(x = with(density) { x.toDp() })
                            .size(ShortsCell)
                            .clip(CircleShape)
                            .background(chip)
                            .bouncyClickable(pressedScale = 0.9f, onClick = { nav.selectTab(t.route) }),
                        contentAlignment = Alignment.Center
                    ) {
                        ShortsIcon(onChip, chip, Modifier.size(26.dp))
                    }
                }
                x += widths[i]
            }

            // the chip: between two cells its start and width blend the way the pager does
            if (n > 0 && pos > -0.5f) {
                val i0 = floor(pos).toInt().coerceIn(0, n - 1)
                val i1 = (i0 + 1).coerceAtMost(n - 1)
                val f = (pos - i0).coerceIn(0f, 1f)
                val s0 = widths.take(i0).sum()
                val s1 = widths.take(i1).sum()
                val startPx = s0 + (s1 - s0) * f
                val widthPx = widths[i0] + (widths[i1] - widths[i0]) * f
                Box(
                    Modifier
                        .offset(x = with(density) { startPx.toDp() })
                        .width(with(density) { widthPx.toDp() })
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(chip)
                )
            }

            // text tabs
            x = 0f
            tabs.forEachIndexed { i, t ->
                if (t.route != Route.Shorts) {
                    val bump = (1f - abs(pos - i)).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .offset(x = with(density) { x.toDp() })
                            .width(with(density) { widths[i].toDp() })
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .bouncyClickable(pressedScale = 0.94f, onClick = { nav.selectTab(t.route) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t.label,
                            color = lerp(plain, onChip, bump),
                            fontSize = fontSize,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                        if (t.route == Route.Downloads && downloading && bump < 0.5f) {
                            Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp).size(7.dp).clip(CircleShape).background(Accent))
                        }
                    }
                }
                x += widths[i]
            }
        }
    }
}
