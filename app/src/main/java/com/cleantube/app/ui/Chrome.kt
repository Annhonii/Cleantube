package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Top bar of a pushed screen: back arrow + NType title. */
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
            fontFamily = NType,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/** Top bar of the four tab screens. The Home tab shows the dot-matrix brand. */
@Composable
fun TabTopBar(
    title: String,
    brand: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val nav = LocalNav.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(60.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = TextPrimary,
            fontFamily = if (brand) NDot else NType,
            fontWeight = if (brand) FontWeight.Normal else FontWeight.Bold,
            fontSize = if (brand) 28.sp else 26.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        actions()
        IconButton(onClick = { nav.push(Route.Settings) }) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextPrimary)
        }
    }
}

private data class TabSpec(val route: Route, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec(Route.Home, "Home", Icons.Rounded.Home),
    TabSpec(Route.Feed, "Feed", Icons.Rounded.Subscriptions),
    TabSpec(Route.Library, "Library", Icons.Rounded.VideoLibrary),
    TabSpec(Route.History, "History", Icons.Rounded.History)
)

/** Floating pill: the selected tab expands into a red chip with its label; plus a round search button. */
@Composable
fun FloatingNav(
    selected: Route,
    onSelect: (Route) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Card)
                .border(BorderStroke(1.dp, Hairline), CircleShape)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tabs.forEach { t ->
                val isSelected = t.route == selected
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Accent else Color.Transparent)
                        .clickable { onSelect(t.route) }
                        .padding(horizontal = 14.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        t.icon,
                        contentDescription = t.label,
                        tint = if (isSelected) Color.White else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    AnimatedVisibility(visible = isSelected) {
                        Text(
                            t.label,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Card)
                .border(BorderStroke(1.dp, Hairline), CircleShape)
                .clickable(onClick = onSearch),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = TextPrimary)
        }
    }
}
