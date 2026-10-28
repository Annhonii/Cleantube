package com.cleantube.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.Session

/** Height of the tab row under the status bar (screens start below it, see [collapsingHeaderHeight]). */
val TopTabsHeight = 64.dp

/**
 * Tabs along the top: the open tab is a soft pill with its icon and name, the others are plain icons, and the last
 * round button is the account (opens Settings). Over a Short it is see-through with white icons.
 */
@Composable
fun TopTabBar(nav: Nav, tabs: List<TabSpec>, modifier: Modifier = Modifier) {
    val onShorts = nav.current == Route.Shorts
    val idle = if (onShorts) Color(0xB3FFFFFF) else TextMuted
    val sel = if (onShorts) Color.White else Accent
    val pill = if (onShorts) Color(0x33FFFFFF) else Accent.copy(alpha = 0.14f)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onShorts) Modifier else Modifier.background(Bg))
            .statusBarsPadding()
            .height(TopTabsHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tabs.forEach { t ->
            val selected = t.route == nav.current
            TopTabItem(t.label, t.icon, selected, sel, idle, pill) { if (!selected) nav.selectTab(t.route) }
        }
        // account
        val thumb = Session.thumb
        Box(
            Modifier
                .size(44.dp)
                .bouncyClickable(pressedScale = 0.9f, onClick = { if (nav.current != Route.Settings) nav.push(Route.Settings) })
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (thumb.isNotEmpty()) AppImage(thumb, Modifier.size(30.dp), CircleShape)
            else Icon(Icons.Rounded.PersonOutline, contentDescription = "Account", tint = idle, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun TopTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    selColor: Color,
    idleColor: Color,
    pill: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .height(44.dp)
            .bouncyClickable(pressedScale = 0.92f, onClick = onClick)
            .clip(CircleShape)
            .background(if (selected) pill else Color.Transparent)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 500f))
            .padding(horizontal = if (selected) 16.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) selColor else idleColor, modifier = Modifier.size(24.dp))
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = selColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        }
    }
}
