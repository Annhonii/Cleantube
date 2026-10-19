package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.PersonOutline
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

/**
 * Top bar of the tab screens: a black pill with the visible tabs and the account photo. The selected tab is
 * a light chip with its name next to the icon; all the other cells share the rest of the width equally, for
 * 3 tabs or 6. The chip is one shape that slides and resizes with a spring when the tab changes (tap,
 * swipe on the screen, or dragging the pill). [actions] (refresh, clear...) go on a thin row below.
 * [title] and [brand] are kept for the callers; the selected tab already says where you are.
 */
@Composable
fun TabTopBar(
    title: String,
    brand: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val nav = LocalNav.current
    val store = Graph.store
    val tabs = visibleTabs(store)
    val n = tabs.size
    val selIndex = tabs.indexOfFirst { it.route == nav.current }
    val downloading = Downloads.items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }

    // pos = the selected tab's index as a number that glides from one tab to the next. Every size and the
    // chip are computed from it, so everything moves together. -1 = no tab of the bar is open.
    // It lives above the screens (every tab screen has its own bar), so the glide carries on across the switch.
    val posAnim = LocalTabPos.current
    LaunchedEffect(selIndex) {
        posAnim.animateTo(selIndex.toFloat(), spring(dampingRatio = 0.9f, stiffness = 1100f))
    }
    val pos by posAnim.asState()
    // 1 right on the cell that pos points at, falling to 0 one cell away
    fun bump(i: Int): Float = (1f - abs(pos - i)).coerceIn(0f, 1f)
    val weights = List(n) { i -> 1f + (SELECTED_WEIGHT - 1f) * bump(i) }
    val total = weights.sum() + 1f // + the profile cell

    // dragging the pill: one tab per ~44dp, the chip follows the finger
    val stepPx = with(LocalDensity.current) { 44.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val navState = rememberUpdatedState(nav)

    Column(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .statusBarsPadding()
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(58.dp)
                .clip(CircleShape)
                .background(NavBg)
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

            // the sliding chip: between the two cells pos is between, its start and width blend the same way
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
                        .background(OnNavBg)
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
                ProfileCell(onClick = { nav.push(Route.Settings) }, modifier = Modifier.weight(1f))
            }
        }
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

/** One tab of the pill. [bump] is 1 when it is the selected one (name shown, dark icon on the light chip) and 0 otherwise. */
@Composable
private fun PillCell(t: TabSpec, bump: Float, dot: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Icon(
                    t.icon,
                    contentDescription = t.label,
                    tint = lerp(OnNavBg, NavBg, bump),
                    modifier = Modifier.size(22.dp)
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
                    color = NavBg,
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

/** The last cell: the Google account's photo (a person icon when signed out). Opens Settings. */
@Composable
private fun ProfileCell(onClick: () -> Unit, modifier: Modifier) {
    val thumb = Session.thumb
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (thumb.isNotEmpty()) {
            AppImage(thumb, Modifier.size(32.dp), CircleShape)
        } else {
            Icon(Icons.Rounded.PersonOutline, contentDescription = "Settings", tint = OnNavBg, modifier = Modifier.size(24.dp))
        }
    }
}

/** Floating search bar at the bottom of the tab screens. Tapping it opens the search page. */
@Composable
fun FloatingSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(8.dp, shape)
            .clip(shape)
            .background(Card)
            .clickable(onClick = onClick)
            .height(56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // small rounded "play" badge, like the logo in the reference design
        Box(
            Modifier
                .size(width = 34.dp, height = 24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = OnAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            "Search",
            color = TextMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.Search, contentDescription = "Search", tint = TextPrimary)
    }
}
