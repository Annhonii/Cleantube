package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import coil3.compose.AsyncImage
import com.cleantube.app.data.formatDuration
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay

/** Played part of the progress bar. */
val PlayRed = Color(0xFFE5231C)

private val BarTrack = Color(0xCC4A4A4A)
private val BarBuffered = Color(0xCCA8A8A8)

/** Seconds skipped by a double tap on the left / right side of the player. */
private const val SKIP_SECONDS = 10f

/**
 * State of the YouTube IFrame player behind [VideoPlayerCard]. The IFrame player itself is shown without its own
 * controls (controls=0); everything you see on top of the picture is drawn by the app.
 */
@Stable
class VideoPlayerState {
    var player by mutableStateOf<YouTubePlayer?>(null)
    var loadedId by mutableStateOf<String?>(null)

    /** True once the video really plays (until then the thumbnail and a spinner are shown). */
    var started by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var position by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(0f)

    /** 0..1: how much of the video YouTube has downloaded already. */
    var loaded by mutableFloatStateOf(0f)
    var failedId by mutableStateOf<String?>(null)
    var errorText by mutableStateOf("")

    val listener = object : AbstractYouTubePlayerListener() {
        override fun onReady(youTubePlayer: YouTubePlayer) {
            player = youTubePlayer
        }

        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
            when (state) {
                PlayerConstants.PlayerState.PLAYING -> {
                    playing = true
                    buffering = false
                    started = true
                    ended = false
                }
                PlayerConstants.PlayerState.PAUSED -> {
                    playing = false
                    buffering = false
                }
                PlayerConstants.PlayerState.BUFFERING -> buffering = true
                PlayerConstants.PlayerState.ENDED -> {
                    playing = false
                    buffering = false
                    ended = true
                }
                else -> {}
            }
        }

        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
            position = second
        }

        override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
            this@VideoPlayerState.duration = duration
        }

        override fun onVideoLoadedFraction(youTubePlayer: YouTubePlayer, loadedFraction: Float) {
            loaded = loadedFraction
        }

        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
            errorText = error.name
            failedId = loadedId
        }
    }

    fun load(id: String) {
        failedId = null
        errorText = ""
        started = false
        playing = false
        buffering = false
        ended = false
        position = 0f
        duration = 0f
        loaded = 0f
        loadedId = id
        player?.loadVideo(id, 0f)
    }

    fun togglePlay() {
        val p = player ?: return
        when {
            ended -> {
                p.seekTo(0f)
                p.play()
            }
            playing -> p.pause()
            else -> p.play()
        }
    }

    fun seekTo(sec: Float) {
        val p = player ?: return
        p.seekTo(sec)
        position = sec
        if (ended) {
            ended = false
            p.play()
        }
    }

    fun seekBy(deltaSec: Float) {
        val max = if (duration > 0f) duration else Float.MAX_VALUE
        seekTo((position + deltaSec).coerceIn(0f, max))
    }
}

private class SeekHint(val id: Int, val forward: Boolean, val at: Offset)

/**
 * The video page's player: rounded card, own controls (minimise chevron, big play / pause button, time labels,
 * red progress bar with buffered part, fullscreen button). Tap = show / hide controls, double tap on the sides
 * = skip 10 s, double tap in the middle = play / pause.
 *
 * [modifier] decides the size and position, so the page can put the card at the top or fill the whole screen
 * ([immersive]) without the player being re-created.
 */
@Composable
fun VideoPlayerCard(
    videoId: String,
    immersive: Boolean,
    onBack: () -> Unit,
    onToggleImmersive: () -> Unit,
    onOpenInYouTube: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val st = remember { VideoPlayerState() }
    val playerView = remember { YouTubePlayerView(ctx).apply { enableAutomaticInitialization = false } }

    DisposableEffect(playerView) {
        val owner = ctx as? LifecycleOwner
        owner?.lifecycle?.addObserver(playerView)
        val options = IFramePlayerOptions.Builder(ctx)
            .controls(0)
            .fullscreen(0)
            .rel(0)
            .ivLoadPolicy(3)
            .build()
        playerView.initialize(st.listener, options)
        onDispose {
            owner?.lifecycle?.removeObserver(playerView)
            st.player = null
            playerView.release()
        }
    }

    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(st.player, videoId) {
        if (st.player != null) st.load(videoId)
    }

    // If the player never gets ready, or the video doesn't start, show the error screen instead of spinning forever.
    LaunchedEffect(playerView, retry) {
        delay(12_000)
        if (st.player == null) {
            st.errorText = "player not ready"
            st.failedId = videoId
        }
    }
    LaunchedEffect(st.loadedId, st.player, retry) {
        if (st.loadedId == null || st.player == null) return@LaunchedEffect
        delay(15_000)
        if (!st.started && st.failedId == null) {
            st.errorText = "timed out"
            st.failedId = videoId
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var touchTick by remember { mutableIntStateOf(0) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var hint by remember { mutableStateOf<SeekHint?>(null) }
    var hintId by remember { mutableIntStateOf(0) }

    val failed = st.failedId == videoId
    val showControls = !failed && (controlsVisible || !st.playing || st.ended)

    // Controls fade out 3 s after the last touch while the video is playing.
    LaunchedEffect(controlsVisible, st.playing, touchTick, scrub) {
        if (controlsVisible && st.playing && scrub == null) {
            delay(3_000)
            controlsVisible = false
        }
    }

    val shape = if (immersive) RectangleShape else RoundedCornerShape(28.dp)

    Box(modifier.clip(shape).background(Color.Black)) {
        AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())

        if (!st.started && !failed) {
            AsyncImage(
                model = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // touch layer: sits above the YouTube page so its own tap handling never gets the touches
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(videoId) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            touchTick++
                        },
                        onDoubleTap = { off ->
                            touchTick++
                            val third = size.width / 3f
                            when {
                                off.x < third -> {
                                    st.seekBy(-SKIP_SECONDS)
                                    hintId += 1
                                    hint = SeekHint(hintId, false, off)
                                }
                                off.x > 2f * third -> {
                                    st.seekBy(SKIP_SECONDS)
                                    hintId += 1
                                    hint = SeekHint(hintId, true, off)
                                }
                                else -> st.togglePlay()
                            }
                        }
                    )
                }
        )

        hint?.let { h -> key(h.id) { SeekHintView(h) } }

        // thin red line on the bottom edge while the controls are hidden
        if (!failed && !showControls && st.started && st.duration > 0f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF)))
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth((st.position / st.duration).coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(PlayRed)
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x59000000), Color(0x33000000), Color(0x99000000))
                            )
                        )
                )

                // top-left: minimise chevron
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp)
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // centre: play / pause (spinner while loading or buffering)
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0x66808080))
                        .clickable {
                            touchTick++
                            st.togglePlay()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!st.started || st.buffering) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(
                            when {
                                st.ended -> Icons.Rounded.Replay
                                st.playing -> Icons.Rounded.Pause
                                else -> Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (st.playing) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // bottom: time labels + progress bar, fullscreen button on the right
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        val shown = scrub ?: st.position
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDuration(shown.toLong()), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(formatDuration(st.duration.toLong()), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        SeekBar(
                            position = shown,
                            duration = st.duration,
                            loaded = st.loaded,
                            scrubbing = scrub != null,
                            onSeek = { sec ->
                                touchTick++
                                st.seekTo(sec)
                            },
                            onScrub = { scrub = it }
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = {
                        touchTick++
                        onToggleImmersive()
                    }) {
                        Icon(
                            if (immersive) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = if (immersive) "Exit fullscreen" else "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        if (failed) {
            Column(
                Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "This video can't be played inside the app.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
                if (st.errorText.isNotEmpty()) {
                    Text("(${st.errorText})", color = Color(0xFF777777), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Retry", onClick = {
                        retry++
                        st.load(videoId)
                    })
                    Pill("Open in YouTube", onClick = onOpenInYouTube, active = true)
                }
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp)
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

/**
 * Slim rounded bar: dark track, light buffered part, red played part. Tap or drag to jump; while dragging the bar
 * gets thicker and shows a round handle. [onSeek] fires on release, [onScrub] reports the position under the finger.
 */
@Composable
private fun SeekBar(
    position: Float,
    duration: Float,
    loaded: Float,
    scrubbing: Boolean,
    onSeek: (Float) -> Unit,
    onScrub: (Float?) -> Unit
) {
    var lastFrac by remember { mutableFloatStateOf(0f) }
    val onSeekNow by rememberUpdatedState(onSeek)
    val onScrubNow by rememberUpdatedState(onScrub)
    val durationNow by rememberUpdatedState(duration)

    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { off ->
                    if (durationNow > 0f) onSeekNow((off.x / size.width).coerceIn(0f, 1f) * durationNow)
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        lastFrac = (off.x / size.width).coerceIn(0f, 1f)
                        onScrubNow(lastFrac * durationNow)
                    },
                    onDragEnd = {
                        if (durationNow > 0f) onSeekNow(lastFrac * durationNow)
                        onScrubNow(null)
                    },
                    onDragCancel = { onScrubNow(null) },
                    onHorizontalDrag = { change, _ ->
                        lastFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrubNow(lastFrac * durationNow)
                        change.consume()
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val thick = (if (scrubbing) 6.dp else 4.dp).toPx()
            val y = (size.height - thick) / 2f
            val radius = CornerRadius(thick / 2f, thick / 2f)
            val played = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
            val buffered = maxOf(loaded.coerceIn(0f, 1f), played)

            drawRoundRect(color = BarTrack, topLeft = Offset(0f, y), size = Size(size.width, thick), cornerRadius = radius)
            drawRoundRect(color = BarBuffered, topLeft = Offset(0f, y), size = Size(size.width * buffered, thick), cornerRadius = radius)
            drawRoundRect(color = PlayRed, topLeft = Offset(0f, y), size = Size(size.width * played, thick), cornerRadius = radius)
            if (scrubbing) {
                drawCircle(color = PlayRed, radius = 8.dp.toPx(), center = Offset(size.width * played, y + thick / 2f))
            }
        }
    }
}

/** Ripple + "+10s" / "-10s" where the double tap happened. */
@Composable
private fun SeekHintView(h: SeekHint) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(600)) }
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            color = Color.White.copy(alpha = 0.28f * (1f - p.value)),
            radius = 48.dp.toPx() + 90.dp.toPx() * p.value,
            center = h.at
        )
    }
    Box(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        contentAlignment = if (h.forward) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(
            (if (h.forward) "+" else "-") + SKIP_SECONDS.toInt() + "s",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { alpha = 1f - p.value }
        )
    }
}
