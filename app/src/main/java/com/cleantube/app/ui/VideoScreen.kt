package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.Api
import com.cleantube.app.data.ApiException
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Page
import com.cleantube.app.data.Paged
import com.cleantube.app.data.VideoDetails
import com.cleantube.app.data.VideoItem

// Colours of the video page: dark grey page, lighter grey cards and chips, light-blue "liked", red Subscribe.
private val VBg = Color(0xFF1E1E1E)
private val VCard = Color(0xFF2E2E2E)
private val VChip = Color(0xFF3B3B3B)
private val VBlue = Color(0xFF6BA5E7)
private val VRed = Color(0xFFE9524D)
private val VSoft = Color(0xFFD0D0D0)
private val VMuted = Color(0xFFA8A8A8)

/** Corner shape of every card, chip and button on the video page: square (0 % rounding). */
private val VShape = RectangleShape

/** Side margin of the player card, and of the cards / chips below it. */
private val PlayerMargin = 8.dp
private val PageMargin = 16.dp

private val unitLetters = Regex("(?<=\\d)([KMB])\\b")

/** "1.2M subscribers" -> "1.2m subscribers", "73K" -> "73k". */
private fun String.lowerUnits(): String = unitLetters.replace(this) { it.value.lowercase() }

@Composable
fun VideoScreen(route: Route.Video) {
    val nav = LocalNav.current
    val store = Graph.store
    val ctx = LocalContext.current
    val host = LocalFullscreen.current
    val scope = rememberCoroutineScope()

    var details by remember(route.id) { mutableStateOf<VideoDetails?>(null) }
    var channel by remember(route.id) { mutableStateOf<ChannelItem?>(null) }
    var error by remember(route.id) { mutableStateOf<ApiException?>(null) }
    var reload by remember(route.id) { mutableIntStateOf(0) }
    var descOpen by remember(route.id) { mutableStateOf(false) }
    var saveDialog by remember(route.id) { mutableStateOf(false) }
    var downloadDialog by remember(route.id) { mutableStateOf(false) }
    var commentsOpen by remember(route.id) { mutableStateOf(false) }
    var reaction by remember(route.id) { mutableIntStateOf(store.reaction(route.id)) }

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
    val listState = rememberLazyListState()

    // Related videos and the first comments load as soon as the video details (which carry their tokens) are in.
    LaunchedEffect(details) {
        if (details != null) {
            if (more.items.isEmpty()) more.loadMore()
            if (comments.items.isEmpty()) comments.loadMore()
        }
    }
    LoadMoreEffect(listState, more)

    // Leaving the page must also leave fullscreen.
    DisposableEffect(Unit) { onDispose { host.setImmersiveMode(false) } }
    val immersive = host.immersive

    BoxWithConstraints(Modifier.fillMaxSize().background(VBg)) {
        val playerHeight = (maxWidth - PlayerMargin * 2) * 9f / 16f

        if (!immersive) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // room for the player card, which is drawn on top of this column
                Spacer(Modifier.height(playerHeight + PlayerMargin * 2))

                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {

                    // ---- title (tap = description) + views / age
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = PageMargin).padding(top = 4.dp)) {
                            val chevron by animateFloatAsState(if (descOpen) 180f else 0f, label = "chevron")
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { descOpen = !descOpen },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    video?.title ?: "Loading…",
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = if (descOpen) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Description",
                                    tint = TextPrimary,
                                    modifier = Modifier.padding(start = 8.dp).size(30.dp).rotate(chevron)
                                )
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                video?.viewsText?.takeIf { it.isNotEmpty() }?.let {
                                    Text(it.lowerUnits(), color = VSoft, fontSize = 13.sp)
                                }
                                video?.ageText?.takeIf { it.isNotEmpty() }?.let {
                                    Text(it, color = VSoft, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // ---- description (opens with the chevron)
                    item {
                        val description = details?.description.orEmpty()
                        AnimatedVisibility(visible = descOpen && description.isNotBlank()) {
                            Text(
                                description,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PageMargin)
                                    .padding(top = 12.dp)
                                    .clip(VShape)
                                    .background(VCard)
                                    .padding(14.dp)
                            )
                        }
                    }

                    // ---- action chips (scroll sideways)
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = PageMargin),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ActionChip(
                                Icons.Rounded.ThumbUp,
                                details?.likesText.orEmpty().lowerUnits().ifEmpty { "Like" },
                                active = reaction == 1
                            ) {
                                reaction = if (reaction == 1) 0 else 1
                                store.setReaction(route.id, reaction)
                            }
                            ActionChip(Icons.Rounded.ThumbDown, "Dislike", active = reaction == -1) {
                                reaction = if (reaction == -1) 0 else -1
                                store.setReaction(route.id, reaction)
                            }
                            ActionChip(Icons.Rounded.Share, "Share") { shareVideo(ctx, route.id) }
                            // clips are made on YouTube itself
                            ActionChip(Icons.Rounded.ContentCut, "Clip") { openInYouTube(ctx, route.id) }
                            ActionChip(Icons.Rounded.Add, "Save") { if (video != null) saveDialog = true }
                            ActionChip(Icons.Rounded.Download, "Download") { if (video != null) downloadDialog = true }
                        }
                    }

                    // ---- channel card
                    if (video != null) {
                        item {
                            val base = channel ?: ChannelItem(video.channelId, video.channelTitle)
                            // fall back to what the video itself knows while / if the details have no owner
                            val ch = base.copy(
                                id = base.id.ifEmpty { video.channelId },
                                title = base.title.ifEmpty { video.channelTitle }
                            )
                            val subscribed = ch.id.isNotEmpty() && store.isSubscribed(ch.id)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PageMargin)
                                    .padding(top = 16.dp)
                                    .clip(VShape)
                                    .background(VCard)
                                    .clickable(enabled = ch.id.isNotEmpty()) { nav.push(Route.Channel(ch.id, ch)) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (ch.thumb.isNotEmpty()) {
                                    AppImage(ch.thumb, Modifier.size(46.dp), CircleShape)
                                } else {
                                    // no logo available (yet): round placeholder with the channel's first letter
                                    Box(
                                        Modifier.size(46.dp).clip(CircleShape).background(VChip),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            ch.title.trim().take(1).uppercase(),
                                            color = TextPrimary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        ch.title.ifEmpty { "Loading channel…" },
                                        color = TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (ch.subsText.isNotEmpty()) {
                                        Text(ch.subsText.lowerUnits(), color = VMuted, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (subscribed) "Subscribed" else "Subscribe",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(VShape)
                                        .background(if (subscribed) VChip else VRed)
                                        .clickable(enabled = ch.id.isNotEmpty()) { toggleSubscription(scope, ch) }
                                        .padding(horizontal = 22.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    // ---- comments card: first comment as a preview, tap for all of them
                    item {
                        val first = comments.items.firstOrNull()
                        val countText = details?.commentCountText.orEmpty()
                        val commentsError = comments.error
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PageMargin)
                                .padding(top = 16.dp)
                                .clip(VShape)
                                .background(VCard)
                                .clickable { commentsOpen = true }
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Comments", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                if (countText.isNotEmpty()) {
                                    Text("($countText)", color = VMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Rounded.UnfoldMore, contentDescription = "Open comments", tint = VSoft, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            when {
                                first != null -> Row(verticalAlignment = Alignment.Top) {
                                    AppImage(first.avatar, Modifier.size(30.dp), CircleShape)
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
                                commentsError != null ->
                                    Text(commentsError.message ?: "Comments aren't available.", color = VMuted, fontSize = 13.sp)
                                comments.finished -> Text("No comments yet.", color = VMuted, fontSize = 13.sp)
                                else -> Text("Loading comments…", color = VMuted, fontSize = 13.sp)
                            }
                        }
                    }

                    // ---- general error (video details failed)
                    val err = error
                    if (err != null && details == null) {
                        item { ErrorBox(err, onRetry = { reload++ }) }
                    }

                    // ---- up next
                    val vids = more.items.filter { it.id != route.id }
                    item { Spacer(Modifier.height(8.dp)) }
                    items(vids.size) { i ->
                        val v = vids[i]
                        VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) }, container = VCard, square = true)
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
            }
        }

        // Comments: tap on the comments card opens them in the whole area under the player (fixed, no dragging).
        // It is drawn before the player card, so the video stays visible and playing on top.
        if (commentsOpen && !immersive) {
            CommentsPanel(
                paged = comments,
                ready = true,
                countText = details?.commentCountText.orEmpty(),
                onOpenInYouTube = { openInYouTube(ctx, route.id) },
                onDismiss = { commentsOpen = false },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = playerHeight + PlayerMargin * 2)
                    .background(Card)
                    .navigationBarsPadding()
                    // swallow touches so nothing behind the panel scrolls or gets tapped
                    .pointerInput(Unit) {}
            )
        }

        // The player card sits on top: pinned under the status bar, or the whole screen in fullscreen.
        // It is always the same composable, so switching fullscreen on / off never restarts the video.
        VideoPlayerCard(
            videoId = route.id,
            immersive = immersive,
            onBack = { if (host.immersive) host.setImmersiveMode(false) else if (!nav.back()) nav.selectTab(Route.Home) },
            onToggleImmersive = { host.setImmersiveMode(!host.immersive) },
            onOpenInYouTube = { openInYouTube(ctx, route.id) },
            modifier = if (immersive) {
                Modifier.fillMaxSize()
            } else {
                Modifier.statusBarsPadding().padding(PlayerMargin).fillMaxWidth().aspectRatio(16f / 9f)
            }
        )
    }

    if (downloadDialog && video != null) {
        DownloadDialog(video) { downloadDialog = false }
    }
    if (saveDialog && video != null) {
        SaveToPlaylistDialog(video) { saveDialog = false }
    }
    // Back closes the comments panel first (before leaving the video page).
    BackHandler(enabled = commentsOpen && !immersive) { commentsOpen = false }
}

/** Pill-shaped chip with an icon and a label; [active] turns it light blue. */
@Composable
private fun ActionChip(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .height(40.dp)
            .clip(VShape)
            .background(if (active) VBlue else VChip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}
