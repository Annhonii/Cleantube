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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import com.cleantube.app.data.Graph
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The video page as a bottom sheet (like the NewPipe mini player / YouTube):
 *  - a video opens full page; drag the picture down (or press Back, or the arrow) and it shrinks into a
 *    mini player card above the bottom bar while the video keeps playing
 *  - the mini player is a small floating card (picture 16:9, rounded corners, white border, title underneath)
 *  - leaving the app while it plays opens the popup (picture in picture) window by itself, see PipSupport
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

private val PopupSpeeds = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

private fun speedText(s: Float): String =
    if (s % 1f == 0f) "${s.toInt()}x" else "${s}x"

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

    // ---- popup window: its buttons (quality, play / pause, speed) and the "open by itself when I leave" flag
    DisposableEffect(player) {
        val ctl = object : PipController {
            override val playing get() = player.wantPlay
            override val speedLabel get() = speedText(player.speed)
            override val qualityLabel get() = if (player.quality == 0) "Auto" else "${player.quality}p"
            override val title get() = VideoHost.title
            override val channel get() = VideoHost.channel
            override fun togglePlay() = player.togglePlay()
            override fun cycleSpeed() {
                val l = PopupSpeeds
                player.changeSpeed(l[(l.indexOf(player.speed) + 1).mod(l.size)])
            }
            override fun cycleQuality() {
                val opts = listOf(0) + player.heights.sortedDescending()
                player.changeQuality(opts[(opts.indexOf(player.quality) + 1).mod(opts.size)])
            }
        }
        PipHost.controller = ctl
        onDispose {
            if (PipHost.controller === ctl) PipHost.controller = null
            PipHost.autoEnter = false
            PipHost.sourceRect = null
            PipHost.refresh?.invoke()
        }
    }
    val wantAutoPopup = Graph.store.autoPopup && !hidden && !fullscreen && player.wantPlay && player.error == null
    SideEffect { PipHost.autoEnter = wantAutoPopup }
    LaunchedEffect(
        wantAutoPopup, player.wantPlay, player.speed, player.quality, player.heights,
        VideoHost.title, VideoHost.channel
    ) { PipHost.refresh?.invoke() }

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
    // once the card has stopped moving, tell the system where the picture is: the popup grows out of that spot
    LaunchedEffect(Unit) {
        snapshotFlow { pA.isRunning }.collectLatest { running ->
            if (!running) {
                delay(120)
                PipHost.refresh?.invoke()
            }
        }
    }

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
        // mini player = small floating card: picture (16:9) with the title underneath, like the website's mini player
        val margin = with(density) { 12.dp.toPx() }
        val pad = with(density) { 6.dp.toPx() }
        val cardW = with(density) { 216.dp.toPx() }.coerceAtMost(w * 0.64f)
        val miniW = cardW - 2 * pad
        val miniH = miniW * 9f / 16f
        val cardH = pad + miniH + with(density) { 50.dp.toPx() }
        val miniX = w - margin - cardW
        val cornerPx = with(density) { 24.dp.toPx() }
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
                    val x = lerpF(miniX, 0f, pc)
                    val sw = lerpF(cardW, w, pc).roundToInt()
                    val sh = lerpF(cardH, h, pc).roundToInt()
                    val top = lerpF(h - collapsedBottom - cardH, 0f, pc) + (if (pv < 0f) -pv * travel else 0f)
                    val pl = measurable.measure(Constraints.fixed(sw, sh))
                    layout(constraints.maxWidth, constraints.maxHeight) { pl.place(x.roundToInt(), top.roundToInt()) }
                }
                .graphicsLayer {
                    val pv = pA.value
                    val pc = pv.coerceIn(0f, 1f)
                    shape = RoundedCornerShape(lerpF(cornerPx, 0f, pc))
                    clip = true
                    shadowElevation = (1f - pc) * 14.dp.toPx()
                    alpha = if (pv < 0f) (1f + pv * 1.5f).coerceIn(0f, 1f) else 1f
                }
                .drawBehind { drawRect(lerp(Card, Bg, pA.value.coerceIn(0f, 1f))) }
                .drawWithContent {
                    drawContent()
                    // white border around the mini card; fades away while the page opens
                    val pc = pA.value.coerceIn(0f, 1f)
                    val a = (1f - pc * 3f).coerceIn(0f, 1f)
                    if (a > 0f) {
                        val bw = 2.5.dp.toPx()
                        val r = (lerpF(cornerPx, 0f, pc) - bw / 2).coerceAtLeast(0f)
                        drawRoundRect(
                            Color.White.copy(alpha = a),
                            topLeft = Offset(bw / 2, bw / 2),
                            size = Size(size.width - bw, size.height - bw),
                            cornerRadius = CornerRadius(r, r),
                            style = Stroke(bw)
                        )
                    }
                }
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
                    .onGloballyPositioned { c ->
                        val b = c.boundsInWindow()
                        PipHost.sourceRect = android.graphics.Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
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
                    player.buffering && !miniOn -> CtSpinner(Modifier.align(Alignment.Center), size = 26.dp, disc = false)
                }
                if (miniOn && err == null) {
                    val dur = player.duration.coerceAtLeast(1L)
                    CtProgressLine(
                        (player.position.toFloat() / dur).coerceIn(0f, 1f),
                        (player.buffered.toFloat() / dur).coerceIn(0f, 1f),
                        Modifier.align(Alignment.BottomCenter)
                    )
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

            // ---- the mini player card: title under the picture, play / pause on the picture, close in its corner;
            //      tap = open, drag up = open, swipe down = stop
            if (miniOn) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - pA.value * 2f).coerceIn(0f, 1f) }
                        .then(dragMod)
                        .clickable { expand() }
                ) {
                    Column(
                        Modifier.padding(
                            start = with(density) { pad.toDp() } + 6.dp,
                            end = with(density) { pad.toDp() } + 6.dp,
                            top = with(density) { (pad + miniH).toDp() } + 8.dp
                        )
                    ) {
                        Text(
                            VideoHost.title.ifEmpty { "Loading…" }, color = TextPrimary, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (VideoHost.channel.isNotEmpty()) {
                            Text(VideoHost.channel, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (pad + miniW / 2 - 20.dp.toPx()).roundToInt(),
                                    (pad + miniH / 2 - 20.dp.toPx()).roundToInt()
                                )
                            }
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                            .clickable { player.togglePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (player.wantPlay) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play / pause", tint = Color.White, modifier = Modifier.size(24.dp)
                        )
                    }
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (pad + miniW - 28.dp.toPx() - 6.dp.toPx()).roundToInt(),
                                    (pad + 6.dp.toPx()).roundToInt()
                                )
                            }
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                            .clickable { dismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
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
