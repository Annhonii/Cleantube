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
                .size(46.dp)
                .clip(CircleShape)
                .bouncyClickable(pressedScale = 0.9f, onClick = { nav.push(if (Session.loggedIn) Route.Settings else Route.Login) }),
            contentAlignment = Alignment.Center
        ) {
            if (thumb.isNotEmpty()) AppImage(thumb, Modifier.fillMaxSize(), CircleShape)
            else Box(Modifier.fillMaxSize().background(PillSurface), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PersonOutline, contentDescription = "Account", tint = TextMuted, modifier = Modifier.size(26.dp))
            }
        }
    }
}

private val NavSurface = Color(0xFF151A26)
private val NavChip = Color(0xFFD9D9D9)

/** Floating dark pill at the bottom: Home, Search, Bell (downloads), Profile. The open one gets a light chip with its name. */
@Composable
fun BottomNavPill(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val downloading = Downloads.items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }
    val cur = nav.current
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .background(NavSurface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(Icons.Rounded.Home, "Home", selected = cur == Route.Home, modifier = Modifier.weight(if (cur == Route.Home) 2.2f else 1f)) {
            nav.selectTab(Route.Home)
        }
        NavItem(Icons.Rounded.Search, "Search", selected = false, modifier = Modifier.weight(1f)) {
            nav.push(Route.Search())
        }
        NavItem(Icons.Rounded.Notifications, "Downloads", selected = cur == Route.Downloads, dot = downloading, modifier = Modifier.weight(if (cur == Route.Downloads) 2.6f else 1f)) {
            nav.selectTab(Route.Downloads)
        }
        NavItem(Icons.Rounded.PersonOutline, "Profile", selected = false, modifier = Modifier.weight(1f)) {
            nav.push(if (Session.loggedIn) Route.Settings else Route.Login)
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(56.dp)
            .clip(CircleShape)
            .background(if (selected) NavChip else Color.Transparent)
            .bouncyClickable(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Icon(icon, contentDescription = label, tint = if (selected) Color(0xFF1B1B1B) else Color(0xFFEDEDED), modifier = Modifier.size(26.dp))
                if (dot && !selected) {
                    Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(Accent))
                }
            }
            if (selected) {
                Text(
                    label,
                    color = Color(0xFF1B1B1B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
