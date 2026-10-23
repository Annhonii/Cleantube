package com.cleantube.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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
import com.cleantube.app.data.Post
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

    Box(Modifier.fillMaxSize()) {
        if (ch == null) {
            Column(Modifier.fillMaxSize()) {
                ScreenTitle(route.seed?.title ?: "Channel", onBack = { nav.back() })
                if (err != null) ErrorBox(err, onRetry = { attempt++ }) else Loading()
            }
        } else {
            val paged = remember(ch.id) {
                Paged<VideoItem>(scope) { token -> Api.channelVideos(ch.id, token) }
            }
            val posts = remember(ch.id) {
                Paged<Post>(scope) { token -> Api.channelPosts(ch.id, token) }
            }
            var tab by remember(ch.id) { mutableStateOf(0) }
            if (tab == 1) {
                PagedColumn(
                    posts,
                    emptyText = "No posts on this channel.",
                    header = { item { ChannelHeader(ch, tab, onTab = { tab = it }) } }
                ) { post -> PostCard(post) }
            } else {
                PagedColumn(
                    paged,
                    emptyText = "No public videos.",
                    header = { item { ChannelHeader(ch, tab, onTab = { tab = it }) } }
                ) { v ->
                    if (tab == 0) CompactRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
                }
            }
            // back button floats over the banner
            Box(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .bouncyClickable(pressedScale = 0.88f, onClick = { nav.back() }),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ChannelHeader(ch: ChannelItem, tab: Int, onTab: (Int) -> Unit) {
    val store = Graph.store
    val subscribed = store.isSubscribed(ch.id)
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth()) {
        // banner edge to edge, Subscribe on its lower right corner
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Hairline)) {
            if (ch.banner.isNotEmpty()) AppImage(ch.banner, Modifier.matchParentSize(), RoundedCornerShape(0.dp))
            else if (ch.thumb.isNotEmpty()) AppImage(ch.thumb, Modifier.matchParentSize(), RoundedCornerShape(0.dp))
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Bg)
                )
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 12.dp)
                    .clip(CircleShape)
                    .background(if (subscribed) Card else Accent)
                    .bouncyClickable(pressedScale = 0.94f, onClick = { toggleSubscription(scope, ch) })
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    if (subscribed) "Subscribed" else "Subscribe",
                    color = if (subscribed) TextPrimary else OnAccent,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                )
            }
        }

        // name, handle, stats
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                ch.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (ch.handle.isNotEmpty()) {
                Text(ch.handle, color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            }
            val subs = ch.subsText.ifEmpty { ch.subs.takeIf { it >= 0 }?.let { "${formatCount(it)} subscribers" }.orEmpty() }
            if (subs.isNotEmpty() || ch.videosText.isNotEmpty()) {
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (subs.isNotEmpty()) Text(subs, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (subs.isNotEmpty() && ch.videosText.isNotEmpty()) {
                        Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(28.dp).background(Hairline))
                    }
                    if (ch.videosText.isNotEmpty()) Text(ch.videosText, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }

        // chips
        Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Videos", "Posts", "About").forEachIndexed { i, label ->
                val sel = tab == i
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (sel) Accent else Card)
                        .bouncyClickable(pressedScale = 0.94f, onClick = { onTab(i) })
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (sel) OnAccent else TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }
        }

        if (tab == 2) {
            Text(
                ch.description.ifBlank { "No description." },
                color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Card)
                    .padding(14.dp)
            )
        } else if (tab == 0) {
            Text(
                "Videos", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
            )
        }
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

/** One post of a channel: author line, text, picture / shared video / poll options, counts. */
@Composable
private fun PostCard(post: Post) {
    val nav = LocalNav.current
    var open by remember(post.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.avatar.isNotEmpty()) {
                AppImage(post.avatar, Modifier.size(36.dp), CircleShape)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(post.author, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (post.age.isNotEmpty()) Text(post.age, color = TextMuted, fontSize = 12.sp)
            }
        }
        if (post.text.isNotBlank()) {
            Text(
                post.text, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp,
                maxLines = if (open) Int.MAX_VALUE else 8, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                    .clickable { open = !open }
            )
        }
        if (post.image.isNotEmpty()) {
            val modifier = Modifier.fillMaxWidth().padding(top = 10.dp).aspectRatio(16f / 9f)
            Box(
                if (post.video.isNotEmpty()) modifier.clickable { nav.push(Route.Video(post.video)) } else modifier
            ) {
                AppImage(post.image, Modifier.matchParentSize(), RoundedCornerShape(12.dp))
            }
        }
        if (post.video.isNotEmpty() && post.videoTitle.isNotEmpty()) {
            Text(
                post.videoTitle, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp).clickable { nav.push(Route.Video(post.video)) }
            )
        }
        if (post.poll.isNotEmpty()) {
            Text("Poll: ${post.poll}", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        val counts = listOf(post.likes.takeIf { it.isNotEmpty() }?.let { "$it likes" }, post.comments.takeIf { it.isNotEmpty() }?.let { "$it comments" })
            .filterNotNull().joinToString("  •  ")
        if (counts.isNotEmpty()) Text(counts, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}
