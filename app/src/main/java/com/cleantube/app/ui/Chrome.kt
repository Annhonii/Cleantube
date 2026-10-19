package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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

/**
 * Top bar of the tab screens. The visible tabs and the account photo share the whole width equally,
 * whether there are 3 tabs or 6. The selected tab is a tinted chip with its name under the icon.
 * [actions] (refresh, clear...) go on a thin row below.
 * [title] and [brand] are kept for the callers; the selected tab already says where you are.
 */
@Composable
fun TabTopBar(
    title: String,
    brand: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val nav = LocalNav.current
    val selected = nav.current
    val tabs = visibleTabs(Graph.store)
    val downloading = Downloads.items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { t ->
                TabCell(
                    t,
                    selected = t.route == selected,
                    dot = t.route == Route.Downloads && downloading,
                    onClick = { nav.selectTab(t.route) },
                    modifier = Modifier.weight(1f)
                )
            }
            ProfileCell(onClick = { nav.push(Route.Settings) }, modifier = Modifier.weight(1f))
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

@Composable
private fun TabCell(t: TabSpec, selected: Boolean, dot: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(56.dp)
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AccentSoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.animateContentSize()
        ) {
            Box {
                Icon(
                    t.icon,
                    contentDescription = t.label,
                    tint = if (selected) OnAccentSoft else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
                // dot on the Downloads tab while something is downloading
                if (dot && !selected) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Accent)
                    )
                }
            }
            if (selected) {
                Text(
                    t.label,
                    color = OnAccentSoft,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp)
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
            .height(56.dp)
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (thumb.isNotEmpty()) {
            AppImage(thumb, Modifier.size(32.dp), CircleShape)
        } else {
            Icon(Icons.Rounded.PersonOutline, contentDescription = "Settings", tint = TextMuted, modifier = Modifier.size(26.dp))
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
