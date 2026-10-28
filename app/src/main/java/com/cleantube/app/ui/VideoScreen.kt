package com.cleantube.app.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
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
    // 1 = liked, -1 = disliked, 0 = no rating. Starts from what YouTube says about this account once the page is loaded.
    var rating by remember(route.id) { mutableStateOf(0) }
    var ratingTouched by remember(route.id) { mutableStateOf(false) }

    val video = details?.video ?: route.seed

    fun rate(target: Int) {
        if (!Session.loggedIn) {
            Toast.makeText(ctx, "Sign in with your Google account (Settings) to like or dislike.", Toast.LENGTH_SHORT).show()
            return
        }
        val before = rating
        val next = if (before == target) 0 else target
        ratingTouched = true
        rating = next
        scope.launch {
            try {
                Api.rate(route.id, when (next) { 1 -> "like"; -1 -> "dislike"; else -> "none" })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rating = before
                Toast.makeText(ctx, "Couldn't update your rating: ${e.message ?: "try again"}", Toast.LENGTH_LONG).show()
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
    // the recommendations learn from it: opened = a little, still watching after 30 s = a lot
    LaunchedEffect(route.id) {
        route.seed?.let { com.cleantube.app.data.Reco.onOpen(it) }
        launch { kotlinx.coroutines.delay(4000); Api.markWatched(route.id) }   // YouTube learns from it too
        kotlinx.coroutines.delay(30_000)
        com.cleantube.app.data.Reco.onEngaged(route.id)
    }

    LaunchedEffect(route.id, reload) {
        error = null
        try {
            val d = Api.videoDetails(route.id)
            details = d
            if (!ratingTouched) rating = when (d.likeStatus) { "LIKE" -> 1; "DISLIKE" -> -1; else -> 0 }
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
    // the player's "next" button plays the next video of the playlist being played, otherwise the first Up next video
    LaunchedEffect(details) {
        VideoHost.nextItem = VideoHost.queueNext(route.id)
            ?: details?.related?.firstOrNull { it.id != route.id && !it.isShortLike() }
    }
    val comments = remember(route.id) {
        Paged(scope) { token -> Api.comments(route.id, token) }
    }
    val listState = rememberLazyListState()
    // which parts of this page already played their entrance (so scrolling back doesn't replay it)
    val seen = remember(route.id) { HashSet<Any>() }

    LaunchedEffect(details) {
        if (details != null && more.items.isEmpty()) more.loadMore()
        // loaded up front: the comments card shows the first one as a preview
        if (details != null && comments.items.isEmpty()) comments.loadMore()
    }
    LoadMoreEffect(listState, more)

    // Back closes the comments panel first, only then the video page.
    BackHandler(enabled = commentsOpen) { commentsOpen = false }
    BackHandler(enabled = descOpen) { descOpen = false }
    BackHandler(enabled = PlayerSettingsHost.open) { PlayerSettingsHost.open = false }
    androidx.compose.runtime.LaunchedEffect(route.id) { PlayerSettingsHost.open = false }

    // the picture itself is drawn by VideoSheet (above this page); what is left here is everything below it
    LaunchedEffect(video?.title, video?.channelTitle) {
        video?.let {
            if (it.title.isNotEmpty()) VideoHost.title = it.title
            if (it.channelTitle.isNotEmpty()) VideoHost.channel = it.channelTitle
        }
    }
    LaunchedEffect(commentsOpen, descOpen, PlayerSettingsHost.open) {
        VideoHost.commentsOpen = commentsOpen || descOpen || PlayerSettingsHost.open
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { VideoHost.commentsOpen = false } }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {

                // ---- title (tap to open the description panel) + stats
                item {
                    Column(
                        Modifier
                            .entrance(seen, "title", 120)
                            .fillMaxWidth()
                            .clickable { descOpen = true }
                            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                video?.title ?: "Loading…",
                                color = TextPrimary,
                                fontSize = 19.sp,
                                lineHeight = 25.sp,
                                letterSpacing = (-0.2).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Show description",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
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

                // ---- actions: like / dislike capsule, then round icon tiles
                item {
                    Row(
                        Modifier
                            .entrance(seen, "actions", 200)
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val likes = details?.likesText.orEmpty()
                        Row(
                            Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Card)
                                .border(1.dp, Hairline, RoundedCornerShape(12.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                Modifier
                                    .fillMaxHeight()
                                    .background(if (rating == 1) Accent else Color.Transparent)
                                    .bouncyClickable(pressedScale = 0.92f) { rate(1) }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.ThumbUp, contentDescription = "Like", tint = if (rating == 1) OnAccent else TextPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(likes.ifEmpty { "Like" }, color = if (rating == 1) OnAccent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            Box(Modifier.width(1.dp).height(20.dp).background(Hairline))
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .background(if (rating == -1) TextPrimary else Color.Transparent)
                                    .bouncyClickable(pressedScale = 0.92f) { rate(-1) }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.ThumbDown, contentDescription = "Dislike", tint = if (rating == -1) Bg else TextPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                        ActionTile("Share", Icons.Rounded.Share, onClick = { shareVideo(ctx, route.id) })
                        ActionTile("Save", Icons.Rounded.Add, onClick = { if (video != null) saveDialog = true })
                        ActionTile("Download", Icons.Rounded.Download, onClick = { if (video != null) downloadDialog = true })
                        val bgOn = BackgroundPlay.activeId == route.id
                        ActionTile("Audio", Icons.Rounded.Headphones, onClick = { if (video != null) toggleBackground() }, active = bgOn)
                        ActionTile("Popup", Icons.Rounded.PictureInPictureAlt, onClick = { PipHost.enter?.invoke() })
                    }
                }

                // ---- channel card
                if (video != null && video.channelId.isNotEmpty()) {
                    item {
                        val ch = channel ?: ChannelItem(video.channelId, video.channelTitle)
                        val subscribed = store.isSubscribed(ch.id)
                        val subBg by animateColorAsState(if (subscribed) SecondaryContainer else Accent, tween(280), label = "subBg")
                        val subFg by animateColorAsState(if (subscribed) TextPrimary else OnAccent, tween(280), label = "subFg")
                        Row(
                            Modifier
                                .entrance(seen, "channel", 280)
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .bouncyClickable(pressedScale = 0.97f) { nav.push(Route.Channel(ch.id, ch)) }
                                .clip(RoundedCornerShape(16.dp))
                                .background(Card)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppImage(ch.thumb, Modifier.size(42.dp), CircleShape)
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
                            if (subscribed) {
                                SubscribedPill(ch, compact = true, bg = SecondaryContainer)
                            } else {
                                Text(
                                    "Subscribe",
                                    color = subFg,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .bouncyClickable(pressedScale = 0.88f) { toggleSubscription(scope, ch) }
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(subBg)
                                        .padding(horizontal = 16.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }

                // ---- comments card (first comment as a preview, tap to open the comments panel)
                item {
                    val count = details?.commentCountText.orEmpty()
                    val first = comments.items.firstOrNull()
                    Column(
                        Modifier
                            .entrance(seen, "comments", 360)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .bouncyClickable(pressedScale = 0.975f) { commentsOpen = true }
                            .clip(RoundedCornerShape(16.dp))
                            .background(Card)
                            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
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
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                // big cards like the home feed; no Shorts in here (they have their own tab)
                val vids = more.items.filter { it.id != route.id && !it.isShortLike() }
                item { PrefetchTop(vids) }
                items(vids.size) { i ->
                    val v = vids[i]
                    Box(Modifier.entrance(seen, "up" + v.id, if (i < 4) 420 + i * 70 else 0)) {
                        VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
                    }
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
                enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 320f)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(300, easing = Motion.EmphasizedAccelerate)) { it } + fadeOut(tween(220))
            ) {
                CommentsPanel(
                    videoId = route.id,
                    comments = comments,
                    count = details?.commentCountText.orEmpty(),
                    onClose = { commentsOpen = false }
                )
            }

            // ---- playback settings (speed, quality, audio, captions): slides up under the player like the comments
            androidx.compose.animation.AnimatedVisibility(
                visible = PlayerSettingsHost.open,
                enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 320f)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(300, easing = Motion.EmphasizedAccelerate)) { it } + fadeOut(tween(220))
            ) {
                val player = PlayerControl.owner as? CtPlayer
                if (player != null) {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { PlayerSettingsHost.open = false } }) {
                        PlayerSettingsSheet(
                            player, onClose = { PlayerSettingsHost.open = false },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }

            // ---- description panel: slides up the same way, as a card under the player
            androidx.compose.animation.AnimatedVisibility(
                visible = descOpen,
                enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 320f)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(300, easing = Motion.EmphasizedAccelerate)) { it } + fadeOut(tween(220))
            ) {
                DescriptionPanel(
                    title = video?.title.orEmpty(),
                    meta = listOfNotNull(
                        video?.viewsText?.takeIf { it.isNotEmpty() },
                        video?.ageText?.takeIf { it.isNotEmpty() }
                    ).joinToString(" • "),
                    description = details?.description.orEmpty(),
                    onClose = { descOpen = false }
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
 * The video description in a card that slides up under the player, exactly like [CommentsPanel]: drag the header
 * (or pull the text down while it is at the top) to close it.
 */
@Composable
fun DescriptionPanel(title: String, meta: String, description: String, onClose: () -> Unit) {
    val scroll = rememberScrollState()

    // ---- drag down to close (same behaviour as the comments panel)
    var dragY by remember { mutableFloatStateOf(0f) }
    val closeAt = with(LocalDensity.current) { 120.dp.toPx() }
    suspend fun settle(velocity: Float) {
        if (dragY > closeAt || velocity > 1800f) onClose()
        else animate(dragY, 0f) { v, _ -> dragY = v }
    }
    val headerDrag = rememberDraggableState { d -> dragY = (dragY + d).coerceAtLeast(0f) }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && dragY > 0f) {
                    val c = maxOf(available.y, -dragY)
                    dragY += c
                    return Offset(0f, c)
                }
                return Offset.Zero
            }

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

    Box(Modifier.fillMaxSize().offset { IntOffset(0, dragY.roundToInt()) }) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
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
                    Text("Description", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close description", tint = TextPrimary)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

            Column(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nested)
                    .verticalScroll(scroll)
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp)
            ) {
                Text(
                    title, color = TextPrimary, fontSize = 17.sp, lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (meta.isNotEmpty()) {
                    Text(meta, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(Hairline))
                Text(
                    description.ifBlank { "No description." },
                    color = if (description.isBlank()) TextMuted else TextPrimary,
                    fontSize = 14.sp, lineHeight = 21.sp
                )
            }
        }
    }
}

/**
 * Full comments list in a rounded sheet that fills the area under the player. Loads more while scrolling.
 * Drag the sheet (header, or pull the list down while it is at the top) to close it; a floating
 * "Add a comment" pill sits at the bottom.
 */
@Composable
fun CommentsPanel(
    videoId: String,
    comments: Paged<Comment>,
    count: String,
    onClose: () -> Unit,
    /** One page of the replies under a comment: (page token or null for the first page, the comment's replies token). */
    loadReplies: suspend (String?, String) -> com.cleantube.app.data.Page<Comment> = { t, tok -> Api.comments(videoId, t ?: tok) },
    /** Sends a new top-level comment (videos by default; community posts pass their own). */
    addComment: suspend (String) -> Unit = { Api.postComment(videoId, it) },
    /** Optional content above the first comment (a post shows what it is about). */
    header: (@Composable () -> Unit)? = null
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    LoadMoreEffect(state, comments)
    // opened replies, by the comment's replies token: each one is its own paged list
    val expanded = remember { mutableStateMapOf<String, Paged<Comment>>() }
    // replies that are folding away (kept in the list until their exit animation is done)
    val closing = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    // writing a reply: the comment it answers (and which opened list to add it to)
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    var replyToken by remember { mutableStateOf("") }
    var replyDraft by remember { mutableStateOf("") }
    var replySending by remember { mutableStateOf(false) }
    fun startReply(c: Comment, listToken: String = "") {
        if (!Session.loggedIn) {
            Toast.makeText(ctx, "Sign in to reply.", Toast.LENGTH_SHORT).show()
        } else if (c.replyParams.isEmpty()) {
            val dbg = Api.replyDebug
            if (dbg.isNotEmpty()) {
                try {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("voidstream reply debug", dbg))
                } catch (e: Exception) { /* ignore */ }
            }
            Toast.makeText(
                ctx,
                if (dbg.isNotEmpty()) "Reply isn't available yet. Debug info copied: paste it to Claude so it can be fixed."
                else "YouTube didn't allow replying to this comment from here.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            replyDraft = ""
            replyToken = listToken
            replyTo = c
        }
    }

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
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
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
                if (header != null) item { header() }
                val list = comments.items
                list.forEach { c ->
                    val token = c.repliesToken
                    val rp = if (token.isNotEmpty()) expanded[token] else null
                    item {
                        CommentRow(
                            c,
                            repliesOpen = rp != null && token !in closing,
                            onReply = { startReply(c, token) },
                            onToggleReplies = if (token.isNotEmpty()) {
                                {
                                    if (rp != null) {
                                        if (token !in closing) {
                                            closing.add(token)
                                            scope.launch {
                                                kotlinx.coroutines.delay(280)
                                                expanded.remove(token)
                                                closing.remove(token)
                                            }
                                        }
                                    } else {
                                        // first page uses the thread's token, later pages the one the previous page returned
                                        val p = Paged<Comment>(scope) { t -> loadReplies(t, token) }
                                        expanded[token] = p
                                        p.loadMore()
                                    }
                                }
                            } else null
                        )
                    }
                    if (rp != null) {
                        val replies = rp.items
                        items(replies.size) { i ->
                            ReplyReveal(shown = token !in closing, index = i) {
                                CommentRow(replies[i], reply = true, onReply = { startReply(replies[i], token) })
                            }
                        }
                        item {
                            ReplyReveal(shown = token !in closing, index = replies.size.coerceAtMost(6)) {
                                RepliesFooter(rp, onHide = {
                                    if (token !in closing) {
                                        closing.add(token)
                                        scope.launch {
                                            kotlinx.coroutines.delay(280)
                                            expanded.remove(token)
                                            closing.remove(token)
                                        }
                                    }
                                })
                            }
                        }
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

    val target = replyTo
    if (target != null) {
        AlertDialog(
            onDismissRequest = { if (!replySending) replyTo = null },
            containerColor = Card,
            title = { Text("Reply to ${target.author}") },
            text = { PillTextField(replyDraft, { replyDraft = it }, "Add a reply…", Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(
                    enabled = replyDraft.isNotBlank() && !replySending,
                    onClick = {
                        replySending = true
                        val sent = replyDraft.trim()
                        scope.launch {
                            try {
                                Api.postReply(target.replyParams, sent)
                                Toast.makeText(ctx, "Reply posted.", Toast.LENGTH_SHORT).show()
                                // shown right away at the top of that comment's opened replies
                                expanded[replyToken]?.prepend(
                                    Comment(
                                        author = Session.name.ifEmpty { "You" },
                                        avatar = Session.thumb,
                                        text = sent,
                                        likes = 0,
                                        publishedAt = "just now",
                                        replies = 0
                                    )
                                )
                                replyTo = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Couldn't reply: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                            } finally {
                                replySending = false
                            }
                        }
                    }
                ) { Text(if (replySending) "Posting…" else "Reply", color = TextPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { if (!replySending) replyTo = null }) { Text("Cancel", color = TextMuted) }
            }
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
                        val sentText = draft.trim()
                        scope.launch {
                            try {
                                addComment(sentText)
                                Toast.makeText(ctx, "Comment posted.", Toast.LENGTH_SHORT).show()
                                comments.prepend(
                                    Comment(
                                        author = Session.name.ifEmpty { "You" },
                                        avatar = Session.thumb,
                                        text = sentText,
                                        likes = 0,
                                        publishedAt = "just now",
                                        replies = 0
                                    )
                                )
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
    val bg by animateColorAsState(container, tween(260), label = "chipBg")
    val fg by animateColorAsState(content, tween(260), label = "chipFg")
    // the icon pops (squash, then a bouncy return) whenever the chip switches on or off, e.g. a like
    val pop = remember { Animatable(1f) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(container) {
        if (firstRun) {
            firstRun = false
        } else {
            pop.snapTo(0.55f)
            pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 380f))
        }
    }
    Row(
        modifier = Modifier
            .then(if (onClick != null) Modifier.bouncyClickable(pressedScale = 0.9f, onClick = onClick) else Modifier)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
    onReply: (() -> Unit)? = null,
    onToggleReplies: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val voteScope = rememberCoroutineScope()
    // 1 = liked, -1 = disliked, 0 = nothing (this phone's view of it; YouTube holds the real one)
    var vote by remember(c) { mutableIntStateOf(0) }
    fun doVote(target: Int) {
        if (!Session.loggedIn) {
            Toast.makeText(ctx, "Sign in to like or dislike comments.", Toast.LENGTH_SHORT).show()
            return
        }
        val action = when {
            target == 1 && vote == 1 -> c.unlikeAction
            target == 1 -> c.likeAction
            target == -1 && vote == -1 -> c.undislikeAction
            else -> c.dislikeAction
        }
        if (action.isEmpty()) {
            val dbg = Api.replyDebug
            if (dbg.isNotEmpty()) {
                try {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("voidstream comment debug", dbg))
                } catch (e: Exception) { /* ignore */ }
            }
            Toast.makeText(
                ctx,
                if (dbg.isNotEmpty()) "Comment likes aren't available yet. Debug info copied: paste it to Claude." else "YouTube didn't allow this from here.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val before = vote
        vote = if (before == target) 0 else target
        voteScope.launch {
            try {
                Api.commentAction(action)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                vote = before
                Toast.makeText(ctx, "Couldn't update: ${e.message ?: "try again"}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val arrow by androidx.compose.animation.core.animateFloatAsState(
        if (repliesOpen) 180f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "replyArrow"
    )
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
            TimestampText(c.text, Modifier.padding(top = 2.dp))
            run {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable { doVote(1) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ThumbUp, "Like", tint = if (vote == 1) Accent else TextMuted, modifier = Modifier.size(16.dp))
                    }
                    if (c.likesText.isNotEmpty() && c.likesText != "0") {
                        Text(c.likesText, color = if (vote == 1) Accent else TextMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable { doVote(-1) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ThumbDown, "Dislike", tint = if (vote == -1) Accent else TextMuted, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    if (onReply != null) {
                        Text(
                            "Reply", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(CircleShape).clickable(onClick = onReply).padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
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
                        modifier = Modifier.size(18.dp).rotate(arrow)
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

/** A Short: flagged by YouTube, or a clip of a minute or so (the same rule the Shorts tab uses). */
private fun VideoItem.isShortLike(): Boolean = short || durationSec in 1..62


/** One piece of the replies list that slides open (and folds shut) like YouTube's: a little later for each row. */
@Composable
private fun ReplyReveal(shown: Boolean, index: Int, content: @Composable () -> Unit) {
    val state = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    LaunchedEffect(shown) {
        if (shown) kotlinx.coroutines.delay((index.coerceAtMost(6) * 45).toLong())
        state.targetState = shown
    }
    androidx.compose.animation.AnimatedVisibility(
        visibleState = state,
        enter = androidx.compose.animation.expandVertically(
            androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f),
            expandFrom = Alignment.Top
        ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f)) { -it / 3 },
        exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(240), shrinkTowards = Alignment.Top) +
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
    ) { content() }
}

private val TimestampRegex = Regex("""(?<![\d:])(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?![\d:])""")

/** Comment text where a time like 1:23 or 1:02:03 is a link: tapping it jumps the video to that moment. */
@Composable
private fun TimestampText(text: String, modifier: Modifier = Modifier) {
    val link = Accent
    val annotated = remember(text, link) {
        androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            TimestampRegex.findAll(text).forEach { m ->
                val h = m.groupValues[1].toIntOrNull() ?: 0
                val min = m.groupValues[2].toInt()
                val sec = m.groupValues[3].toInt()
                if (sec < 60 && (m.groupValues[1].isEmpty() || min < 60)) {
                    val total = h * 3600 + min * 60 + sec
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(color = link, fontWeight = FontWeight.Medium),
                        m.range.first, m.range.last + 1
                    )
                    addStringAnnotation("ts", total.toString(), m.range.first, m.range.last + 1)
                }
            }
        }
    }
    androidx.compose.foundation.text.ClickableText(
        text = annotated,
        modifier = modifier,
        style = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = AppFont),
        onClick = { off ->
            annotated.getStringAnnotations("ts", off, off).firstOrNull()?.let { a ->
                val p = PlayerControl.owner as? CtPlayer
                if (p != null) {
                    p.seekTo(a.item.toLong() * 1000L)
                    p.playWhen(true)
                }
            }
        }
    )
}
