package com.cleantube.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.cleantube.app.data.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The video page as a bottom sheet (like the NewPipe mini player / YouTube):
 *  - a video opens full page; drag the picture down (or press Back, or the arrow) and it shrinks into a
 *    mini player card above the bottom bar while the video keeps playing
 *  - tap the card or drag it up to open the page again
 *  - swipe the card down (or press its X) and the video stops and the card goes away
 * The player lives here, outside of the screens, so it survives navigating around the app.
 */
object VideoHost {
    var videoId by mutableStateOf<String?>(null)
        private set
    var seed by mutableStateOf<VideoItem?>(null)
        private set
    var title by mutableStateOf("")
    var channel by mutableStateOf("")

    /** Set by the page while its comments panel is open (Back closes that first). */
    var commentsOpen by mutableStateOf(false)

    /** 1 = full page, 0 = mini player card, below 0 = the card is being swiped away. */
    val p = Animatable(0f)

    /** True while the page is (mostly) open; drives the Back button. */
    var expanded by mutableStateOf(false)
    var openTick by mutableIntStateOf(0)
    var collapseTick by mutableIntStateOf(0)
    var fresh = false

    fun open(id: String, s: VideoItem?) {
        fresh = videoId == null
        if (videoId != id) {
            title = s?.title.orEmpty()
            channel = s?.channelTitle.orEmpty()
        }
        videoId = id
        seed = s
        openTick++
    }

    fun requestCollapse() {
        collapseTick++
    }

    fun close() {
        videoId = null
        seed = null
        title = ""
        channel = ""
        expanded = false
    }
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

@Composable
fun VideoSheet(tabRoot: Boolean, hidden: Boolean) {
    val id = VideoHost.videoId ?: return
    key(id) { VideoSheetContent(id, tabRoot, hidden) }
}

@Composable
private fun VideoSheetContent(id: String, tabRoot: Boolean, hidden: Boolean) {
    val ctx = LocalContext.current
    val host = LocalFullscreen.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pA = VideoHost.p
    val player = remember(id) { CtPlayer(ctx, id, maxHeight = 480) }
    var fullscreen by remember(id) { mutableStateOf(false) }

    // ---- player plumbing (same as the old in-page player)
    DisposableEffect(player) {
        PlayerControl.second = 0f
        PlayerControl.owner = player
        PlayerControl.pause = { player.playWhen(false) }
        val owner = ctx as? LifecycleOwner
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP && !PipHost.active) player.playWhen(false)
        }
        owner?.lifecycle?.addObserver(obs)
        player.load()
        onDispose {
            if (PlayerControl.owner === player) {
                PlayerControl.owner = null
                PlayerControl.pause = null
            }
            owner?.lifecycle?.removeObserver(obs)
            if (fullscreen) host.setActive(false)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            player.tick()
            PlayerControl.second = player.position / 1000f
            delay(if (player.isPlaying) 250L else 500L)
        }
    }
    val view = LocalView.current
    SideEffect { view.keepScreenOn = player.isPlaying }
    DisposableEffect(view) { onDispose { view.keepScreenOn = false } }

    // the Shorts tab has its own sound: pause the video there
    LaunchedEffect(hidden) { if (hidden) player.playWhen(false) }

    // ---- open / collapse requests
    LaunchedEffect(VideoHost.openTick) {
        if (VideoHost.fresh) {
            VideoHost.fresh = false
            pA.snapTo(0f)
        }
        pA.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f))
    }
    val collapseStart = remember { VideoHost.collapseTick }
    LaunchedEffect(VideoHost.collapseTick) {
        if (VideoHost.collapseTick != collapseStart) pA.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 420f))
    }
    LaunchedEffect(Unit) { snapshotFlow { pA.value > 0.5f }.collect { VideoHost.expanded = it } }

    // ---- popup window (picture in picture): just the picture
    if (PipHost.active) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CtVideoSurface(player, Modifier.fillMaxSize(), active = true)
            CtPoster(id, player)
            if (player.buffering) CtSpinner(Modifier.align(Alignment.Center), disc = false)
        }
        return
    }
    if (hidden) return

    val route = remember(id) { Route.Video(id, VideoHost.seed) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
        val navPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val cardH = with(density) { 72.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val pad = with(density) { 4.dp.toPx() }
        val dockSpace by animateFloatAsState(
            with(density) { (if (tabRoot) 88.dp else 12.dp).toPx() }, tween(260), label = "miniDock"
        )
        val collapsedBottom = navPx + dockSpace
        val travel = (h - collapsedBottom - cardH).coerceAtLeast(1f)

        // full page: picture below the status bar, then the rest of the page
        val sideX = with(density) { 8.dp.toPx() }
        val vTop = statusPx + with(density) { 6.dp.toPx() }
        val vW = w - 2 * sideX
        val vH = vW * 9f / 16f
        val area = vTop + vH + with(density) { 6.dp.toPx() }
        val miniW = with(density) { 118.dp.toPx() }
        val miniH = cardH - 2 * pad

        val bodyOn by remember { derivedStateOf { pA.value > 0.02f } }
        val expandedLook by remember { derivedStateOf { pA.value > 0.97f } }
        val miniOn by remember { derivedStateOf { pA.value < 0.5f } }

        fun expand() {
            scope.launch { pA.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f)) }
        }

        fun dismiss() {
            scope.launch {
                pA.animateTo(-(cardH + collapsedBottom + 60f) / travel, tween(200))
                VideoHost.close()
            }
        }

        // drag the picture / the card: collapse, expand or swipe away
        var canDismiss by remember { mutableStateOf(false) }
        val dragState = rememberDraggableState { dy ->
            val low = if (canDismiss) -2f else 0f
            val next = (pA.value - dy / travel).coerceIn(low, 1f)
            scope.launch { pA.snapTo(next) }
        }
        val dragMod = Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStarted = {
                pA.stop()
                canDismiss = pA.value < 0.2f
            },
            onDragStopped = { v ->
                val cur = pA.value
                scope.launch {
                    when {
                        canDismiss && (v > 1100f || -cur * travel > cardH * 0.5f) -> dismiss()
                        v < -700f -> pA.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f))
                        v > 700f -> pA.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 420f))
                        cur > 0.5f -> pA.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f))
                        else -> pA.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 420f))
                    }
                }
            }
        )

        Box(
            Modifier
                .layout { measurable, constraints ->
                    val pv = pA.value
                    val pc = pv.coerceIn(0f, 1f)
                    val x = lerpF(margin, 0f, pc)
                    val sw = (w - 2 * x).roundToInt()
                    val sh = lerpF(cardH, h, pc).roundToInt()
                    val top = lerpF(h - collapsedBottom - cardH, 0f, pc) + (if (pv < 0f) -pv * travel else 0f)
                    val pl = measurable.measure(Constraints.fixed(sw, sh))
                    layout(constraints.maxWidth, constraints.maxHeight) { pl.place(x.roundToInt(), top.roundToInt()) }
                }
                .graphicsLayer {
                    val pv = pA.value
                    val pc = pv.coerceIn(0f, 1f)
                    shape = RoundedCornerShape(lerpF(22.dp.toPx(), 0f, pc))
                    clip = true
                    shadowElevation = (1f - pc) * 10.dp.toPx()
                    alpha = if (pv < 0f) (1f + pv * 1.5f).coerceIn(0f, 1f) else 1f
                }
                .drawBehind { drawRect(lerp(Card, Bg, pA.value.coerceIn(0f, 1f))) }
        ) {
            // ---- the rest of the page (title, buttons, comments, up next); fades in while opening
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        if (!bodyOn) {
                            layout(0, 0) {}
                        } else {
                            val pl = measurable.measure(Constraints.fixed(w.roundToInt(), (h - area).roundToInt().coerceAtLeast(0)))
                            layout(constraints.maxWidth, constraints.maxHeight) { pl.place(0, area.roundToInt()) }
                        }
                    }
                    .graphicsLayer { alpha = ((pA.value - 0.3f) / 0.7f).coerceIn(0f, 1f) }
            ) {
                VideoScreen(route)
            }

            // ---- the picture: grows from the little thumbnail of the card to the full-width player
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val pc = pA.value.coerceIn(0f, 1f)
                        val pw = lerpF(miniW, vW, pc).roundToInt()
                        val ph = lerpF(miniH, vH, pc).roundToInt()
                        val x = lerpF(pad, sideX, pc)
                        val y = lerpF(pad, vTop, pc)
                        val pl = measurable.measure(Constraints.fixed(pw, ph))
                        layout(constraints.maxWidth, constraints.maxHeight) { pl.place(x.roundToInt(), y.roundToInt()) }
                    }
                    .graphicsLayer {
                        val pc = pA.value.coerceIn(0f, 1f)
                        shape = RoundedCornerShape(lerpF(18.dp.toPx(), 26.dp.toPx(), pc))
                        clip = true
                    }
                    .then(dragMod)
                    .background(Color.Black)
            ) {
                CtVideoSurface(player, Modifier.fillMaxSize(), active = !fullscreen)
                CtPoster(id, player)
                val err = player.error
                when {
                    err != null -> if (expandedLook) CtErrorView(err, onRetry = { player.retry() }, onOpen = { openInYouTube(ctx, id) })
                    expandedLook -> CtOverlay(
                        player, fullscreen = false, title = VideoHost.title,
                        onFullscreen = { fullscreen = true; host.setActive(true) }
                    )
                    player.buffering -> CtSpinner(Modifier.align(Alignment.Center), size = 26.dp, disc = false)
                }
                if (expandedLook && err == null) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                            .clickable { VideoHost.requestCollapse() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimise", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // ---- the mini player card: title, play / pause and close; tap = open, swipe down = stop
            if (miniOn) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - pA.value * 2f).coerceIn(0f, 1f) }
                        .then(dragMod)
                        .clickable { expand() }
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(start = with(density) { (miniW + pad).toDp() } + 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                VideoHost.title.ifEmpty { "Loading…" }, color = TextPrimary, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (VideoHost.channel.isNotEmpty()) {
                                Text(VideoHost.channel, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable { player.togglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play / pause", tint = TextPrimary, modifier = Modifier.size(26.dp)
                            )
                        }
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable { dismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(22.dp))
                        }
                        Box(Modifier.width(6.dp))
                    }
                    val dur = player.duration.coerceAtLeast(1L)
                    CtProgressLine(
                        (player.position.toFloat() / dur).coerceIn(0f, 1f),
                        (player.buffered.toFloat() / dur).coerceIn(0f, 1f),
                        Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        if (fullscreen) {
            CtFullscreen(id, player, VideoHost.title) {
                fullscreen = false
                host.setActive(false)
            }
        }
    }
}
