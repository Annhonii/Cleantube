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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.flow.first
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

private fun qualityText(p: CtPlayer): String = if (p.quality == 0) "Auto" else "${p.quality}p"

/** 0.5x > 1x > 1.25x > 1.5x > 2x > back to 0.5x (the same buttons exist in the popup window). */
private fun cycleSpeed(p: CtPlayer) {
    val l = PopupSpeeds
    p.changeSpeed(l[(l.indexOf(p.speed) + 1).mod(l.size)])
}

/** Auto > highest > ... > lowest > Auto. */
private fun cycleQuality(p: CtPlayer) {
    val o = listOf(0) + p.heights.sortedDescending()
    p.changeQuality(o[(o.indexOf(p.quality) + 1).mod(o.size)])
}

@Composable
private fun MiniChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (selected) Color.Black else Color.White,
            fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1
        )
    }
}

@Composable
private fun MiniGlassButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}

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
            override val qualityLabel get() = qualityText(player)
            override val title get() = VideoHost.title
            override val channel get() = VideoHost.channel
            override fun togglePlay() = player.togglePlay()
            override fun openFullscreen() { PipHost.fullscreenTick++ }
            override fun cycleSpeed() = cycleSpeed(player)
            override fun cycleQuality() = cycleQuality(player)
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

    // the popup's fullscreen button: wait until the app is out of the popup window, then open fullscreen
    val fsStart = remember { PipHost.fullscreenTick }
    LaunchedEffect(PipHost.fullscreenTick) {
        if (PipHost.fullscreenTick != fsStart) {
            snapshotFlow { PipHost.active }.first { !it }
            delay(150)
            fullscreen = true
            host.setActive(true)
        }
    }

    // ---- open / collapse requests
    LaunchedEffect(VideoHost.openTick) {
        if (VideoHost.fresh) {
            VideoHost.fresh = false
            pA.snapTo(0f)
        }
        pA.animateTo(1f, spring(dampingRatio = 1f, stiffness = 340f))
    }
    val collapseStart = remember { VideoHost.collapseTick }
    LaunchedEffect(VideoHost.collapseTick) {
        if (VideoHost.collapseTick != collapseStart) pA.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f))
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

    if (hidden) return

    val route = remember(id) { Route.Video(id, VideoHost.seed) }
    var miniWdp by remember { mutableFloatStateOf(Graph.store.miniWidthDp) }
    var dragging by remember { mutableStateOf(false) }
    // the buttons on the mini player are hidden; one tap shows them (settings + fullscreen) for a few seconds
    var controls by remember { mutableStateOf(false) }
    var controlsTick by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }   // the quality + speed panel on the card
    LaunchedEffect(controls, controlsTick, settings) {
        if (controls && !settings) {
            delay(3000)
            controls = false
        }
    }
    LaunchedEffect(controls) { if (!controls) settings = false }
    // keeps the biggest window size seen: the popup window is far smaller than that in both directions
    val biggest = remember { FloatArray(2) }
    // fade of the picture: hides the moment the user switches the app and fades in again once the switch is done
    // (inside the popup window, or when coming back), so the system's own animation is not seen as a jump
    val fadeA = remember { Animatable(1f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        biggest[0] = maxOf(biggest[0], w)
        biggest[1] = maxOf(biggest[1], h)
        // popup window: the very same picture (same surface, no flash) just fills the whole little window.
        // Also true for the first frames of the move into the popup, when the window is already small but the
        // system hasn't announced the popup yet: that is what made the switch jump.
        val inPip = PipHost.active || (w > h && w < biggest[0] * 0.7f && h < biggest[1] * 0.7f)
        LaunchedEffect(inPip, PipHost.transitioning) {
            when {
                // the app is switched / goes into the popup: the player is already hidden, wait until the system's
                // own animation is completely finished, then fade the player in
                inPip -> {
                    fadeA.snapTo(0f)
                    delay(350)
                    fadeA.animateTo(1f, tween(420))
                }
                PipHost.transitioning -> fadeA.snapTo(0f)
                // back in the app after switching: same, hidden first, then a soft fade in
                else -> if (fadeA.value < 1f) {
                    delay(160)
                    fadeA.animateTo(1f, tween(380))
                }
            }
        }
        val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
        val navPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        // mini player = just the picture (16:9), floating bottom-right; its size is changed with the corner handle
        val margin = with(density) { 12.dp.toPx() }
        val cardW = with(density) { miniWdp.dp.toPx() }
            .coerceIn(with(density) { 140.dp.toPx() }, minOf(with(density) { 320.dp.toPx() }, w - 2 * margin))
        val cardH = cardW * 9f / 16f
        val miniX = w - margin - cardW
        val cornerPx = with(density) { 18.dp.toPx() }
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
        val miniScale = cardW / vW
        val dens = density.density

        val bodyOn by remember(inPip) { derivedStateOf { !inPip && pA.value > 0.02f } }
        val expandedLook by remember(inPip) { derivedStateOf { !inPip && pA.value > 0.97f } }
        val miniOn by remember(inPip) { derivedStateOf { !inPip && pA.value < 0.5f } }

        val controlsOn = controls && miniOn && !dragging && !PipHost.transitioning

        fun expand() {
            controls = false
            scope.launch { pA.animateTo(1f, spring(dampingRatio = 1f, stiffness = 340f)) }
        }

        fun dismiss() {
            scope.launch {
                pA.animateTo(-(cardH + collapsedBottom + 60f) / travel, tween(200))
                VideoHost.close()
            }
        }

        // drag the picture / the card: collapse, expand or swipe away
        var canDismiss by remember { mutableStateOf(false) }
        fun onDrag(dy: Float) {
            val low = if (canDismiss) -2f else 0f
            val next = (pA.value - dy / travel).coerceIn(low, 1f)
            scope.launch { pA.snapTo(next) }
        }
        // the big picture is drawn scaled down while it shrinks, so its finger movements are scaled too: undo that
        val dragPicture = rememberDraggableState { dy ->
            onDrag(dy * lerpF(miniScale, 1f, pA.value.coerceIn(0f, 1f)))
        }
        val dragCard = rememberDraggableState { dy -> onDrag(dy) }
        fun dragMod(state: DraggableState) = Modifier.draggable(
            state = state,
            orientation = Orientation.Vertical,
            onDragStarted = {
                pA.stop()
                canDismiss = pA.value < 0.2f
                dragging = true
            },
            onDragStopped = { v ->
                dragging = false
                val cur = pA.value
                scope.launch {
                    when {
                        canDismiss && (v > 1100f || -cur * travel > cardH * 0.5f) -> dismiss()
                        v < -700f -> pA.animateTo(1f, spring(dampingRatio = 1f, stiffness = 340f))
                        v > 700f -> pA.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f))
                        cur > 0.5f -> pA.animateTo(1f, spring(dampingRatio = 1f, stiffness = 340f))
                        else -> pA.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f))
                    }
                }
            }
        )

        Box(
            Modifier
                .layout { measurable, constraints ->
                    val pv = if (inPip) 1f else pA.value
                    val pc = pv.coerceIn(0f, 1f)
                    val x = lerpF(miniX, 0f, pc)
                    val sw = lerpF(cardW, w, pc).roundToInt()
                    val sh = lerpF(cardH, h, pc).roundToInt()
                    val top = lerpF(h - collapsedBottom - cardH, 0f, pc) + (if (pv < 0f) -pv * travel else 0f)
                    val pl = measurable.measure(Constraints.fixed(sw, sh))
                    layout(constraints.maxWidth, constraints.maxHeight) { pl.place(x.roundToInt(), top.roundToInt()) }
                }
                .graphicsLayer {
                    val pv = if (inPip) 1f else pA.value
                    val pc = pv.coerceIn(0f, 1f)
                    shape = RoundedCornerShape(lerpF(cornerPx, 0f, pc))
                    clip = true
                    shadowElevation = (1f - pc) * 12.dp.toPx()
                    alpha = if (pv < 0f) (1f + pv * 1.5f).coerceIn(0f, 1f) else 1f
                }
                .drawBehind {
                    val dark = inPip || (PipHost.transitioning && PipHost.autoEnter)
                    drawRect(if (dark) Color.Black else lerp(Card, Bg, pA.value.coerceIn(0f, 1f)))
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

            // ---- the picture. It is always laid out at the full-page size and only scaled / moved on the GPU while
            //      it shrinks and grows, so the video surface is never resized frame by frame (that was the jitter).
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val pw = (if (inPip) w else vW).roundToInt()
                        val ph = (if (inPip) h else vH).roundToInt()
                        val pl = measurable.measure(Constraints.fixed(pw, ph))
                        layout(constraints.maxWidth, constraints.maxHeight) { pl.place(0, 0) }
                    }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        alpha = fadeA.value
                        if (inPip) {
                            clip = false
                        } else {
                            val pc = pA.value.coerceIn(0f, 1f)
                            val sc = lerpF(miniScale, 1f, pc)
                            scaleX = sc
                            scaleY = sc
                            translationX = lerpF(0f, sideX, pc)
                            translationY = lerpF(0f, vTop, pc)
                            shape = RoundedCornerShape(lerpF(cornerPx, 26.dp.toPx(), pc) / sc)
                            clip = true
                        }
                    }
                    .onGloballyPositioned { c ->
                        val b = c.boundsInWindow()
                        PipHost.sourceRect = android.graphics.Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
                    }
                    .then(dragMod(dragPicture))
                    .background(Color.Black)
            ) {
                CtVideoSurface(player, Modifier.fillMaxSize(), active = !fullscreen)
                CtPoster(id, player)
                val err = player.error
                when {
                    inPip -> if (player.buffering) CtSpinner(Modifier.align(Alignment.Center), disc = false)
                    err != null -> if (expandedLook) CtErrorView(err, onRetry = { player.retry() }, onOpen = { openInYouTube(ctx, id) })
                    expandedLook -> CtOverlay(
                        player, fullscreen = false, title = VideoHost.title,
                        onFullscreen = { fullscreen = true; host.setActive(true) }
                    )
                }
                AnimatedVisibility(
                    visible = controlsOn && err == null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(200))
                ) {
                    val dur = player.duration.coerceAtLeast(1L)
                    CtProgressLine(
                        (player.position.toFloat() / dur).coerceIn(0f, 1f),
                        (player.buffered.toFloat() / dur).coerceIn(0f, 1f),
                        Modifier.graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 1f)
                            scaleY = 1f / miniScale.coerceAtLeast(0.3f)
                        }
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

            // ---- mini player controls: one tap shows just two buttons: settings (quality + speed) and fullscreen.
            //      tap on the picture while they are shown = open the page, drag up = open, swipe down = stop.
            //      Stays while a drag that started here is still going, so the gesture is never cut in the middle.
            if (!inPip && (miniOn || dragging)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - pA.value * 2f).coerceIn(0f, 1f) }
                        .then(dragMod(dragCard))
                        .clickable(enabled = miniOn) {
                            when {
                                settings -> settings = false
                                controls -> expand()
                                else -> controls = true
                            }
                        }
                ) {
                    AnimatedVisibility(
                        visible = controlsOn,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(200))
                    ) {
                        Box(
                            Modifier
                                .size(with(density) { cardW.toDp() }, with(density) { cardH.toDp() })
                                .background(Color(if (settings) 0xB3000000L else 0x40000000L))
                        ) {
                            if (settings) {
                                // quality and speed, the same values the popup window shows
                                Column(
                                    Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                                ) {
                                    val autoLabel = if (player.quality == 0 && player.currentHeight > 0) "Auto ${player.currentHeight}p" else "Auto"
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        MiniChip(autoLabel, player.quality == 0) { player.changeQuality(0) }
                                        player.heights.sortedDescending().forEach { q ->
                                            MiniChip("${q}p", player.quality == q) { player.changeQuality(q) }
                                        }
                                    }
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        PopupSpeeds.forEach { sp ->
                                            MiniChip(speedText(sp), player.speed == sp) { player.changeSpeed(sp) }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    MiniGlassButton(Icons.Rounded.Settings, "Quality and speed") { settings = true }
                                    MiniGlassButton(Icons.Rounded.Fullscreen, "Fullscreen") {
                                        controls = false
                                        fullscreen = true
                                        host.setActive(true)
                                    }
                                }
                            }
                        }
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
