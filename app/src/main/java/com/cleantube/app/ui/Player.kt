package com.cleantube.app.ui

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay

/**
 * Bridge to the activity, which rotates the screen and hides the system bars while a video is fullscreen.
 * (The fullscreen picture itself is drawn by [CtFullscreen]; [view] / [enter] / [leave] are kept for the old callers.)
 */
class FullscreenHost {
    var view by mutableStateOf<View?>(null)
        private set
    private var exit: (() -> Unit)? = null

    /** Set by the activity: called with true when entering fullscreen, false when leaving. */
    var onChanged: ((Boolean) -> Unit)? = null

    fun enter(v: View, exitFullscreen: () -> Unit) {
        exit = exitFullscreen
        view = v
        onChanged?.invoke(true)
    }

    fun leave() {
        view = null
        exit = null
        onChanged?.invoke(false)
    }

    /** Used by the native player: just rotate / hide bars. */
    fun setActive(on: Boolean) {
        onChanged?.invoke(on)
    }

    /** Back button while fullscreen. */
    fun requestExit() {
        val e = exit
        if (e != null) e() else leave()
    }
}

/** Lets other screens read / control the player (used by "Background" to carry on from the same second). */
object PlayerControl {
    @Volatile var second: Float = 0f
    var owner: Any? = null
    var pause: (() -> Unit)? = null
}

/** Picture-in-picture ("Popup") state. The activity sets [enter] and flips [active]. */
object PipHost {
    var active by mutableStateOf(false)
    var enter: (() -> Unit)? = null

    /** True from the moment the user leaves the app until the popup is up (or the app is back): hide extra buttons. */
    var transitioning by mutableStateOf(false)

    /** True while a video is playing on screen: leaving the app should then open the popup by itself. */
    @Volatile var autoEnter = false

    /** Where the picture is in the window; the system grows the popup out of this rectangle (smooth animation). */
    @Volatile var sourceRect: android.graphics.Rect? = null

    /** The player behind the popup's buttons (play / pause, speed, quality). */
    @Volatile var controller: PipController? = null

    /** Set by the activity: re-sends the popup's buttons and auto-open flag to the system. */
    var refresh: (() -> Unit)? = null
}

/** What the popup window's buttons need from the player. */
interface PipController {
    val playing: Boolean
    val speedLabel: String
    val qualityLabel: String
    val title: String
    val channel: String
    fun togglePlay()
    fun cycleSpeed()
    fun cycleQuality()
}

val LocalFullscreen = staticCompositionLocalOf<FullscreenHost> { error("FullscreenHost not provided") }

/**
 * CleanTube's own player. It plays the video file straight from the stream links (see [CtPlayer]) and draws
 * everything itself: no YouTube page, no YouTube controls, just the picture, a loading animation and minimal controls.
 */
@Composable
fun PlayerBox(videoId: String, embeddable: Boolean, modifier: Modifier = Modifier, title: String = "") {
    val ctx = LocalContext.current
    val host = LocalFullscreen.current
    val p = remember(videoId) { CtPlayer(ctx, videoId, maxHeight = 480) }
    var fullscreen by remember(videoId) { mutableStateOf(false) }

    DisposableEffect(p) {
        PlayerControl.second = 0f
        PlayerControl.owner = p
        PlayerControl.pause = { p.playWhen(false) }

        // like any video app: stop when the app goes away (the popup window and background play are separate)
        val owner = ctx as? LifecycleOwner
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP && !PipHost.active) p.playWhen(false)
        }
        owner?.lifecycle?.addObserver(obs)

        p.load()
        onDispose {
            if (PlayerControl.owner === p) {
                PlayerControl.owner = null
                PlayerControl.pause = null
            }
            owner?.lifecycle?.removeObserver(obs)
            if (fullscreen) host.setActive(false)
            p.release()
        }
    }

    // position for the seek bar and for "Background" play
    LaunchedEffect(p) {
        while (true) {
            p.tick()
            PlayerControl.second = p.position / 1000f
            delay(if (p.isPlaying) 250L else 500L)
        }
    }

    // keep the screen on while playing
    val view = LocalView.current
    SideEffect { view.keepScreenOn = p.isPlaying }
    DisposableEffect(view) { onDispose { view.keepScreenOn = false } }

    val inPopup = PipHost.active
    Box((if (inPopup) modifier.fillMaxSize() else modifier.aspectRatio(16f / 9f)).background(Color.Black)) {
        CtVideoSurface(p, Modifier.fillMaxSize(), active = !fullscreen)
        CtPoster(videoId, p)
        val err = p.error
        when {
            err != null -> CtErrorView(err, onRetry = { p.retry() }, onOpen = { openInYouTube(ctx, videoId) })
            !inPopup -> CtOverlay(
                p, fullscreen = false, title = title,
                onFullscreen = { fullscreen = true; host.setActive(true) }
            )
            p.buffering -> CtSpinner(Modifier.align(Alignment.Center), disc = false)
        }
    }

    if (fullscreen) {
        CtFullscreen(videoId, p, title) {
            fullscreen = false
            host.setActive(false)
        }
    }
}

/** Full-window player drawn in a dialog above the app. Back or the close button leaves it. */
@Composable
internal fun CtFullscreen(videoId: String, p: CtPlayer, title: String, onExit: () -> Unit) {
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setDimAmount(0f)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= 28) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            val c = WindowCompat.getInsetsController(window, view)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CtVideoSurface(p, Modifier.fillMaxSize(), active = true)
            CtPoster(videoId, p)
            val err = p.error
            if (err != null) {
                CtErrorView(err, onRetry = { p.retry() }, onOpen = null)
            } else {
                CtOverlay(p, fullscreen = true, title = title, onFullscreen = onExit)
            }
        }
    }
}
