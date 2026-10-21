package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.Api
import com.cleantube.app.data.NotificationItem
import com.cleantube.app.data.Session
import com.cleantube.app.data.TabCache

/**
 * The Notification tab: YouTube's own notification bell (new uploads, live streams, comments, replies,
 * mentions...). It needs a signed-in account, because YouTube only keeps notifications per account.
 */
@Composable
fun NotificationsScreen() {
    val nav = LocalNav.current

    Column(Modifier.fillMaxSize()) {
        TabTopBar("Notification")
        if (!Session.loggedIn) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Sign in with your Google account to see your YouTube notifications: new uploads, live streams, comments, replies and mentions.",
                    color = TextMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(16.dp))
                Pill("Sign in", onClick = { nav.push(Route.Login) }, active = true)
            }
        } else {
            val paged = TabCache.paged("notifications:${Session.handle}") { token -> Api.notifications(token) }
            PagedColumn(
                paged = paged,
                emptyText = "No notifications yet."
            ) { n ->
                NotificationRow(n, onClick = {
                    when {
                        n.videoId.isNotEmpty() -> nav.push(Route.Video(n.videoId))
                        n.channelId.isNotEmpty() -> nav.push(Route.Channel(n.channelId))
                        n.handle.isNotEmpty() -> nav.push(Route.Channel(id = "", handle = n.handle))
                    }
                })
            }
        }
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "upload" -> "New video"
    "live" -> "Live"
    "premiere" -> "Premiere"
    "comment" -> "Comment"
    "reply" -> "Reply"
    "mention" -> "Mention"
    else -> ""
}

@Composable
private fun NotificationRow(n: NotificationItem, onClick: () -> Unit) {
    Row(
        Modifier
            .bouncyClickable(pressedScale = 0.975f, onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (n.read) Color.Transparent else Card)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp)) {
            if (n.avatar.isNotEmpty()) {
                AppImage(n.avatar, Modifier.size(46.dp), CircleShape)
            } else {
                Box(Modifier.size(46.dp).clip(CircleShape).background(TertiaryContainer), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Rounded.Notifications, contentDescription = null, tint = TextMuted, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                n.message,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (n.read) FontWeight.Normal else FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            val label = kindLabel(n.kind)
            val meta = listOf(label, n.time).filter { it.isNotEmpty() }.joinToString(" • ")
            if (meta.isNotEmpty()) {
                Text(meta, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (n.thumb.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            AppImage(n.thumb, Modifier.width(96.dp).aspectRatio(16f / 9f), RoundedCornerShape(10.dp))
        }
        if (!n.read) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Tertiary))
        }
    }
}
