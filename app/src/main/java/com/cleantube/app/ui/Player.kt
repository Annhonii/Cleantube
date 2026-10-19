package com.cleantube.app.ui

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Bridge between the player (which hands us a fullscreen view) and the activity
 * (which rotates the screen and hides the system bars).
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

    /** The video page's own fullscreen (landscape, system bars hidden); its player card then fills the screen. */
    var immersive by mutableStateOf(false)
        private set

    fun setImmersiveMode(on: Boolean) {
        if (immersive == on) return
        immersive = on
        onChanged?.invoke(on)
    }

    /** Back button while fullscreen. */
    fun requestExit() {
        if (immersive) {
            setImmersiveMode(false)
            return
        }
        val e = exit
        if (e != null) e() else leave()
    }
}

val LocalFullscreen = staticCompositionLocalOf<FullscreenHost> { error("FullscreenHost not provided") }

/**
 * The official YouTube IFrame player (via android-youtube-player).
 * Playback, ads and controls are entirely YouTube's.
 */
@Composable
fun PlayerBox(videoId: String, embeddable: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val host = LocalFullscreen.current
    val currentId by rememberUpdatedState(videoId)

    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var failedId by remember { mutableStateOf<String?>(null) }

    val playerView = remember {
        YouTubePlayerView(ctx).apply { enableAutomaticInitialization = false }
    }

    DisposableEffect(playerView) {
        val owner = ctx as? LifecycleOwner
        owner?.lifecycle?.addObserver(playerView)

        playerView.addFullscreenListener(object : FullscreenListener {
            override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                host.enter(fullscreenView, exitFullscreen)
            }

            override fun onExitFullscreen() {
                host.leave()
            }
        })

        val options = IFramePlayerOptions.Builder(ctx)
            .controls(1)
            .fullscreen(1)
            .build()

        playerView.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                player = youTubePlayer
            }

            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                failedId = currentId
            }
        }, options)

        onDispose {
            owner?.lifecycle?.removeObserver(playerView)
            player = null
            playerView.release()
        }
    }

    LaunchedEffect(player, videoId) {
        player?.loadVideo(videoId, 0f)
    }

    val failed = !embeddable || failedId == videoId

    Box(modifier.aspectRatio(16f / 9f).background(Color.Black)) {
        AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())
        if (failed) {
            Column(
                Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "This video can't be played inside the app.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Row(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (embeddable) {
                        Pill("Retry", onClick = {
                            failedId = null
                            player?.loadVideo(videoId, 0f)
                        })
                    }
                    Pill("Open in YouTube", onClick = { openInYouTube(ctx, videoId) }, active = true)
                }
            }
        }
    }
}
