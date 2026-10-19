package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.DlStatus
import com.cleantube.app.data.Downloads
import com.cleantube.app.data.Session
import kotlin.math.abs

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

private fun downloading(): Boolean =
    Downloads.items.any { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }

/**
 * Top bar of the tab screens: round Search and Downloads buttons on the left (the Downloads one gets a
 * red dot while something is downloading) and the account photo on the right, which opens Settings.
 * Screens other than Home also show their [title] under it. [actions] (refresh, clear...) go on a thin row below.
 */
@Composable
fun TabTopBar(
    title: String,
    brand: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val nav = LocalNav.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundButton(Icons.Rounded.Search, "Search", onClick = { nav.push(Route.Search()) })
            Spacer(Modifier.width(10.dp))
            RoundButton(
                Icons.Rounded.Download,
                "Downloads",
                dot = downloading(),
                onClick = { nav.selectTab(Route.Downloads) }
            )
            Spacer(Modifier.weight(1f))
            Avatar(onClick = { nav.push(Route.Settings) })
        }
        if (!brand) {
            Text(
                title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 4.dp)
            )
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

/** 44dp round button on a soft tile, optional red dot in the corner. */
@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit, dot: Boolean = false) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(22.dp))
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Accent)
            )
        }
    }
}

/** The signed-in account's photo (or a person icon when signed out). Opens Settings. */
@Composable
private fun Avatar(onClick: () -> Unit) {
    val thumb = Session.thumb
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(AccentSoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (thumb.isNotEmpty()) {
            AppImage(thumb, Modifier.size(44.dp), CircleShape)
        } else {
            Icon(Icons.Rounded.Person, contentDescription = "Settings", tint = OnAccentSoft, modifier = Modifier.size(24.dp))
        }
    }
}

private data class TabSpec(val route: Route, val label: String, val icon: ImageVector)

/** Always visible in the bottom pill. */
private val mainTabs = listOf(
    TabSpec(Route.Home, "Home", Icons.Rounded.Home),
    TabSpec(Route.Shorts, "Shorts", Icons.Rounded.LocalFireDepartment),
    TabSpec(Route.Feed, "Feed", Icons.Rounded.Subscriptions),
    TabSpec(Route.Library, "Library", Icons.Rounded.VideoLibrary)
)

/** Behind the grid button at the end of the pill. */
private val moreTabs = listOf(
    TabSpec(Route.Downloads, "Downloads", Icons.Rounded.Download),
    TabSpec(Route.History, "History", Icons.Rounded.History)
)

/**
 * Floating black pill at the bottom: the selected tab is a light chip with its label, the grid button at the
 * end opens the tabs that don't fit (Downloads, History). Search is the round button in the top bar.
 */
@Composable
fun FloatingNav(
    selected: Route,
    onSelect: (Route) -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    val inMore = moreTabs.firstOrNull { it.route == selected }

    // Drag the pill (the light chip) left / right: it steps through the tabs, one per ~44dp, with a tick.
    val stepPx = with(LocalDensity.current) { 44.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val currentTab = rememberUpdatedState(selected)
    val select = rememberUpdatedState(onSelect)

    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(NavBg)
            .pointerInput(Unit) {
                var acc = 0f
                var cursor: Route = currentTab.value
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f; cursor = currentTab.value },
                    onDragCancel = { acc = 0f },
                    onDragEnd = { acc = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        acc += amount
                        // finger to the right = the chip moves right = next tab
                        while (abs(acc) >= stepPx) {
                            val dir = if (acc > 0) 1 else -1
                            acc -= dir * stepPx
                            val next = tabAfter(cursor, dir)
                            if (next == null) {
                                acc = 0f
                                break
                            }
                            cursor = next
                            select.value(next)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                )
            }
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        mainTabs.forEach { t ->
            NavItem(t.icon, t.label, selected = t.route == selected, onClick = { onSelect(t.route) })
        }
        Box {
            // When Downloads / History is open, the grid button shows it as the selected chip.
            NavItem(
                icon = inMore?.icon ?: Icons.Rounded.Apps,
                label = inMore?.label ?: "More",
                selected = inMore != null,
                dot = inMore == null && downloading(),
                onClick = { menu = true }
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                moreTabs.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.label) },
                        leadingIcon = { Icon(t.icon, contentDescription = null) },
                        onClick = { menu = false; onSelect(t.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    dot: Boolean = false
) {
    Row(
        modifier = Modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(if (selected) OnNavBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 14.dp else 12.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) NavBg else OnNavBg,
                modifier = Modifier.size(22.dp)
            )
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
        AnimatedVisibility(visible = selected) {
            Text(
                label,
                color = NavBg,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
