package com.cleantube.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
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
import com.cleantube.app.data.formatCount
import com.cleantube.app.data.timeAgo
import com.cleantube.app.data.uploadsPlaylistOf

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

    val video = details?.video ?: route.seed
    val channelId = video?.channelId.orEmpty()

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
    val active: Paged<*> = if (tab == 0) more else comments
    val listState = rememberLazyListState()

    LaunchedEffect(tab, details) {
        if (tab == 0 && details != null && more.items.isEmpty()) more.loadMore()
        if (tab == 1 && comments.items.isEmpty()) comments.loadMore()
    }
    LoadMoreEffect(listState, active)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PlayerBox(
            videoId = route.id,
            embeddable = details?.embeddable ?: true
        )

        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {

            // ---- title + stats
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        video?.title ?: "Loading…",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOfNotNull(
                        video?.viewsText?.takeIf { it.isNotEmpty() },
                        details?.likesText?.takeIf { it.isNotEmpty() }?.let { "$it likes" },
                        video?.ageText?.takeIf { it.isNotEmpty() }
                    ).joinToString(" • ")
                    if (meta.isNotEmpty()) {
                        Text(meta, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // ---- actions
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Pill("Share", onClick = { shareVideo(ctx, route.id) }, icon = Icons.Rounded.Share)
                    Pill(
                        "Save",
                        onClick = { if (video != null) saveDialog = true },
                        icon = Icons.Rounded.Add
                    )
                    Pill("Open in YouTube", onClick = { openInYouTube(ctx, route.id) }, icon = Icons.Rounded.PlayArrow)
                }
            }

            // ---- channel box
            if (video != null && video.channelId.isNotEmpty()) {
                item {
                    val ch = channel ?: ChannelItem(video.channelId, video.channelTitle)
                    val subscribed = store.isSubscribed(ch.id)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(16.dp))
                            .clickable { nav.push(Route.Channel(ch.id, ch)) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppImage(ch.thumb, Modifier.size(42.dp), CircleShape)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.title,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (ch.subsText.isNotEmpty()) {
                                Text(ch.subsText, color = TextMuted, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Pill(
                            if (subscribed) "Subscribed" else "Subscribe",
                            onClick = { toggleSubscription(scope, ch) },
                            active = !subscribed
                        )
                    }
                }
            }

            // ---- description
            val description = details?.description.orEmpty()
            if (description.isNotBlank()) {
                item {
                    Text(
                        description,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        maxLines = if (descOpen) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Card)
                            .clickable { descOpen = !descOpen }
                            .padding(14.dp)
                    )
                }
            }

            // ---- general error (video details failed, e.g. quota)
            val err = error
            if (err != null && details == null) {
                item { ErrorBox(err, onRetry = { reload++ }) }
            }

            // ---- tabs
            item {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Pill("Up next", onClick = { tab = 0 }, active = tab == 0)
                    val c = details?.commentCountText.orEmpty()
                    Pill(
                        if (c.isNotEmpty()) "Comments · $c" else "Comments",
                        onClick = { tab = 1 },
                        active = tab == 1
                    )
                }
            }

            if (tab == 0) {
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

    if (saveDialog && video != null) {
        SaveToPlaylistDialog(video) { saveDialog = false }
    }
}

@Composable
private fun CommentRow(c: Comment) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
