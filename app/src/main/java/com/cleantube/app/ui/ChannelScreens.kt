package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.ApiException
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Paged
import com.cleantube.app.data.PlaylistItem
import com.cleantube.app.data.VideoItem
import com.cleantube.app.data.Api
import com.cleantube.app.data.formatCount

@Composable
fun ChannelScreen(route: Route.Channel) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()

    var channel by remember(route) { mutableStateOf(route.seed) }
    var error by remember(route) { mutableStateOf<ApiException?>(null) }
    var attempt by remember(route) { mutableStateOf(0) }

    // Always fetch the full channel (banner, description, subscriber count), even when we have a seed.
    LaunchedEffect(route, attempt) {
        error = null
        try {
            channel = if (route.id.isNotEmpty()) Api.channel(route.id)
            else Api.channelByHandle(route.handle)
        } catch (e: ApiException) {
            error = e
        }
    }

    val ch = channel
    val err = error

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(ch?.title ?: "Channel", onBack = { nav.back() })

        if (ch == null) {
            if (err != null) ErrorBox(err, onRetry = { attempt++ }) else Loading()
        } else {
            val paged = remember(ch.id) {
                Paged<VideoItem>(scope) { token -> Api.channelVideos(ch.id, token) }
            }
            PagedColumn(
                paged,
                emptyText = "No public videos.",
                header = { item { ChannelHeader(ch) } }
            ) { v ->
                VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
            }
        }
    }
}

@Composable
private fun ChannelHeader(ch: ChannelItem) {
    val store = Graph.store
    val subscribed = store.isSubscribed(ch.id)
    val scope = rememberCoroutineScope()
    var open by remember(ch.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (ch.banner.isNotEmpty()) {
            AppImage(
                ch.banner,
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(16f / 5f),
                RoundedCornerShape(16.dp)
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppImage(ch.thumb, Modifier.size(68.dp), CircleShape)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ch.title,
                    color = TextPrimary,
                    fontFamily = NType,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val line = listOfNotNull(
                    ch.handle.takeIf { it.isNotEmpty() },
                    ch.subsText.ifEmpty { ch.subs.takeIf { it >= 0 }?.let { "${formatCount(it)} subscribers" }.orEmpty() }.takeIf { it.isNotEmpty() }
                ).joinToString(" • ")
                if (line.isNotEmpty()) Text(line, color = TextMuted, fontSize = 13.sp)
            }
        }
        Pill(
            if (subscribed) "Subscribed" else "Subscribe",
            onClick = { toggleSubscription(scope, ch) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            active = !subscribed
        )
        if (ch.description.isNotBlank()) {
            Text(
                ch.description,
                color = TextPrimary,
                fontSize = 13.sp,
                maxLines = if (open) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Card)
                    .clickable { open = !open }
                    .padding(14.dp)
            )
        }
        Text(
            "Videos",
            color = TextPrimary,
            fontFamily = NType,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PlaylistScreen(route: Route.Playlist) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var info by remember(route.id) { mutableStateOf<PlaylistItem?>(null) }

    LaunchedEffect(route.id) {
        try {
            info = Api.playlist(route.id)
        } catch (e: ApiException) {
            // the title we already have is enough
        }
    }

    val paged = remember(route.id) {
        Paged<VideoItem>(scope) { token -> Api.playlistVideos(route.id, token) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(info?.title ?: route.title.ifEmpty { "Playlist" }, onBack = { nav.back() })
        PagedColumn(paged, emptyText = "This playlist is empty or private.") { v ->
            VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
        }
    }
}
