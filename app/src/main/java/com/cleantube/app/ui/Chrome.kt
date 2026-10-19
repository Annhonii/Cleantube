package com.cleantube.app.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
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
    TabSpec("home", Route.Home, "Home", Icons.Rounded.Home),
    TabSpec("shorts", Route.Shorts, "Shorts", Icons.Rounded.LocalFireDepartment),
    TabSpec("feed", Route.Feed, "Feed", Icons.Rounded.Subscriptions),
    TabSpec("library", Route.Library, "Library", Icons.Rounded.VideoLibrary),
    TabSpec("downloads", Route.Downloads, "Downloads", Icons.Rounded.Download),
    TabSpec("history", Route.History, "History", Icons.Rounded.History)
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

/** Height the pill row takes at the top of a tab screen (58dp pill + 8dp above and below). */
val TabPillSpace = 138.dp

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

/**
 * The black pill with the visible tabs and the account photo. [position] is where the selected chip is, as a
 * tab index with a fraction (1.4 = 40% of the way from tab 1 to tab 2): it comes straight from the pager,
 * so the chip slides, stretches and shrinks together with the finger while you swipe the page,
 * and glides with the pager's spring after a tap. -1 = no tab of the bar is open.
 * Dragging the pill itself also changes tabs (one tab per ~44dp).
 */
@Composable
fun TabPill(position: () -> Float, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val store = Graph.store
    val tabs = visibleTabs(store)
    val n = tabs.size
    val downloading = Downloads.items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }

    val pos = position()
    // 1 right on the cell that pos points at, falling to 0 one cell away
    fun bump(i: Int): Float = (1f - abs(pos - i)).coerceIn(0f, 1f)
    val weights = List(n) { i -> 1f + (SELECTED_WEIGHT - 1f) * bump(i) }
    val total = weights.sum()

    val stepPx = with(LocalDensity.current) { 44.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val navState = rememberUpdatedState(nav)

    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        AppHeader()
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(58.dp)
                .shadow(6.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(PillSurface)
                .pointerInput(Unit) {
                    var acc = 0f
                    var cursor: Route = navState.value.current
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f; cursor = navState.value.current },
                        onDragCancel = { acc = 0f },
                        onDragEnd = { acc = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            acc += amount
                            // finger to the right = the chip moves right = next tab
                            while (abs(acc) >= stepPx) {
                                val dir = if (acc > 0) 1 else -1
                                acc -= dir * stepPx
                                val next = adjacentTab(Graph.store, cursor, dir)
                                if (next == null) {
                                    acc = 0f
                                    break
                                }
                                cursor = next
                                navState.value.selectTab(next)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                }
                .padding(5.dp)
        ) {
            val inner = maxWidth

            // the sliding chip: between two cells its start and width blend the same way as the cells do
            if (n > 0 && pos > -0.5f) {
                val i0 = floor(pos).toInt().coerceIn(0, n - 1)
                val i1 = (i0 + 1).coerceAtMost(n - 1)
                val f = (pos - i0).coerceIn(0f, 1f)
                val start0 = weights.take(i0).sum()
                val start1 = weights.take(i1).sum()
                val chipStart = inner * (((start0 + (start1 - start0) * f)) / total)
                val chipWidth = inner * ((weights[i0] + (weights[i1] - weights[i0]) * f) / total)
                Box(
                    Modifier
                        .offset(x = chipStart)
                        .width(chipWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(AccentSoft)
                )
            }

            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, t ->
                    PillCell(
                        t,
                        bump = bump(i),
                        dot = t.route == Route.Downloads && downloading,
                        onClick = { nav.selectTab(t.route) },
                        modifier = Modifier.weight(weights[i])
                    )
                }
            }
        }
    }
}

/** One tab of the pill. [bump] is 1 when it is the selected one (name shown, dark icon on the light chip) and 0 otherwise. */
@Composable
private fun PillCell(t: TabSpec, bump: Float, dot: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .bouncyClickable(pressedScale = 0.86f, onClick = onClick)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Icon(
                    t.icon,
                    contentDescription = t.label,
                    tint = lerp(TextPrimary, OnAccentSoft, bump),
                    modifier = Modifier
                        .size(22.dp)
                        // the selected tab's icon swells a little as the chip arrives
                        .graphicsLayer {
                            val s = 1f + 0.16f * bump
                            scaleX = s
                            scaleY = s
                        }
                )
                // dot on the Downloads tab while something is downloading
                if (dot && bump < 0.5f) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Accent)
                    )
                }
            }
            if (bump > 0.05f) {
                Text(
                    t.label,
                    color = OnAccentSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.alpha(bump * bump).padding(start = 6.dp)
                )
            }
        }
    }
}

/** Surface of the tab pill and the search bar: white in light mode, dark grey in dark mode. */
private val PillSurface: Color get() = if (Bg.luminance() > 0.5f) Color.White else Color(0xFF1C1C1C)

/** Round white button with an icon, used for the bell and settings in the header. */
@Composable
private fun RoundIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, dot: Boolean = false) {
    Box(
        Modifier
            .size(46.dp)
            .shadow(4.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(PillSurface)
            .bouncyClickable(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = TextMuted, modifier = Modifier.size(22.dp))
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(11.dp).size(8.dp).clip(CircleShape).background(Accent))
    }
}

/** Top row: account photo + name on the left, bell (downloads) and settings on the right. */
@Composable
fun AppHeader(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val thumb = Session.thumb
    val name = Session.name.ifEmpty { "owner" }
    val downloading = Downloads.items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }
    Row(
        modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .bouncyClickable(pressedScale = 0.9f, onClick = { nav.push(if (Session.loggedIn) Route.Settings else Route.Login) }),
            contentAlignment = Alignment.Center
        ) {
            if (thumb.isNotEmpty()) AppImage(thumb, Modifier.fillMaxSize(), CircleShape)
            else Box(Modifier.fillMaxSize().background(Card), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PersonOutline, contentDescription = "Account", tint = TextMuted, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            name,
            color = TextPrimary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        RoundIconButton(Icons.Rounded.Notifications, "Downloads", onClick = { nav.selectTab(Route.Downloads) }, dot = downloading)
        Spacer(Modifier.width(10.dp))
        RoundIconButton(Icons.Rounded.Settings, "Settings", onClick = { nav.push(Route.Settings) })
    }
}

/** Floating search bar at the bottom of the tab screens. Tapping it opens the search page. */
@Composable
fun FloatingSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .fillMaxWidth()
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .background(PillSurface.copy(alpha = 0.94f))
            .height(58.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // black rounded "play" badge, like the logo in the reference design
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
        Text(
            "Search",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.Search, contentDescription = "Search", tint = TextPrimary)
    }
}
