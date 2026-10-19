package com.cleantube.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt
import com.cleantube.app.data.Api
import com.cleantube.app.data.BackgroundPlay
import com.cleantube.app.data.Streams
import com.cleantube.app.data.ApiException
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.Comment
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Page
import com.cleantube.app.data.Paged
import com.cleantube.app.data.Session
import com.cleantube.app.data.VideoDetails
import com.cleantube.app.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch


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
    var commentsOpen by remember(route.id) { mutableStateOf(false) }
    var descOpen by remember(route.id) { mutableStateOf(false) }
    var saveDialog by remember(route.id) { mutableStateOf(false) }
    var downloadDialog by remember(route.id) { mutableStateOf(false) }
    // 1 = liked, -1 = disliked, 0 = no rating. Only what was done in this screen is known:
    // YouTube's earlier rating of the video isn't read from the page.
    var rating by remember(route.id) { mutableStateOf(0) }

    val video = details?.video ?: route.seed

    fun rate(target: Int) {
        if (!Session.loggedIn) {
            Toast.makeText(ctx, "Sign in with your Google account (Settings) to like or dislike.", Toast.LENGTH_SHORT).show()
            return
        }
        val before = rating
        val next = if (before == target) 0 else target
        rating = next
        scope.launch {
            try {
                Api.rate(route.id, when (next) { 1 -> "like"; -1 -> "dislike"; else -> "none" })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rating = before
                Toast.makeText(ctx, "Couldn't update your rating. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Background play: the sound keeps going with the screen off (see BackgroundPlay).
    var bgLoading by remember(route.id) { mutableStateOf(false) }
    fun toggleBackground() {
        if (BackgroundPlay.activeId == route.id) {
            BackgroundPlay.stop(ctx)
            return
        }
        if (bgLoading) return
        bgLoading = true
        Toast.makeText(ctx, "Starting background play…", Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val audio = Streams.fetch(route.id).options.filter { it.audioOnly }
                val pick = audio.firstOrNull { it.ext == "m4a" } ?: audio.firstOrNull()
                    ?: throw ApiException(ApiException.Kind.OTHER, "No audio stream found for this video.")
                val startMs = (PlayerControl.second * 1000).toLong()
                PlayerControl.pause?.invoke()
                BackgroundPlay.start(ctx, route.id, video?.title.orEmpty(), pick.url, pick.userAgent, startMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(ctx, "Couldn't start background play: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            } finally {
                bgLoading = false
            }
        }
    }

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
    val listState = rememberLazyListState()

    LaunchedEffect(details) {
        if (details != null && more.items.isEmpty()) more.loadMore()
        // loaded up front: the comments card shows the first one as a preview
        if (details != null && comments.items.isEmpty()) comments.loadMore()
    }
    LoadMoreEffect(listState, more)

    // Back closes the comments panel first, only then the video page.
    BackHandler(enabled = commentsOpen) { commentsOpen = false }

    val inPopup = PipHost.active
    Column(if (inPopup) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding()) {
        // rounded player "card"
        PlayerBox(
            videoId = route.id,
            embeddable = details?.embeddable ?: true,
            modifier = if (inPopup) Modifier else Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(24.dp))
        )

        // everything below the player is hidden while the video is in the popup window
        if (!inPopup) Box(Modifier.fillMaxSize()) {
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
                        val likes = details?.likesText.orEmpty()
                        ActionChip(
                            likes.ifEmpty { "Like" },
                            Icons.Rounded.ThumbUp,
                            onClick = { rate(1) },
                            container = if (rating == 1) Accent else Card,
                            content = if (rating == 1) OnAccent else TextPrimary
                        )
                        ActionChip(
                            "Dislike",
                            Icons.Rounded.ThumbDown,
                            onClick = { rate(-1) },
                            container = if (rating == -1) TextPrimary else Card,
                            content = if (rating == -1) Bg else TextPrimary
                        )
                        ActionChip("Share", Icons.Rounded.Share, onClick = { shareVideo(ctx, route.id) })
                        ActionChip("Save", Icons.Rounded.Add, onClick = { if (video != null) saveDialog = true })
                        ActionChip("Download", Icons.Rounded.Download, onClick = { if (video != null) downloadDialog = true })
                        val bgOn = BackgroundPlay.activeId == route.id
                        ActionChip(
                            "Background",
                            Icons.Rounded.Headphones,
                            onClick = { if (video != null) toggleBackground() },
                            container = if (bgOn) TextPrimary else Card,
                            content = if (bgOn) Bg else TextPrimary
                        )
                        ActionChip("Popup", Icons.Rounded.PictureInPictureAlt, onClick = { PipHost.enter?.invoke() })
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
                                color = if (subscribed) TextPrimary else OnAccent,
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

                // ---- comments card (first comment as a preview, tap to open the comments panel)
                item {
                    val count = details?.commentCountText.orEmpty()
                    val first = comments.items.firstOrNull()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Card)
                            .clickable { commentsOpen = true }
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
                                contentDescription = "Show all comments",
                                tint = TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (first != null) {
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

                // ---- up next
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

                item {
                    val e = more.error
                    if (e != null) {
                        ErrorBox(e, onRetry = { more.loadMore() })
                    } else if (more.loading) {
                        Loading()
                    } else if (more.items.isEmpty() && more.finished) {
                        EmptyState("No other videos.")
                    }
                }
            }

            // ---- comments panel: slides up under the player, the video keeps playing
            androidx.compose.animation.AnimatedVisibility(
                visible = commentsOpen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                CommentsPanel(
                    videoId = route.id,
                    comments = comments,
                    count = details?.commentCountText.orEmpty(),
                    onClose = { commentsOpen = false }
                )
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

/**
 * Full comments list in a rounded sheet that fills the area under the player. Loads more while scrolling.
 * Drag the sheet (header, or pull the list down while it is at the top) to close it; a floating
 * "Add a comment" pill sits at the bottom.
 */
@Composable
private fun CommentsPanel(videoId: String, comments: Paged<Comment>, count: String, onClose: () -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    LoadMoreEffect(state, comments)
    // opened replies, by the comment's replies token: each one is its own paged list
    val expanded = remember { mutableStateMapOf<String, Paged<Comment>>() }

    // ---- drag down to close
    var dragY by remember { mutableFloatStateOf(0f) }
    val closeAt = with(LocalDensity.current) { 120.dp.toPx() }
    suspend fun settle(velocity: Float) {
        if (dragY > closeAt || velocity > 1800f) onClose()
        else animate(dragY, 0f) { v, _ -> dragY = v }
    }
    val headerDrag = rememberDraggableState { d -> dragY = (dragY + d).coerceAtLeast(0f) }
    val nested = remember {
        object : NestedScrollConnection {
            // list is being pushed back up while the sheet is pulled down: move the sheet first
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && dragY > 0f) {
                    val c = maxOf(available.y, -dragY)
                    dragY += c
                    return Offset(0f, c)
                }
                return Offset.Zero
            }

            // list is already at the top and the finger keeps pulling down: drag the sheet
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    dragY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragY > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    // ---- add comment
    var composing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().offset { IntOffset(0, dragY.roundToInt()) }) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Card)
                // swallow taps so nothing underneath the panel reacts
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Column(
                Modifier.fillMaxWidth().draggable(
                    state = headerDrag,
                    orientation = Orientation.Vertical,
                    onDragStopped = { v -> settle(v) }
                )
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Hairline)
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Comments", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    if (count.isNotEmpty()) {
                        Text("  $count", color = TextMuted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close comments", tint = TextPrimary)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

            LazyColumn(
                Modifier.fillMaxSize().nestedScroll(nested),
                state = state,
                // room at the bottom so the floating pill never hides the last comment
                contentPadding = PaddingValues(top = 6.dp, bottom = 110.dp)
            ) {
                val list = comments.items
                list.forEach { c ->
                    val token = c.repliesToken
                    val rp = if (token.isNotEmpty()) expanded[token] else null
                    item {
                        CommentRow(
                            c,
                            repliesOpen = rp != null,
                            onToggleReplies = if (token.isNotEmpty()) {
                                {
                                    if (rp != null) {
                                        expanded.remove(token)
                                    } else {
                                        // first page uses the thread's token, later pages the one the previous page returned
                                        val p = Paged<Comment>(scope) { t -> Api.comments(videoId, t ?: token) }
                                        expanded[token] = p
                                        p.loadMore()
                                    }
                                }
                            } else null
                        )
                    }
                    if (rp != null) {
                        val replies = rp.items
                        items(replies.size) { i -> CommentRow(replies[i], reply = true) }
                        item { RepliesFooter(rp, onHide = { expanded.remove(token) }) }
                    }
                }
                item {
                    val e = comments.error
                    if (e != null) {
                        ErrorBox(e, onRetry = { comments.loadMore() })
                    } else if (comments.loading) {
                        Loading()
                    } else if (list.isEmpty() && comments.finished) {
                        EmptyState("No comments.")
                    }
                }
            }
        }

        AddCommentPill(
            onClick = {
                if (Session.loggedIn) composing = true
                else Toast.makeText(ctx, "Sign in to write a comment.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }

    if (composing) {
        AlertDialog(
            onDismissRequest = { if (!sending) composing = false },
            containerColor = Card,
            title = { Text("Add a comment") },
            text = { PillTextField(draft, { draft = it }, "Add a comment…", Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank() && !sending,
                    onClick = {
                        sending = true
                        scope.launch {
                            try {
                                Api.postComment(videoId, draft.trim())
                                Toast.makeText(ctx, "Comment posted.", Toast.LENGTH_SHORT).show()
                                draft = ""
                                composing = false
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Couldn't post: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                            } finally {
                                sending = false
                            }
                        }
                    }
                ) { Text(if (sending) "Posting…" else "Post", color = TextPrimary) }
            },
            dismissButton = {
                TextButton(enabled = !sending, onClick = { composing = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

/** Floating pill at the bottom of the comments sheet. */
@Composable
private fun AddCommentPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Card)
            .border(BorderStroke(1.dp, Hairline), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Edit, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Add a comment…", color = TextMuted, fontSize = 15.sp, maxLines = 1)
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

/** Under an opened comment's replies: status, "Show more replies" and "Hide replies". */
@Composable
private fun RepliesFooter(replies: Paged<Comment>, onHide: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 56.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (replies.error != null) {
            Text(
                "Couldn't load replies. Tap to retry",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { replies.loadMore() }.padding(vertical = 6.dp)
            )
        } else if (replies.loading) {
            Text("Loading replies…", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        } else {
            if (!replies.finished) {
                Text(
                    "Show more replies",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { replies.loadMore() }.padding(vertical = 6.dp)
                )
            }
            Text(
                "Hide replies",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onHide).padding(vertical = 6.dp)
            )
        }
    }
}

/**
 * One comment. [reply] = a reply (indented, smaller avatar). [onToggleReplies] != null adds the
 * "N replies" button that opens / closes the replies; [repliesOpen] turns its arrow around.
 */
@Composable
private fun CommentRow(
    c: Comment,
    reply: Boolean = false,
    repliesOpen: Boolean = false,
    onToggleReplies: (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().padding(start = if (reply) 56.dp else 20.dp, end = 20.dp, top = 9.dp, bottom = 9.dp)) {
        AppImage(c.avatar, Modifier.size(if (reply) 24.dp else 34.dp), CircleShape)
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
            if (c.likesText.isNotEmpty() && c.likesText != "0") {
                Text("${c.likesText} likes", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            if (onToggleReplies != null) {
                val n = c.replies
                Row(
                    Modifier
                        .padding(top = 4.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleReplies)
                        .padding(end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (repliesOpen) "Hide replies" else "Show replies",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp).rotate(if (repliesOpen) 180f else 0f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (n > 0) "$n ${if (n == 1) "reply" else "replies"}" else "Replies",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
