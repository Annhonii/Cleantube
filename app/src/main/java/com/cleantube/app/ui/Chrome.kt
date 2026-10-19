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

private data class TabSpec(val route: Route, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec(Route.Home, "Home", Icons.Rounded.Home),
    TabSpec(Route.Shorts, "Shorts", Icons.Rounded.LocalFireDepartment),
    TabSpec(Route.Feed, "Feed", Icons.Rounded.Subscriptions),
    TabSpec(Route.Library, "Library", Icons.Rounded.VideoLibrary),
    TabSpec(Route.Downloads, "Downloads", Icons.Rounded.Download),
    TabSpec(Route.History, "History", Icons.Rounded.History)
)

/**
 * Top bar of the tab screens: the tabs as icons (the selected one grows into a tinted chip with its
 * label) and the profile / settings icon at the end. [actions] (refresh, clear...) go on a thin row below.
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { t ->
                TabButton(t, selected = t.route == selected, onClick = { nav.selectTab(t.route) })
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { nav.push(Route.Settings) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.PersonOutline, contentDescription = "Settings", tint = TextMuted)
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

@Composable
private fun TabButton(t: TabSpec, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 12.dp else 7.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Icon(
                t.icon,
                contentDescription = t.label,
                tint = if (selected) OnAccentSoft else TextMuted,
                modifier = Modifier.size(22.dp)
            )
            // dot on the Downloads tab while something is downloading
            if (t.route == Route.Downloads && !selected &&
                Downloads.items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Accent)
                )
            }
        }
        AnimatedVisibility(visible = selected) {
            Text(
                t.label,
                color = OnAccentSoft,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
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
