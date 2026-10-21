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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.cleantube.app.data.Api
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.VideoItem

/** The signed-in account's subscribed channels, kept for a while so switching tabs is instant. */
internal object AccountChannelsCache {
    var list: List<ChannelItem> = emptyList()
        private set
    private var time = 0L

    fun valid(): Boolean = list.isNotEmpty() && System.currentTimeMillis() - time < 15 * 60_000L

    fun invalidate() {
        time = 0L
    }

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

private val NewDot = Color(0xFF3EA6FF)

/**
 * YouTube style strip: round logos, channels with a video you haven't watched yet first and marked with a blue dot,
 * swipe left for the rest. The arrow at the end opens the list of all subscriptions (with Subscribe / Unsubscribe).
 */
@Composable
fun SubscriptionStrip(channels: List<ChannelItem>, videos: List<VideoItem>) {
    if (channels.isEmpty()) return
    val nav = LocalNav.current
    val history = Graph.store.history
    val ordered = remember(channels, videos, history) {
        val seen = history.mapTo(HashSet()) { it.id }
        val fresh = videos.filter { it.id !in seen && !it.short }.mapTo(HashSet()) { it.channelId }
        // newest upload first (feed order); channels with something unwatched go in front, the rest follow
        orderByLatestUpload(channels, videos).sortedByDescending { it.id in fresh } to fresh
    }
    val (list, fresh) = ordered
    var showAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(list.size, key = { list[it].id }) { i ->
                    val c = list[i]
                    Column(
                        Modifier
                            .bouncyClickable(pressedScale = 0.9f) { nav.push(Route.Channel(c.id, c)) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppImage(c.thumb, Modifier.size(58.dp), CircleShape)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (c.id in fresh) NewDot else Color.Transparent)
                        )
                    }
                }
            }
            IconButton(onClick = { showAll = true }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "All subscriptions", tint = TextPrimary, modifier = Modifier.size(30.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    }

    if (showAll) AllSubscriptionsSheet(list, onDismiss = { showAll = false })
}

/** Every subscribed channel: logo, name and a Subscribe / Unsubscribe button on the right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllSubscriptionsSheet(channels: List<ChannelItem>, onDismiss: () -> Unit) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    // channels unsubscribed while the sheet is open stay in the list so they can be subscribed again
    val off = remember { mutableStateListOf<String>() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Bg, contentColor = TextPrimary) {
        Text(
            "Subscriptions",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(channels.size, key = { channels[it].id }) { i ->
                val c = channels[i]
                val subscribed = c.id !in off
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onDismiss()
                                nav.push(Route.Channel(c.id, c))
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppImage(c.thumb, Modifier.size(46.dp), CircleShape)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            c.title,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Pill(
                        if (subscribed) "Unsubscribe" else "Subscribe",
                        onClick = {
                            val turnOn = !subscribed
                            if (turnOn) off.remove(c.id) else off.add(c.id)
                            val store = Graph.store
                            if (store.isSubscribed(c.id) != turnOn) store.toggleSubscription(c)
                            if (Session.loggedIn) {
                                scope.launch {
                                    try {
                                        Api.subscribe(c.id, turnOn)
                                        AccountChannelsCache.invalidate()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        // the local list is still correct
                                    }
                                }
                            }
                        },
                        active = !subscribed
                    )
                }
            }
        }
    }
}
