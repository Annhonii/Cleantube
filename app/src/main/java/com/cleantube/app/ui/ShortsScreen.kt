@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cleantube.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.geometry.Offset as PullOffset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import com.cleantube.app.data.TabCache
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil3.compose.AsyncImage
import com.cleantube.app.data.Api
import com.cleantube.app.data.Comment
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Page
import com.cleantube.app.data.Paged
import androidx.compose.material.icons.rounded.Download
import com.cleantube.app.data.Playback
import com.cleantube.app.data.Session
import com.cleantube.app.data.VideoDetails
import com.cleantube.app.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/** Read by the app's Back handling: while a sheet / menu of the Shorts tab is open, Back closes that first. */
/** Room kept at the bottom of a Short for the floating tab pill. The pill is hidden on the Shorts tab, so none. */
val ShortsPillSpace = 0.dp

object ShortsUi {
    var overlayOpen by mutableStateOf(false)
}

private val shortTopics = listOf(
    "For you" to "", "Music" to "music", "Comedy" to "funny", "Gaming" to "gaming",
    "Sports" to "sports", "Food" to "food", "Tech" to "tech", "News" to "news"
)

private val HashtagBlue = Color(0xFF4DA6FF)
private val HeartRed = Color(0xFFFF3B5C)

/** What the screen-level menu needs to know about the Short that is on screen. */
@Stable
private class ActiveShort(val video: VideoItem, val player: CtPlayer, val details: VideoDetails?)

private class Burst(val id: Int, val origin: Offset)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private suspend fun safeDetails(id: String): VideoDetails? = try {
    Api.videoDetails(id)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/**
 * The Shorts tab: a full-screen native vertical feed (edge to edge) with its own header, side action bar,
 * channel / caption overlay and scrub bar. Each Short plays in CleanTube's own player; the next ones are prepared
 * in advance (links found, first seconds loaded) so swiping feels instant.
 */
@Composable
fun ShortsScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var topic by remember { mutableStateOf("") }
    var resumed by remember { mutableStateOf(true) }
    var downloadFor by remember { mutableStateOf<VideoItem?>(null) }
    var saveFor by remember { mutableStateOf<VideoItem?>(null) }
    var active by remember { mutableStateOf<ActiveShort?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<VideoItem?>(null) }
    var commentsCount by remember { mutableStateOf("") }

    // Back closes the comments / menu first
    SideEffect { ShortsUi.overlayOpen = menuOpen || commentsFor != null }
    DisposableEffect(Unit) { onDispose { ShortsUi.overlayOpen = false } }
    BackHandler(enabled = menuOpen || commentsFor != null) {
        if (commentsFor != null) commentsFor = null else menuOpen = false
    }

    // stop the sound when the app goes to the background
    DisposableEffect(Unit) {
        val owner = ctx as? LifecycleOwner
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) resumed = false
            if (e == Lifecycle.Event.ON_START) resumed = true
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    // the video is black: keep the status / navigation bar icons white while this tab is open
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = ctx.findActivity()?.window
        val c = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = c?.isAppearanceLightStatusBars ?: false
        val prevNav = c?.isAppearanceLightNavigationBars ?: false
        c?.isAppearanceLightStatusBars = false
        c?.isAppearanceLightNavigationBars = false
        onDispose {
            c?.isAppearanceLightStatusBars = prevStatus
            c?.isAppearanceLightNavigationBars = prevNav
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // a new feed whenever the topic changes
        key(topic) {
            val t = topic
            // kept between tab switches: the feed and the Short you were on stay as they were
            val feed = TabCache.paged<VideoItem>("shorts:$t") { token ->
                // Shorts that were already shown once never come back (the Shorts already in this feed stay as they are)
                val page = Api.shorts(token, t)
                Page(page.items.filter { !Graph.store.isShortSeen(it.id) }, page.next)
            }
            LaunchedEffect(feed) { if (feed.items.isEmpty() && !feed.loading) feed.refresh() }
            val items = remember(feed.items) { feed.items.distinctBy { it.id } }
            val itemsNow = rememberUpdatedState(items)
            val pager = rememberPagerState(initialPage = TabCache.shortsPage[t] ?: 0) { itemsNow.value.size }
            SideEffect { TabCache.shortsPage[t] = pager.currentPage }

            // endless feed: ask for more when the end is near (also covers pages that had no real Shorts)
            LaunchedEffect(items.size, pager.currentPage, feed.loading) {
                if (!feed.loading && !feed.finished && feed.error == null && items.size - pager.currentPage <= 4) {
                    feed.loadMore()
                }
            }
            // find the stream links of the next Shorts before the user gets there
            LaunchedEffect(items.size, pager.currentPage) {
                for (i in 0..2) items.getOrNull(pager.currentPage + i)?.let { Playback.prefetch(it.id) }
            }

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val err = feed.error
                    if (err != null && !feed.loading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(err.message ?: "Couldn't load Shorts.", color = Color(0xFFDDDDDD), fontSize = 13.sp)
                            Box(Modifier.padding(top = 12.dp)) { Pill("Retry", onClick = { feed.refresh() }) }
                        }
                    } else {
                        CtSpinner()
                    }
                }
            } else {
                // pull down on the first Short to refresh the feed
                val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
                var pull by remember { mutableFloatStateOf(0f) }
                val connection = remember(pager, feed) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: PullOffset, source: NestedScrollSource): PullOffset {
                            if (pull > 0f && available.y < 0f && source == NestedScrollSource.UserInput) {
                                val take = maxOf(available.y, -pull)
                                pull += take
                                return PullOffset(0f, take)
                            }
                            return PullOffset.Zero
                        }

                        override fun onPostScroll(consumed: PullOffset, available: PullOffset, source: NestedScrollSource): PullOffset {
                            if (source == NestedScrollSource.UserInput && available.y > 0f && pager.currentPage == 0 && !feed.refreshing) {
                                pull = (pull + available.y * 0.5f).coerceAtMost(thresholdPx * 1.6f)
                                return PullOffset(0f, available.y)
                            }
                            return PullOffset.Zero
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            if (pull >= thresholdPx) feed.pullRefresh()
                            pull = 0f
                            return Velocity.Zero
                        }
                    }
                }
                Box(Modifier.fillMaxSize().nestedScroll(connection)) {
                    VerticalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        key = { itemsNow.value.getOrNull(it)?.id ?: it }
                    ) { page ->
                        val v = itemsNow.value.getOrNull(page)
                        if (v != null) {
                            ShortPage(
                                v = v,
                                current = pager.currentPage == page && resumed && !menuOpen && commentsFor == null,
                                onActive = { active = it },
                                onMenu = { menuOpen = true },
                                onComments = { video, count -> commentsCount = count; commentsFor = video },
                                onSave = { saveFor = v },
                                onDownload = { downloadFor = v }
                            )
                        }
                    }
                    val shown by animateFloatAsState(if (feed.refreshing) thresholdPx else pull, label = "shortsPull")
                    if (shown > 1f || feed.refreshing) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 80.dp)
                                .graphicsLayer {
                                    translationY = shown * 0.6f
                                    alpha = (shown / thresholdPx).coerceIn(0f, 1f)
                                }
                        ) { CtSpinner(size = 40.dp) }
                    }
                }
            }
        }

        // long-press / three-dot menu
        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140))
        ) {
            ShortMenu(
                active = active,
                topic = topic,
                onTopic = { topic = it; menuOpen = false },
                onSave = { active?.let { saveFor = it.video } },
                onDownload = { active?.let { downloadFor = it.video } },
                onReport = { active?.let { openInYouTube(ctx, it.video.id) } },
                onClose = { menuOpen = false }
            )
        }

        // comments: a sheet over the lower 72% of the screen
        val cf = commentsFor
        val comments = remember(cf?.id) {
            cf?.let { v -> Paged<Comment>(scope) { t -> Api.comments(v.id, t) } }
        }
        LaunchedEffect(comments) { comments?.loadMore() }
        AnimatedVisibility(
            visible = cf != null && comments != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160))
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0x73000000))
                    .pointerInput(Unit) { detectTapGestures { commentsFor = null } }
            ) {
                AnimatedVisibility(
                    visible = true,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(260)) { it },
                    exit = slideOutVertically(tween(200)) { it }
                ) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(0.72f)) {
                        if (cf != null && comments != null) {
                            CommentsPanel(cf.id, comments, commentsCount) { commentsFor = null }
                        }
                    }
                }
            }
        }
    }

    downloadFor?.let { DownloadDialog(it) { downloadFor = null } }
    saveFor?.let { SaveToPlaylistDialog(it) { saveFor = null } }
}

// ------------------------------------------------------------------------------------------------ menu

@Composable
private fun ShortMenu(
    active: ActiveShort?,
    topic: String,
    onTopic: (String) -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit,
    onReport: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var showDesc by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Color(0x80000000))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xF2161616))
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape).background(Color(0xFF444444))
            )
            val p = active?.player
            if (p != null) {
                MenuLabel("Playback speed")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                        val label = if (s == 1f) "Normal" else if (s % 1f == 0f) "${s.toInt()}x" else "${s}x"
                        CtChip(label, p.speed == s) { p.changeSpeed(s) }
                    }
                }
                MenuLabel("Quality")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val auto = if (p.quality == 0 && p.currentHeight > 0) "Auto ${p.currentHeight}p" else "Auto"
                    CtChip(auto, p.quality == 0) { p.changeQuality(0) }
                    p.heights.reversed().forEach { h -> CtChip("${h}p", p.quality == h) { p.changeQuality(h) } }
                }
            }
            MenuLabel("Feed")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shortTopics.forEach { (label, value) -> CtChip(label, topic == value) { onTopic(value) } }
            }

            Spacer(Modifier.size(2.dp))
            MenuRow(Icons.Rounded.PlaylistAdd, "Save to playlist", "") { onSave(); onClose() }
            MenuRow(Icons.Rounded.Download, "Download", "Video or audio only") { onDownload(); onClose() }
            MenuRow(Icons.Rounded.ClosedCaption, "Captions", "Not available yet") {
                Toast.makeText(ctx, "Captions aren't available yet.", Toast.LENGTH_SHORT).show()
            }
            MenuRow(
                Icons.Rounded.Description, "Description",
                if (showDesc) "Hide" else "Show"
            ) { showDesc = !showDesc }
            if (showDesc) {
                val d = active?.details?.description.orEmpty().ifBlank { active?.video?.title.orEmpty() }
                Text(
                    captionText(d.take(1200)), color = Color(0xFFDDDDDD), fontSize = 13.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 40.dp, bottom = 4.dp)
                )
            }
            MenuRow(
                Icons.Rounded.MusicNote, "Audio source",
                "Original audio · ${active?.video?.channelTitle.orEmpty()}"
            ) { }
            MenuRow(Icons.Rounded.Flag, "Report", "Opens YouTube") { onReport(); onClose() }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(text, color = Color(0xFF9A9A9A), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bouncyClickable(pressedScale = 0.98f, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Color(0xFF9A9A9A), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ------------------------------------------------------------------------------------------------ one Short

private fun captionText(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    Regex("#[\\p{L}\\p{N}_]+").findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        withStyle(SpanStyle(color = HashtagBlue, fontWeight = FontWeight.Bold)) { append(m.value) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
private fun ShortPage(
    v: VideoItem,
    current: Boolean,
    onActive: (ActiveShort) -> Unit,
    onMenu: () -> Unit,
    onComments: (VideoItem, String) -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit
) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val p = remember(v.id) { CtPlayer(ctx, v.id, shorts = true, maxHeight = 720) }
    val currentNow = rememberUpdatedState(current)
    val menuNow = rememberUpdatedState(onMenu)

    var details by remember(v.id) { mutableStateOf<VideoDetails?>(null) }
    var rating by remember(v.id) { mutableIntStateOf(0) }
    var expanded by remember(v.id) { mutableStateOf(false) }
    var overflow by remember(v.id) { mutableStateOf(false) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val bursts = remember { mutableStateListOf<Burst>() }
    var burstId by remember { mutableIntStateOf(0) }
    var seekSide by remember { mutableIntStateOf(0) }
    var seekTick by remember { mutableIntStateOf(0) }
    var flashTick by remember { mutableIntStateOf(0) }
    var flashPlaying by remember { mutableStateOf(true) }
    var flashVisible by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // Prepared paused (first picture + a few seconds loaded), starts the moment it becomes the current Short.
    DisposableEffect(p) {
        p.setLooping(true)
        p.playWhen(false)
        p.load()
        onDispose { p.release() }
    }
    LaunchedEffect(current) {
        if (current) p.playWhen(true) else {
            p.playWhen(false)
            p.restart()
        }
    }
    LaunchedEffect(p) {
        while (true) {
            p.tick()
            delay(if (currentNow.value) 250L else 1000L)
        }
    }
    // channel, counts, description: only for the Short that is on screen
    LaunchedEffect(current) {
        if (current) Graph.store.markShortSeen(v.id)
    }
    LaunchedEffect(current) {
        if (current && details == null) details = safeDetails(v.id)
    }
    LaunchedEffect(current, details) {
        if (current) onActive(ActiveShort(v, p, details))
    }
    LaunchedEffect(flashTick) {
        if (flashTick > 0) {
            flashVisible = true
            delay(450)
            flashVisible = false
        }
    }

    fun burst(at: Offset) {
        burstId++
        bursts += Burst(burstId, at)
    }

    fun rate(target: Int) {
        val before = rating
        val next = if (before == target) 0 else target
        if (next == 1) burst(Offset(box.width * 0.88f, box.height * 0.5f))
        if (!Session.loggedIn) {
            Toast.makeText(ctx, "Sign in with your Google account (Settings) to like or dislike.", Toast.LENGTH_SHORT).show()
            return
        }
        rating = next
        scope.launch {
            try {
                Api.rate(v.id, when (next) { 1 -> "like"; -1 -> "dislike"; else -> "none" })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rating = before
                Toast.makeText(ctx, "Couldn't update your rating. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val ch = details?.channel
    // The feed doesn't always carry the title: fall back to the one from the watch page.
    val title = v.title.ifEmpty { details?.video?.title.orEmpty() }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { box = it }) {
        CtVideoSurface(p, Modifier.fillMaxSize())
        CtPoster(v.id, p, fit = true)

        // gestures: tap = play / pause, double tap on the edges = ∓5 s, double tap in the middle = like, long press = menu
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        p.togglePlay()
                        flashPlaying = p.wantPlay
                        flashTick++
                    },
                    onDoubleTap = { off ->
                        val w = size.width
                        when {
                            off.x < w * 0.35f -> { p.seekBy(-5_000L); seekSide = -1; seekTick++ }
                            off.x > w * 0.65f -> { p.seekBy(5_000L); seekSide = 1; seekTick++ }
                            else -> if (rating != 1) rate(1) else burst(off)
                        }
                    },
                    onLongPress = { menuNow.value() }
                )
            }
        )

        SeekRipple(seekSide, seekTick)

        // bottom shade behind the text
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.38f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB8000000))))
        )

        // stalled mid-way: the same spinner as everywhere
        if (p.buffering && p.firstFrame) CtSpinner(Modifier.align(Alignment.Center))

        AnimatedVisibility(
            visible = flashVisible && p.error == null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(80)) + scaleIn(initialScale = 0.7f),
            exit = fadeOut(tween(260)) + scaleOut(targetScale = 1.25f)
        ) {
            Box(Modifier.size(68.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Icon(
                    if (flashPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp)
                )
            }
        }

        // ---------------- bottom-left: channel, caption, audio
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(bottom = ShortsPillSpace)
                .padding(start = 14.dp, end = 88.dp, bottom = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(0x44FFFFFF))
                        .bouncyClickable(pressedScale = 0.92f) { ch?.let { nav.push(Route.Channel(it.id)) } }
                ) {
                    if (ch != null && ch.thumb.isNotEmpty()) {
                        AsyncImage(ch.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                val handle = ch?.handle?.takeIf { it.isNotEmpty() }
                    ?: v.channelTitle.ifEmpty { ch?.title.orEmpty() }
                Text(
                    handle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 10.dp)
                )
                if (ch != null) {
                    val subscribed = Graph.store.isSubscribed(ch.id)
                    Spacer(Modifier.width(10.dp))
                    SubscribePill(subscribed) { toggleSubscription(scope, ch) }
                }
            }

            val cap = remember(title) { captionText(title) }
            Text(
                cap, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp,
                maxLines = if (expanded) 12 else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
                modifier = Modifier.padding(top = 10.dp).pointerInput(Unit) { detectTapGestures { expanded = !expanded } }
            )
            if (expanded) {
                val d = details?.description.orEmpty().trim()
                if (d.isNotEmpty() && d != title) {
                    Text(captionText(d.take(500)), color = Color(0xE6FFFFFF), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (!expanded && overflow) {
                Text(
                    "…more", color = Color(0xCCFFFFFF), fontSize = 13.sp,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { expanded = true } }
                )
            }
        }

        // ---------------- right: action bar (no background, sits low so it covers less of the video)
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = ShortsPillSpace).padding(end = 8.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SideAction(Icons.Rounded.ThumbUp, details?.likesText?.ifEmpty { null } ?: "Like", tint = if (rating == 1) HeartRed else Color.White) { rate(1) }
            SideAction(Icons.Rounded.ChatBubbleOutline, details?.commentCountText?.ifEmpty { null } ?: "Comments") {
                scope.launch {
                    if (details == null) details = safeDetails(v.id)
                    onComments(v, details?.commentCountText.orEmpty())
                }
            }
            SideAction(Icons.Rounded.Share, "Share") { shareVideo(ctx, v.id) }
            SideAction(Icons.Rounded.MoreVert, "More", onClick = onMenu)
        }

        // ---------------- very bottom: slim progress / scrub bar
        // Only while paused / scrubbing. The position is read inside this block, so the rest of the page
        // doesn't recompose 4 times a second while the Short plays.
        if (current && (!p.wantPlay || dragging)) {
            val dur = p.duration.coerceAtLeast(1L)
            val frac = if (dragging) dragFraction else (p.position.toFloat() / dur).coerceIn(0f, 1f)
            CtSeekBar(
                fraction = frac,
                buffered = (p.buffered.toFloat() / dur).coerceIn(0f, 1f),
                onDragStart = { dragging = true; dragFraction = frac },
                onDrag = { dragFraction = it },
                onDragEnd = { f -> p.seekTo((f * dur).toLong()); dragging = false },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 4.dp).fillMaxWidth().padding(horizontal = 6.dp),
                showThumb = false
            )
        }

        HeartsLayer(bursts) { id -> bursts.removeAll { it.id == id } }

        if (p.error != null) {
            CtErrorView(p.error.orEmpty(), onRetry = { p.retry() }, onOpen = { openInYouTube(ctx, v.id) })
        }
    }
}

@Composable
private fun SideAction(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.width(56.dp).bouncyClickable(pressedScale = 0.88f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
        Text(
            label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), blurRadius = 6f)),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun SubscribePill(subscribed: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (subscribed) Color(0x4DFFFFFF) else Color.White, tween(220), label = "subBg")
    val fg by animateColorAsState(if (subscribed) Color.White else Color.Black, tween(220), label = "subFg")
    Row(
        Modifier.bouncyClickable(pressedScale = 0.92f, onClick = onClick).clip(CircleShape).background(bg)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (subscribed) {
            Icon(Icons.Rounded.NotificationsNone, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(if (subscribed) "Subscribed" else "Subscribe", color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Expanding glow on the left / right edge after a double tap, with "5 s". */
@Composable
private fun SeekRipple(side: Int, tick: Int) {
    if (side == 0) return
    key(tick) {
        val t = remember { Animatable(0f) }
        LaunchedEffect(Unit) { t.animateTo(1f, tween(600, easing = LinearOutSlowInEasing)) }
        val a = (1f - t.value).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize()) {
            val pos = if (side < 0) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                Modifier
                    .align(pos)
                    .padding(horizontal = 12.dp)
                    .size(170.dp)
                    .graphicsLayer { scaleX = 0.5f + 0.9f * t.value; scaleY = 0.5f + 0.9f * t.value; alpha = a }
                    .clip(CircleShape)
                    .background(Color(0x40FFFFFF))
            )
            Text(
                if (side < 0) "−5 s" else "+5 s", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(pos)
                    .padding(horizontal = 56.dp)
                    .graphicsLayer { alpha = a }
            )
        }
    }
}

/** Hearts that float up and fade (on like and on double tap in the middle). */
@Composable
private fun HeartsLayer(bursts: List<Burst>, onDone: (Int) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        bursts.forEach { b -> key(b.id) { HeartBatch(b, onDone) } }
    }
}

@Composable
private fun HeartBatch(b: Burst, onDone: (Int) -> Unit) {
    val progress = remember { Animatable(0f) }
    val seeds = remember { List(8) { Triple(Random.nextFloat() * 2f - 1f, 0.55f + Random.nextFloat() * 0.7f, 0.6f + Random.nextFloat() * 0.8f) } }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(950, easing = LinearOutSlowInEasing))
        onDone(b.id)
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    seeds.forEach { (dx, rise, sz) ->
        val sizeDp = (30f * sz).dp
        val sizePx = with(density) { sizeDp.toPx() }
        Icon(
            Icons.Rounded.Favorite, contentDescription = null, tint = HeartRed,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (b.origin.x + dx * 70.dp.toPx() * progress.value - sizePx / 2).roundToInt(),
                        (b.origin.y - rise * 190.dp.toPx() * progress.value - sizePx / 2).roundToInt()
                    )
                }
                .size(sizeDp)
                .graphicsLayer {
                    alpha = (1f - progress.value).coerceIn(0f, 1f)
                    val s = 0.6f + 0.7f * progress.value
                    scaleX = s
                    scaleY = s
                }
        )
    }
}
