package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.cleantube.app.data.Api
import com.cleantube.app.data.Paged
import com.cleantube.app.data.Playback
import com.cleantube.app.data.VideoItem
import kotlinx.coroutines.delay

private val shortTopics = listOf(
    "For you" to "", "Music" to "music", "Comedy" to "funny", "Gaming" to "gaming",
    "Sports" to "sports", "Food" to "food", "Tech" to "tech", "News" to "news"
)

/**
 * The Shorts tab: a native vertical feed. Each Short plays in CleanTube's own player; the next ones are
 * prepared in advance (links found, first seconds already loaded) so swiping feels instant.
 */
@Composable
fun ShortsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var topic by remember { mutableStateOf("") }
    var muted by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(true) }
    var downloadFor by remember { mutableStateOf<VideoItem?>(null) }
    var saveFor by remember { mutableStateOf<VideoItem?>(null) }

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

    Column(Modifier.fillMaxSize().background(Bg)) {
        TabTopBar("Shorts")
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            // new feed whenever the topic changes
            key(topic) {
                val feed = remember { Paged<VideoItem>(scope) { token -> Api.shorts(token, topic) } }
                LaunchedEffect(feed) { feed.refresh() }
                val items = remember(feed.items) { feed.items.distinctBy { it.id } }
                val itemsNow = rememberUpdatedState(items)
                val pager = rememberPagerState { itemsNow.value.size }

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
                    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
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
                    VerticalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 80.dp),
                        beyondViewportPageCount = 1,
                        key = { itemsNow.value.getOrNull(it)?.id ?: it }
                    ) { page ->
                        val v = itemsNow.value.getOrNull(page)
                        if (v != null) {
                            ShortPage(
                                v = v,
                                current = pager.currentPage == page && resumed,
                                muted = muted,
                                onMute = { muted = !muted },
                                onSave = { saveFor = v },
                                onDownload = { downloadFor = v },
                                onShare = { shareVideo(ctx, v.id) }
                            )
                        }
                    }
                }
            }

            // topic chips float over the top of the picture
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shortTopics.size) { i ->
                        val (label, value) = shortTopics[i]
                        Pill(label, onClick = { topic = value }, active = topic == value)
                    }
                }
            }
        }
    }

    downloadFor?.let { DownloadDialog(it) { downloadFor = null } }
    saveFor?.let { SaveToPlaylistDialog(it) { saveFor = null } }
}

@Composable
private fun ShortPage(
    v: VideoItem,
    current: Boolean,
    muted: Boolean,
    onMute: () -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    val ctx = LocalContext.current
    val p = remember(v.id) { CtPlayer(ctx, v.id, shorts = true, maxHeight = 720) }
    val currentNow = rememberUpdatedState(current)

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
    LaunchedEffect(muted) { p.setVolume(if (muted) 0f else 1f) }
    LaunchedEffect(p) {
        while (true) {
            p.tick()
            delay(if (currentNow.value) 250L else 1000L)
        }
    }

    val dur = p.duration.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val frac = if (dragging) dragFraction else (p.position.toFloat() / dur).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CtVideoSurface(p, Modifier.fillMaxSize())
        CtPoster(v.id, p, fit = true)

        // tap = pause / play
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { p.togglePlay() }) }
        )

        // stalled mid-way: the same spinner as everywhere
        if (p.buffering && p.firstFrame) CtSpinner(Modifier.align(Alignment.Center))

        AnimatedVisibility(
            visible = current && !p.wantPlay && p.firstFrame && p.error == null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        // bottom shade + text
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
        ) {
            Column(Modifier.padding(start = 16.dp, end = 80.dp, top = 28.dp, bottom = 38.dp)) {
                if (v.channelTitle.isNotEmpty()) {
                    Text(v.channelTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    v.title, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                )
                if (v.viewsText.isNotEmpty()) {
                    Text(v.viewsText, color = Color(0xCCFFFFFF), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // side buttons
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShortAction(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (muted) "Muted" else "Sound", onMute)
            ShortAction(Icons.Rounded.Add, "Save", onSave)
            ShortAction(Icons.Rounded.Download, "Download", onDownload)
            ShortAction(Icons.Rounded.Share, "Share", onShare)
        }

        // thin seek bar at the very bottom
        CtSeekBar(
            fraction = frac,
            buffered = (p.buffered.toFloat() / dur).coerceIn(0f, 1f),
            onDragStart = { dragging = true; dragFraction = frac },
            onDrag = { dragFraction = it },
            onDragEnd = { f -> p.seekTo((f * dur).toLong()); dragging = false },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp),
            showThumb = false
        )

        if (p.error != null) {
            CtErrorView(p.error.orEmpty(), onRetry = { p.retry() }, onOpen = { openInYouTube(ctx, v.id) })
        }
    }
}

@Composable
private fun ShortAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.bouncyClickable(pressedScale = 0.9f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
