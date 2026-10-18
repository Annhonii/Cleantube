@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.cleantube.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.cleantube.app.data.Api
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Paged
import com.cleantube.app.data.VideoDetails
import com.cleantube.app.data.VideoItem
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.CancellationException

/** Extra info the page needs that the feed itself doesn't carry (counts, avatar, handle, description). */
class ShortMeta(val details: VideoDetails?, val channel: ChannelItem?)

/** Which sheet to open: the quick one (long press) or the full one (three dots). */
class MenuRequest(val video: VideoItem, val full: Boolean)

internal suspend fun <T> tryOrNull(block: suspend () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

/**
 * State of the one YouTube player that plays the Short on screen.
 * The player is YouTube's official IFrame player (controls hidden); everything you see on top of it is drawn by the app.
 */
@Stable
class ShortsPlayer {
    var player by mutableStateOf<YouTubePlayer?>(null)
    var loadedId by mutableStateOf<String?>(null)

    /** True once the Short that was asked for is really playing (until then a thumbnail covers the player). */
    var started by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var position by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(0f)
    var speed by mutableStateOf(PlayerConstants.PlaybackRate.RATE_1)
    var failedId by mutableStateOf<String?>(null)

    val listener = object : AbstractYouTubePlayerListener() {
        override fun onReady(youTubePlayer: YouTubePlayer) {
            player = youTubePlayer
        }

        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
            when (state) {
                PlayerConstants.PlayerState.PLAYING -> {
                    playing = true
                    if (!started) {
                        started = true
                        youTubePlayer.setPlaybackRate(speed)
                    }
                }
                PlayerConstants.PlayerState.PAUSED -> playing = false
                // Shorts loop.
                PlayerConstants.PlayerState.ENDED -> {
                    playing = false
                    loadedId?.let { youTubePlayer.loadVideo(it, 0f) }
                }
                else -> {}
            }
        }

        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
            position = second
        }

        override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
            this@ShortsPlayer.duration = duration
        }

        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
            failedId = loadedId
        }
    }

    fun load(id: String, startSec: Float = 0f) {
        failedId = null
        started = false
        playing = false
        position = startSec
        duration = 0f
        loadedId = id
        player?.loadVideo(id, startSec)
    }

    fun togglePlay() {
        val p = player ?: return
        if (playing) p.pause() else p.play()
    }

    fun seekBy(deltaSec: Float) {
        val max = if (duration > 0f) duration else Float.MAX_VALUE
        seekTo((position + deltaSec).coerceIn(0f, max))
    }

    fun seekTo(sec: Float) {
        val p = player ?: return
        p.seekTo(sec)
        position = sec
    }

    fun setSpeedTo(rate: PlayerConstants.PlaybackRate) {
        speed = rate
        player?.setPlaybackRate(rate)
    }
}

/** Opens a Short in the YouTube app (where Remix, Report and the creation tools live), or the browser. */
fun openShortInYouTube(ctx: Context, videoId: String) {
    val uri = Uri.parse("https://www.youtube.com/shorts/$videoId")
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube"))
    } catch (e: ActivityNotFoundException) {
        openUrl(ctx, uri.toString())
    }
}

private fun openCast(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Open Quick Settings and tap Cast / Smart View", Toast.LENGTH_LONG).show()
    }
}

private fun channelOf(v: VideoItem, m: ShortMeta?): ChannelItem? {
    val c = m?.channel
    if (c != null && c.id.isNotEmpty()) return c
    return if (v.channelId.isNotEmpty()) ChannelItem(id = v.channelId, title = v.channelTitle) else null
}

/**
 * Full-screen vertical Shorts feed (9:16, edge to edge): swipe up / down for the next / previous Short, tap to pause,
 * double tap left / right to seek 5 s, double tap the middle to like, long press for the quick menu.
 */
@Composable
fun ShortsScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val store = Graph.store

    val feed = remember { Paged(scope) { token -> Api.shorts(token) } }
    LaunchedEffect(Unit) { feed.loadMore() }
    val shorts = remember(feed.items) { feed.items.distinctBy { it.id } }

    val pager = rememberPagerState(pageCount = { shorts.size })
    val active = if (shorts.isEmpty()) -1 else pager.settledPage.coerceIn(0, shorts.lastIndex)
    val activeVideo = shorts.getOrNull(active)

    var captionsOn by remember { mutableStateOf(false) }
    val sp = remember { ShortsPlayer() }
    val meta = remember { mutableStateMapOf<String, ShortMeta>() }
    val reactions = remember { mutableStateMapOf<String, Int>() }
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    var commentsFor by remember { mutableStateOf<VideoItem?>(null) }
    var downloadFor by remember { mutableStateOf<VideoItem?>(null) }
    var saveFor by remember { mutableStateOf<VideoItem?>(null) }

    // ---- the player (re-created when captions are switched, because YouTube only reads that flag at start-up)
    val playerView = remember(captionsOn) {
        YouTubePlayerView(ctx).apply { enableAutomaticInitialization = false }
    }
    DisposableEffect(playerView) {
        val owner = ctx as? LifecycleOwner
        owner?.lifecycle?.addObserver(playerView)
        val options = IFramePlayerOptions.Builder(ctx)
            .controls(0)
            .fullscreen(0)
            .ccLoadPolicy(if (captionsOn) 1 else 0)
            .build()
        playerView.initialize(sp.listener, options)
        onDispose {
            owner?.lifecycle?.removeObserver(playerView)
            sp.player = null
            playerView.release()
        }
    }

    // Play whatever Short has settled on screen.
    LaunchedEffect(sp.player, activeVideo?.id) {
        val id = activeVideo?.id
        if (sp.player != null && id != null) {
            sp.load(id, if (sp.loadedId == id) sp.position else 0f)
        }
    }

    // Keep the feed topped up.
    LaunchedEffect(active, shorts.size) {
        if (shorts.isNotEmpty() && active >= shorts.size - 3) feed.loadMore()
    }

    // Counts, avatar, handle and description for the Short on screen and the next one.
    LaunchedEffect(active, shorts.size) {
        if (active < 0) return@LaunchedEffect
        for (i in active..active + 1) {
            val v = shorts.getOrNull(i) ?: continue
            if (meta.containsKey(v.id)) continue
            val d = tryOrNull { Api.videoDetails(v.id) }
            val ch = d?.channel?.let { c -> tryOrNull { Api.channel(c.id) } ?: c }
            meta[v.id] = ShortMeta(d, ch)
        }
    }

    fun reactionOf(id: String): Int = reactions[id] ?: store.reaction(id)
    fun setReaction(id: String, value: Int) {
        reactions[id] = value
        store.setReaction(id, value)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screenW = maxWidth
        val screenH = maxHeight

        // ---- layer 1: the live video, cropped to fill the screen (letterboxed for non-vertical clips)
        if (activeVideo != null) {
            val cover = activeVideo.short
            val frameW = if (cover) screenH * 16f / 9f else screenW
            val frameH = if (cover) screenH else screenW * 9f / 16f
            Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                key(playerView) {
                    AndroidView(factory = { playerView }, modifier = Modifier.requiredSize(frameW, frameH))
                }
            }
        }

        // ---- layer 2: the pages (thumbnail while loading / swiping, gestures and all the overlays)
        if (shorts.isNotEmpty()) {
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
                key = { shorts[it].id }
            ) { page ->
                val v = shorts[page]
                val isActive = page == active
                val live = isActive && sp.started && !pager.isScrollInProgress && sp.failedId != v.id
                val m = meta[v.id]
                ShortsPage(
                    video = v,
                    meta = m,
                    isActive = isActive,
                    live = live,
                    player = sp,
                    reaction = reactionOf(v.id),
                    onReact = { setReaction(v.id, it) },
                    onComments = { commentsFor = v },
                    onMenu = { full -> menu = MenuRequest(v, full) },
                    onShare = { shareText(ctx, "https://youtube.com/shorts/${v.id}") },
                    onRemix = { openShortInYouTube(ctx, v.id) },
                    onOpenChannel = {
                        val c = channelOf(v, m)
                        if (c != null) nav.push(Route.Channel(c.id, c))
                    },
                    onSubscribe = {
                        val c = channelOf(v, m)
                        if (c != null) toggleSubscription(scope, c)
                    },
                    onRetry = { sp.load(v.id) },
                    onOpenInYouTube = { openShortInYouTube(ctx, v.id) }
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val err = feed.error
                if (err != null) ErrorBox(err, onRetry = { feed.refresh() }) else Loading()
            }
        }

        // ---- layer 3: top header, floating below the status bar
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
        ) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (!nav.back()) nav.selectTab(Route.Home) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Icon(Icons.Rounded.SmartDisplay, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
                Text(
                    "Shorts",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NType,
                    modifier = Modifier.padding(start = 6.dp)
                )
                Box(Modifier.weight(1f))
                IconButton(onClick = { nav.push(Route.Search()) }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Color.White)
                }
                IconButton(onClick = { openCast(ctx) }) {
                    Icon(Icons.Rounded.Cast, contentDescription = "Cast", tint = Color.White)
                }
                IconButton(onClick = { activeVideo?.let { menu = MenuRequest(it, true) } }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Color.White)
                }
            }
        }

        // ---- layer 4: slim progress / scrub bar on the very bottom edge
        if (activeVideo != null) {
            ShortsProgressBar(
                progress = { if (sp.duration > 0f) sp.position / sp.duration else 0f },
                onSeek = { frac -> if (sp.duration > 0f) sp.seekTo(frac * sp.duration) },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            )
        }
    }

    // ---- sheets and dialogs
    val menuReq = menu
    if (menuReq != null) {
        ShortsMenuSheet(
            req = menuReq,
            meta = meta[menuReq.video.id],
            player = sp,
            captionsOn = captionsOn,
            onCaptions = { captionsOn = it },
            onDownload = { downloadFor = menuReq.video },
            onSave = { saveFor = menuReq.video },
            onOpenChannel = {
                channelOf(menuReq.video, meta[menuReq.video.id])?.let { nav.push(Route.Channel(it.id, it)) }
            },
            onDismiss = { menu = null }
        )
    }

    val cv = commentsFor
    if (cv != null) {
        ShortsCommentsSheet(
            video = cv,
            hasDetails = meta[cv.id]?.details != null,
            countText = meta[cv.id]?.details?.commentCountText.orEmpty(),
            onOpenInYouTube = { openShortInYouTube(ctx, cv.id) },
            onDismiss = { commentsFor = null }
        )
    }

    val dv = downloadFor
    if (dv != null) DownloadDialog(dv) { downloadFor = null }

    val sv = saveFor
    if (sv != null) SaveToPlaylistDialog(sv) { saveFor = null }
}
