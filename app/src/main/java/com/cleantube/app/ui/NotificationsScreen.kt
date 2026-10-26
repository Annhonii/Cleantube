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
import androidx.compose.material3.Card as M3Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement as Arr
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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

private enum class NotifFilter(val label: String) { All("All"), Videos("Videos"), Mentions("Mentions") }

/** Uploads, live streams and premieres. */
private fun NotificationItem.isVideo() = kind == "upload" || kind == "live" || kind == "premiere"

/** Comments, replies and mentions: what other people did with you / your comments. */
private fun NotificationItem.isMention() = kind == "comment" || kind == "reply" || kind == "mention"

/**
 * The Notification screen: YouTube's own notification bell (new uploads, live streams, comments, replies,
 * mentions...). It needs a signed-in account, because YouTube only keeps notifications per account.
 * Filter chips (All / Videos / Mentions) sit under the title; "All" shows the two kinds as separate sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(16.dp))
                Pill("Sign in", onClick = { nav.push(Route.Login) }, active = true)
            }
        } else {
            val paged = TabCache.paged("notifications:${Session.handle}") { token -> Api.notifications(token) }
            var filter by rememberSaveable { mutableStateOf(NotifFilter.All) }
            val open: (NotificationItem) -> Unit = { n ->
                when {
                    n.videoId.isNotEmpty() -> nav.push(Route.Video(n.videoId))
                    n.channelId.isNotEmpty() -> nav.push(Route.Channel(n.channelId))
                    n.handle.isNotEmpty() -> nav.push(Route.Channel(id = "", handle = n.handle))
                }
            }

            // filter chips
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arr.spacedBy(8.dp)
            ) {
                NotifFilter.values().forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label, style = MaterialTheme.typography.labelLarge) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = TextMuted,
                            selectedContainerColor = Accent,
                            selectedLabelColor = OnAccent
                        )
                    )
                }
            }

            val all = paged.items
            val shown = when (filter) {
                NotifFilter.All -> all
                NotifFilter.Videos -> all.filter { it.isVideo() }
                NotifFilter.Mentions -> all.filter { it.isMention() }
            }
            val state = paged.listState
            LoadMoreEffect(state, paged)
            // a filter can leave only a few rows on screen: keep loading older notifications until the list is filled
            LaunchedEffect(filter, all.size, paged.loading, paged.finished) {
                if (filter != NotifFilter.All && shown.size < 6 && !paged.loading && !paged.finished && paged.error == null && all.isNotEmpty()) {
                    paged.loadMore()
                }
            }

            PullRefresh(refreshing = paged.refreshing, onRefresh = { paged.pullRefresh() }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(), state = state,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = listBottomPad()),
                    verticalArrangement = Arr.spacedBy(0.dp)
                ) {
                    if (filter == NotifFilter.All) {
                        val videos = shown.filter { it.isVideo() }
                        val mentions = shown.filter { it.isMention() }
                        val other = shown.filter { !it.isVideo() && !it.isMention() }
                        if (videos.isNotEmpty()) {
                            item(key = "h-videos") { SectionTitle("Video uploads") }
                            itemsIndexed(videos) { i, n -> NotificationCard(n, groupedCardShape(i, videos.size), onClick = { open(n) }) }
                        }
                        if (mentions.isNotEmpty()) {
                            item(key = "h-mentions") { SectionTitle("Mentions & comments") }
                            itemsIndexed(mentions) { i, n -> NotificationCard(n, groupedCardShape(i, mentions.size), onClick = { open(n) }) }
                        }
                        if (other.isNotEmpty()) {
                            item(key = "h-other") { SectionTitle("Other") }
                            itemsIndexed(other) { i, n -> NotificationCard(n, groupedCardShape(i, other.size), onClick = { open(n) }) }
                        }
                    } else {
                        itemsIndexed(shown) { i, n -> NotificationCard(n, groupedCardShape(i, shown.size), onClick = { open(n) }) }
                    }
                    item(key = "footer") {
                        val err = paged.error
                        when {
                            err != null -> ErrorBox(err, onRetry = { paged.loadMore() })
                            paged.loading -> Loading()
                            shown.isEmpty() && paged.finished -> EmptyState(
                                when (filter) {
                                    NotifFilter.All -> "No notifications yet."
                                    NotifFilter.Videos -> "No new videos from your channels."
                                    NotifFilter.Mentions -> "No mentions or comments yet."
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = TextMuted, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp)
    )
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

/**
 * One notification as a row of the system Settings list: compact (about 72dp), tonal container, joined corners,
 * picture at the left, text in the normal weight (16sp title, 14sp kind + time), small thumbnail at the right.
 * An unread notification only has a stronger container and a small dot on its thumbnail (no bold text).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(n: NotificationItem, shape: RoundedCornerShape, onClick: () -> Unit) {
    M3Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (n.read) Card else SurfaceHighest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (n.avatar.isNotEmpty()) {
                AppImage(n.avatar, Modifier.size(40.dp), CircleShape)
            } else {
                Box(Modifier.size(40.dp).clip(CircleShape).background(TertiaryContainer), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Rounded.Notifications, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    n.message,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOf(kindLabel(n.kind), n.time).filter { it.isNotEmpty() }.joinToString(" • ")
                if (meta.isNotEmpty()) {
                    Text(meta, color = TextMuted, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (n.thumb.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Box {
                    AppImage(n.thumb, Modifier.width(72.dp).aspectRatio(16f / 9f), RoundedCornerShape(8.dp))
                    if (!n.read) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(8.dp)
                                .clip(CircleShape).background(Accent)
                        )
                    }
                }
            } else if (!n.read) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
            }
        }
    }
}
