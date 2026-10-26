package com.cleantube.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import android.widget.Toast
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
            val posts = remember(ch.id) { Paged<Post>(scope) { token -> Api.channelPosts(ch.id, token) } }
            val live = remember(ch.id) { Paged<VideoItem>(scope) { token -> Api.channelVideosTab(ch.id, "live", token) } }
            val shorts = remember(ch.id) { Paged<VideoItem>(scope) { token -> Api.channelVideosTab(ch.id, "shorts", token) } }
            val lists = remember(ch.id) { Paged<PlaylistItem>(scope) { token -> Api.channelPlaylists(ch.id, token) } }
            var tab by remember(ch.id) { mutableStateOf(0) }
            val head: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                item(key = "channel-header") {
                    ChannelHeader(ch, tab, onTab = { tab = it }, onBack = { nav.back() }, onAbout = { nav.push(Route.ChannelAbout(ch)) })
                }
            }
            when (tab) {
                0 -> PagedColumn(paged, emptyText = "No public videos.", header = head) { v ->
                    VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
                }
                1 -> PagedColumn(live, emptyText = "No live streams.", header = head) { v ->
                    VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
                }
                2 -> PagedColumn(posts, emptyText = "No posts on this channel.", header = head) { post -> PostCard(post) }
                3 -> PagedColumn(lists, emptyText = "No playlists.", header = head) { pl ->
                    PlaylistRow(pl, onClick = { nav.push(Route.Playlist(pl.id, pl.title)) })
                }
                else -> PagedColumn(shorts, emptyText = "No Shorts.", header = head) { v ->
                    VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
                }
            }
        }
    }
}

private val M3Standard = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** "1.2M subscribers" -> ("1.2M", "subscribers") */
private fun statParts(t: String): Pair<String, String> {
    val i = t.indexOf(' ')
    return if (i < 0) t to "" else t.substring(0, i) to t.substring(i + 1)
}

@Composable
private fun ChannelHeader(ch: ChannelItem, tab: Int, onTab: (Int) -> Unit, onBack: () -> Unit, onAbout: () -> Unit) {
    val store = Graph.store
    val subscribed = store.isSubscribed(ch.id)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val avatarSize = 104.dp

    Column(Modifier.fillMaxWidth()) {
        // ---- banner (rounded, inset) with back and the 3-dot menu on it; the channel photo overlaps its lower left
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.6f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Accent.copy(alpha = 0.55f), Card)))
            ) {
                if (ch.banner.isNotEmpty()) AppImage(ch.banner, Modifier.matchParentSize(), RoundedCornerShape(24.dp))
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x66000000))
                        .bouncyClickable(pressedScale = 0.88f, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                            .bouncyClickable(pressedScale = 0.88f, onClick = { menu = true }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = {
                                menu = false
                                shareText(ctx, "https://www.youtube.com/channel/${ch.id}")
                            }
                        )
                    }
                }
            }
            AppImage(
                ch.thumb,
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = avatarSize / 2)
                    .size(avatarSize)
                    .border(4.dp, Bg, CircleShape),
                CircleShape
            )
        }

        // ---- Subscribe next to the photo
        val subBg by animateColorAsState(if (subscribed) Card else TextPrimary, tween(300, easing = M3Standard), label = "subBg")
        val subFg by animateColorAsState(if (subscribed) TextPrimary else Bg, tween(300, easing = M3Standard), label = "subFg")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (subscribed) {
                // YouTube-style bell: tap to get a notification for this channel's new videos
                val belled = store.isBelled(ch.id)
                Box(
                    Modifier
                        .padding(end = 10.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (belled) TextPrimary else Card)
                        .bouncyClickable(pressedScale = 0.9f, onClick = {
                            store.toggleBell(ch.id)
                            if (store.isBelled(ch.id) && !store.alertsEnabled) store.alertsEnabled = true
                            com.cleantube.app.data.UploadAlerts.sync(ctx)
                            Toast.makeText(
                                ctx,
                                if (store.isBelled(ch.id)) "You'll get notified about new videos from ${ch.title}"
                                else "Notifications off for ${ch.title}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (belled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                        contentDescription = if (belled) "Turn off notifications" else "Notify me about new videos",
                        tint = if (belled) Bg else TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Box(
                Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(subBg)
                    .bouncyClickable(pressedScale = 0.95f, onClick = { toggleSubscription(scope, ch) })
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (subscribed) "Subscribed" else "Subscribe", color = subFg, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        }

        // ---- name and handle
        Text(
            ch.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        if (ch.handle.isNotEmpty()) Text(ch.handle, color = TextMuted, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp, top = 2.dp))

        // ---- info card: numbers, line, description
        val subs = ch.subsText.ifEmpty { ch.subs.takeIf { it >= 0 }?.let { "${formatCount(it)} subscribers" }.orEmpty() }
        val stats = listOf(subs, ch.videosText).filter { it.isNotEmpty() }
        if (stats.isNotEmpty() || ch.description.isNotBlank()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Card)
                    .clickable(onClick = onAbout)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                if (stats.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        stats.forEachIndexed { i, t ->
                            val (n, l) = statParts(t)
                            if (i > 0) Text("  \u00B7  ", color = TextMuted, fontSize = 15.sp)
                            Text(n, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (l.isNotEmpty()) Text(" ${l.replaceFirstChar { it.uppercase() }}", color = TextMuted, fontSize = 16.sp)
                        }
                    }
                }
                if (stats.isNotEmpty() && ch.description.isNotBlank()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(Hairline))
                }
                if (ch.description.isNotBlank()) {
                    Text(
                        ch.description, color = TextPrimary, fontSize = 15.sp, lineHeight = 24.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ---- chips
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("Videos", "Live", "Posts", "Playlists", "Shorts").forEachIndexed { i, label ->
                val sel = tab == i
                val bg by animateColorAsState(if (sel) Accent else Color.Transparent, tween(300, easing = M3Standard), label = "chipBg")
                val fg by animateColorAsState(if (sel) OnAccent else TextPrimary, tween(300, easing = M3Standard), label = "chipFg")
                val line by animateColorAsState(if (sel) Accent else Hairline, tween(300, easing = M3Standard), label = "chipLine")
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, line, CircleShape)
                        .background(bg)
                        .bouncyClickable(pressedScale = 0.94f, onClick = { onTab(i) })
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = fg, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                }
            }
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

/** The channel's About page, full screen: photo, name, numbers, the whole description, links. */
@Composable
fun ChannelAboutScreen(ch: ChannelItem) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("About", onBack = { nav.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = listBottomPad()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppImage(ch.thumb, Modifier.size(72.dp), CircleShape)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(ch.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    if (ch.handle.isNotEmpty()) Text(ch.handle, color = TextMuted, fontSize = 15.sp)
                }
            }
            val subs = ch.subsText.ifEmpty { ch.subs.takeIf { it >= 0 }?.let { "${formatCount(it)} subscribers" }.orEmpty() }
            val stats = listOf(subs, ch.videosText).filter { it.isNotEmpty() }
            if (stats.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    stats.forEach { t ->
                        val (n, l) = statParts(t)
                        Row {
                            Text(n, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (l.isNotEmpty()) Text(" ${l.replaceFirstChar { it.uppercase() }}", color = TextMuted, fontSize = 16.sp)
                        }
                    }
                }
            }
            if (ch.description.isNotBlank()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
                    Text("Description", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(ch.description, color = TextPrimary, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Share", onClick = { shareText(ctx, "https://www.youtube.com/channel/${ch.id}") })
                Pill("Open in YouTube", onClick = { openUrl(ctx, "https://www.youtube.com/channel/${ch.id}") })
            }
        }
    }
}
