package com.cleantube.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.cleantube.app.data.Api
import com.cleantube.app.data.Paged
import com.cleantube.app.data.VideoItem
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/** Topics shown as chips: label to extra search words. */
private val topics = listOf(
    "For you" to "",
    "Music" to "music",
    "Gaming" to "gaming",
    "Comedy" to "funny",
    "Tech" to "tech",
    "Sports" to "sports",
    "Food" to "food"
)

/** Full-screen vertical Shorts feed: swipe up for the next one, tap to pause. */
@Composable
fun ShortsScreen() {
    val scope = rememberCoroutineScope()
    var topic by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // A new list (and a new pager) for every topic.
        key(topic) {
            val paged = remember { Paged(scope) { token -> Api.shorts(token, topics[topic].second) } }
            ShortsPager(paged)
        }

        // topic chips over the top of the video
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
                .statusBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topics.forEachIndexed { i, t ->
                    Pill(t.first, onClick = { topic = i }, active = topic == i)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortsPager(paged: Paged<VideoItem>) {
    val pager = rememberPagerState(pageCount = { paged.items.size })

    LaunchedEffect(paged) { paged.loadMore() }
    // keep a few Shorts ahead of the viewer
    LaunchedEffect(pager.currentPage, paged.items.size, paged.loading) {
        if (paged.items.size - pager.currentPage <= 4 && paged.error == null) paged.loadMore()
    }

    Box(Modifier.fillMaxSize()) {
        if (paged.items.isEmpty()) {
            val err = paged.error
            when {
                err != null -> ErrorBox(err, onRetry = { paged.refresh() }, modifier = Modifier.align(Alignment.Center))
                paged.loading || !paged.finished -> Loading(Modifier.align(Alignment.Center))
                else -> EmptyState("No Shorts found right now. Try another topic.", Modifier.align(Alignment.Center))
            }
        } else {
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { paged.items.getOrNull(it)?.id ?: it }
            ) { page ->
                val v = paged.items.getOrNull(page)
                if (v != null) ShortPage(v, active = page == pager.settledPage)
            }
        }
    }
}

@Composable
private fun ShortPage(v: VideoItem, active: Boolean) {
    val ctx = LocalContext.current
    var paused by remember(v.id) { mutableStateOf(false) }
    var failed by remember(v.id) { mutableStateOf(false) }
    var downloadDialog by remember(v.id) { mutableStateOf(false) }
    var saveDialog by remember(v.id) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Thumbnail first: it is what you see while the player loads, and on neighbouring pages during the swipe.
        AppImage(v.thumb, Modifier.fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape)

        if (active && !failed) {
            ShortPlayer(v.id, paused, onError = { failed = true }, modifier = Modifier.fillMaxSize())
        }

        // Transparent layer above the player: tap = pause / play, swipes still reach the pager.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { paused = !paused }
        )

        if (paused && !failed) {
            Icon(
                Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(84.dp).clip(CircleShape).background(Color(0x66000000))
            )
        }

        // title + channel at the bottom left
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 84.dp, top = 40.dp, bottom = 96.dp)
            ) {
                if (v.channelTitle.isNotEmpty()) {
                    Text(v.channelTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(v.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (v.viewsText.isNotEmpty()) Text(v.viewsText, color = TextMuted, fontSize = 12.sp, maxLines = 1)
            }
        }

        // action rail on the right
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 8.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShortAction(Icons.Rounded.Download, "Download") { downloadDialog = true }
            ShortAction(Icons.Rounded.PlaylistAdd, "Save") { saveDialog = true }
            ShortAction(Icons.Rounded.Share, "Share") { shareVideo(ctx, v.id) }
            ShortAction(Icons.Rounded.OpenInBrowser, "YouTube") { openInYouTube(ctx, v.id) }
        }

        if (failed) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("This Short can't be played inside the app.", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                Pill("Open in YouTube", onClick = { openInYouTube(ctx, v.id) }, active = true)
            }
        }
    }

    if (downloadDialog) DownloadDialog(v) { downloadDialog = false }
    if (saveDialog) SaveToPlaylistDialog(v) { saveDialog = false }
}

@Composable
private fun ShortAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(0x66000000))
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

/** The official YouTube player, no controls, looping, filling the whole page. Only the visible page has one. */
@Composable
private fun ShortPlayer(videoId: String, paused: Boolean, onError: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    val onErrorNow by rememberUpdatedState(onError)
    val view = remember { YouTubePlayerView(ctx).apply { enableAutomaticInitialization = false } }

    DisposableEffect(view) {
        val owner = ctx as? LifecycleOwner
        owner?.lifecycle?.addObserver(view)
        val options = IFramePlayerOptions.Builder(ctx)
            .controls(0)
            .fullscreen(0)
            .build()
        view.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                player = youTubePlayer
            }

            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                // loop
                if (state == PlayerConstants.PlayerState.ENDED) {
                    youTubePlayer.seekTo(0f)
                    youTubePlayer.play()
                }
            }

            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                onErrorNow()
            }
        }, options)
        onDispose {
            owner?.lifecycle?.removeObserver(view)
            player = null
            view.release()
        }
    }

    LaunchedEffect(player, videoId) { player?.loadVideo(videoId, 0f) }
    LaunchedEffect(player, paused) {
        val p = player ?: return@LaunchedEffect
        if (paused) p.pause() else p.play()
    }

    AndroidView(factory = { view }, modifier = modifier)
}
