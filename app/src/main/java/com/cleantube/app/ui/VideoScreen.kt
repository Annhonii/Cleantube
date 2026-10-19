package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.ApiException
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.Comment
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Page
import com.cleantube.app.data.Paged
import com.cleantube.app.data.VideoDetails
import com.cleantube.app.data.VideoItem
import com.cleantube.app.data.Api

/** Soft blue used only for the likes chip, same as the reference design. */
private val LikeBlue = Color(0xFF5B9BD5)

@Composable
fun VideoScreen(route: Route.Video) {
    val nav = LocalNav.current
    val store = Graph.store
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var details by remember(route.id) { mutableStateOf<VideoDetails?>(null) }
    var channel by remember(route.id) { mutableStateOf<ChannelItem?>(null) }
    var error by remember(route.id) { mutableStateOf<ApiException?>(null) }
    var reload by remember(route.id) { mutableStateOf(0) }
    var tab by remember(route.id) { mutableStateOf(0) }
    var descOpen by remember(route.id) { mutableStateOf(false) }
    var saveDialog by remember(route.id) { mutableStateOf(false) }
    var downloadDialog by remember(route.id) { mutableStateOf(false) }

    val video = details?.video ?: route.seed

    // Opening a video counts as watching it (the player itself works without any API call).
    LaunchedEffect(route.id) { route.seed?.let { store.addHistory(it) } }

    LaunchedEffect(route.id, reload) {
        error = null
        try {
            val d = Api.videoDetails(route.id)
            details = d
            channel = d.channel
            if (route.seed == null) store.addHistory(d.video)
        } catch (e: ApiException) {
            error = e
        }
    }

    val more = remember(details) {
        Paged<VideoItem>(scope) { token ->
            if (token == null) Page(details?.related.orEmpty(), details?.relatedNext)
            else Api.related(token)
        }
    }
    val comments = remember(route.id) {
        Paged(scope) { token -> Api.comments(route.id, token) }
    }
    // tab 0 = Up next, tab 1 = comments opened from the comments card
    val active: Paged<*> = if (tab == 0) more else comments
    val listState = rememberLazyListState()

    LaunchedEffect(tab, details) {
        if (tab == 0 && details != null && more.items.isEmpty()) more.loadMore()
        // comments are also loaded while closed, the card above shows the first one as a preview
        if ((tab == 1 || details != null) && comments.items.isEmpty()) comments.loadMore()
    }
    LoadMoreEffect(listState, active)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // rounded player "card"
        PlayerBox(
            videoId = route.id,
            embeddable = details?.embeddable ?: true,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(24.dp))
        )

        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {

            // ---- title (tap to open the description) + stats
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { descOpen = !descOpen }
                        .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            video?.title ?: "Loading…",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = if (descOpen) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(top = 2.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (descOpen) "Hide description" else "Show description",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp).rotate(if (descOpen) 180f else 0f)
                        )
                    }
                    val meta = listOfNotNull(
                        video?.viewsText?.takeIf { it.isNotEmpty() },
                        video?.ageText?.takeIf { it.isNotEmpty() }
                    ).joinToString(" • ")
                    if (meta.isNotEmpty()) {
                        Text(meta, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            // ---- description (opens under the title)
            val description = details?.description.orEmpty()
            if (descOpen && description.isNotBlank()) {
                item {
                    Text(
                        description,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Card)
                            .padding(16.dp)
                    )
                }
            }

            // ---- actions
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Likes are shown as a number only: the app has no way to like a video.
                    val likes = details?.likesText.orEmpty()
                    if (likes.isNotEmpty()) {
                        ActionChip(likes, Icons.Rounded.ThumbUp, onClick = null, container = LikeBlue, content = Color.White)
                    }
                    ActionChip("Share", Icons.Rounded.Share, onClick = { shareVideo(ctx, route.id) })
                    ActionChip("Save", Icons.Rounded.Add, onClick = { if (video != null) saveDialog = true })
                    ActionChip("Download", Icons.Rounded.Download, onClick = { if (video != null) downloadDialog = true })
                    ActionChip("YouTube", Icons.Rounded.PlayArrow, onClick = { openInYouTube(ctx, route.id) })
                }
            }

            // ---- channel card
            if (video != null && video.channelId.isNotEmpty()) {
                item {
                    val ch = channel ?: ChannelItem(video.channelId, video.channelTitle)
                    val subscribed = store.isSubscribed(ch.id)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Card)
                            .clickable { nav.push(Route.Channel(ch.id, ch)) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppImage(ch.thumb, Modifier.size(46.dp), CircleShape)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.title,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (ch.subsText.isNotEmpty()) {
                                Text(ch.subsText, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (subscribed) "Subscribed" else "Subscribe",
                            color = if (subscribed) TextPrimary else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (subscribed) Hairline else Accent)
                                .clickable { toggleSubscription(scope, ch) }
                                .padding(horizontal = 20.dp, vertical = 11.dp)
                        )
                    }
                }
            }

            // ---- comments card (first comment as a preview, tap to open them all)
            item {
                val count = details?.commentCountText.orEmpty()
                val first = comments.items.firstOrNull()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Card)
                        .clickable { tab = if (tab == 1) 0 else 1 }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Comments", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        if (count.isNotEmpty()) {
                            Text("  $count", color = TextMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Rounded.UnfoldMore,
                            contentDescription = if (tab == 1) "Show up next" else "Show all comments",
                            tint = TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (first != null && tab == 0) {
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                            AppImage(first.avatar, Modifier.size(28.dp), CircleShape)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                first.text,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ---- general error (video details failed, e.g. quota)
            val err = error
            if (err != null && details == null) {
                item { ErrorBox(err, onRetry = { reload++ }) }
            }

            // ---- up next / comments
            if (tab == 0) {
                item {
                    Text(
                        "Up next",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp)
                    )
                }
                val vids = more.items.filter { it.id != route.id }
                items(vids.size) { i ->
                    val v = vids[i]
                    VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
                }
            } else {
                val list = comments.items
                items(list.size) { i -> CommentRow(list[i]) }
            }

            item {
                val e = active.error
                if (e != null) {
                    ErrorBox(e, onRetry = { active.loadMore() })
                } else if (active.loading) {
                    Loading()
                } else if (active.items.isEmpty() && active.finished) {
                    EmptyState(if (tab == 0) "No other videos." else "No comments.")
                }
            }
        }
    }

    if (downloadDialog && video != null) {
        DownloadDialog(video) { downloadDialog = false }
    }
    if (saveDialog && video != null) {
        SaveToPlaylistDialog(video) { saveDialog = false }
    }
}

/** Rounded action chip. [onClick] = null makes it a plain label (no ripple, not tappable). */
@Composable
private fun ActionChip(
    text: String,
    icon: ImageVector,
    onClick: (() -> Unit)?,
    container: Color = Card,
    content: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun CommentRow(c: Comment) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp)) {
        AppImage(c.avatar, Modifier.size(34.dp), CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOf(c.author, c.publishedAt).filter { it.isNotEmpty() }.joinToString(" • "),
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(c.text, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            val extra = listOfNotNull(
                if (c.likesText.isNotEmpty() && c.likesText != "0") "${c.likesText} likes" else null,
                if (c.replies > 0) "${c.replies} replies" else null
            ).joinToString(" • ")
            if (extra.isNotEmpty()) {
                Text(extra, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
