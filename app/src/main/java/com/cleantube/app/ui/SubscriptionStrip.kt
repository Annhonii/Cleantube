package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.VideoItem

/** The signed-in account's subscribed channels, kept for a while so switching tabs is instant. */
internal object AccountChannelsCache {
    var list: List<ChannelItem> = emptyList()
        private set
    private var time = 0L

    fun valid(): Boolean = list.isNotEmpty() && System.currentTimeMillis() - time < 15 * 60_000L

    fun put(channels: List<ChannelItem>) {
        list = channels
        time = System.currentTimeMillis()
    }
}

/**
 * Subscribed channels like YouTube's strip: the channel that uploaded most recently comes first.
 * [videos] must be newest-first (the order of the feed); channels without a video in it follow after.
 */
fun orderByLatestUpload(channels: List<ChannelItem>, videos: List<VideoItem>): List<ChannelItem> {
    val byId = channels.associateBy { it.id }
    val recentIds = LinkedHashSet<String>()
    videos.forEach { if (it.channelId.isNotEmpty()) recentIds.add(it.channelId) }
    val recent = recentIds.mapNotNull { byId[it] }
    val rest = channels.filter { it.id !in recentIds }
    return (recent + rest).distinctBy { it.id }
}

/** Row of round channel logos with the name underneath; tap opens the channel. */
@Composable
fun SubscriptionStrip(channels: List<ChannelItem>, videos: List<VideoItem>) {
    if (channels.isEmpty()) return
    val nav = LocalNav.current
    val ordered = remember(channels, videos) { orderByLatestUpload(channels, videos) }

    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(ordered.size, key = { ordered[it].id }) { i ->
                val c = ordered[i]
                Column(
                    Modifier
                        .width(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { nav.push(Route.Channel(c.id, c)) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppImage(c.thumb, Modifier.size(60.dp), CircleShape)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        c.title,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    }
}
