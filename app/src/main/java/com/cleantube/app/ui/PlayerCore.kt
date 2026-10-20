@file:OptIn(UnstableApi::class)

package com.cleantube.app.ui

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.cleantube.app.data.Np
import com.cleantube.app.data.Playback
import com.cleantube.app.data.StreamInfo
import com.cleantube.app.data.StreamOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val playerHttp: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

private fun httpFactory(ua: String): OkHttpDataSource.Factory {
    val f = OkHttpDataSource.Factory(playerHttp).setUserAgent(ua)
    if (ua == Np.USER_AGENT) {
        f.setDefaultRequestProperties(
            mapOf("Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/")
        )
    }
    return f
}

private fun progressive(url: String, ua: String): MediaSource =
    ProgressiveMediaSource.Factory(httpFactory(ua)).createMediaSource(MediaItem.fromUri(url))

/** Picture and sound come as two files for most qualities: ExoPlayer plays them together. */
private fun buildSource(o: StreamOption): MediaSource {
    val video = progressive(o.url, o.userAgent)
    if (!o.merge || o.audioUrl.isEmpty()) return video
    return MergingMediaSource(video, progressive(o.audioUrl, o.audioUserAgent.ifEmpty { o.userAgent }))
}

/**
 * CleanTube's own video player: ExoPlayer fed with the NewPipe stream links. Everything the UI needs
 * (playing, buffering, position, quality...) is exposed as Compose state.
 */
@Stable
class CtPlayer(
    ctx: Context,
    val videoId: String,
    shorts: Boolean = false,
    private val maxHeight: Int = 720
) {
    private val app = ctx.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val exo: ExoPlayer = ExoPlayer.Builder(app, DefaultRenderersFactory(app).setEnableDecoderFallback(true))
        .setLoadControl(
            DefaultLoadControl.Builder()
                // start after half a second of data; after a stall resume after 1.5 s
                .setBufferDurationsMs(
                    if (shorts) 3_000 else 15_000,
                    if (shorts) 15_000 else 60_000,
                    if (shorts) 250 else 500,
                    if (shorts) 1_000 else 1_500
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        )
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    var isPlaying by mutableStateOf(false); private set
    var buffering by mutableStateOf(true); private set
    var firstFrame by mutableStateOf(false); private set
    var ended by mutableStateOf(false); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var buffered by mutableLongStateOf(0L); private set
    var videoRatio by mutableFloatStateOf(16f / 9f); private set
    var error by mutableStateOf<String?>(null); private set
    var speed by mutableFloatStateOf(1f); private set
    /** What the user wants (false = paused by the user / by the screen). */
    var wantPlay by mutableStateOf(true); private set
    /** Heights offered in the quality menu, ascending. */
    var heights by mutableStateOf<List<Int>>(emptyList()); private set
    /** 0 = Auto, otherwise the chosen height. */
    var quality by mutableIntStateOf(0); private set
    var currentHeight by mutableIntStateOf(0); private set

    private var info: StreamInfo? = null
    private var job: Job? = null
    private var retries = 0
    private var released = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING || (state == Player.STATE_IDLE && error == null)
            ended = state == Player.STATE_ENDED
            if (state == Player.STATE_READY && exo.duration > 0) duration = exo.duration
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@CtPlayer.isPlaying = isPlaying
        }

        override fun onRenderedFirstFrame() {
            firstFrame = true
            retries = 0
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                videoRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // most often an expired / refused link: get fresh ones and carry on from the same second
            if (retries < 2) {
                retries++
                load(force = true, resumeMs = exo.currentPosition.coerceAtLeast(0L))
            } else {
                fail("Couldn't play this video (${error.errorCodeName}).")
            }
        }
    }

    init {
        exo.addListener(listener)
    }

    /** Finds the stream links (instant when cached) and starts loading. */
    fun load(force: Boolean = false, resumeMs: Long = 0L) {
        job?.cancel()
        error = null
        buffering = true
        job = scope.launch {
            try {
                val i = Playback.resolve(videoId, force)
                info = i
                heights = Playback.ladder(i).map { Playback.heightOf(it) }.filter { it > 0 }.distinct().sorted()
                prepare(i, resumeMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message?.lineSequence()?.firstOrNull() ?: "Couldn't load this video.")
            }
        }
    }

    private fun prepare(i: StreamInfo, resumeMs: Long) {
        if (released) return
        val cap = when {
            retries >= 2 -> 360
            quality > 0 -> quality
            else -> maxHeight
        }
        val opt = Playback.pick(i, cap)
        val src: MediaSource = when {
            opt != null -> {
                currentHeight = Playback.heightOf(opt)
                buildSource(opt)
            }
            i.hlsUrl.isNotEmpty() -> {
                currentHeight = 0
                HlsMediaSource.Factory(httpFactory(Np.USER_AGENT)).createMediaSource(MediaItem.fromUri(i.hlsUrl))
            }
            else -> {
                fail("No playable stream found for this video.")
                return
            }
        }
        exo.setMediaSource(src, resumeMs)
        exo.prepare()
        exo.playWhenReady = wantPlay
    }

    private fun fail(msg: String) {
        error = msg
        buffering = false
        if (!released) exo.stop()
    }

    fun retry() {
        retries = 0
        load(force = true, resumeMs = position)
    }

    fun setPlaying(on: Boolean) {
        wantPlay = on
        if (!released) exo.playWhenReady = on
    }

    fun togglePlay() {
        if (ended) {
            exo.seekTo(0)
            setPlaying(true)
        } else {
            setPlaying(!wantPlay)
        }
    }

    fun seekTo(ms: Long) {
        val d = if (exo.duration > 0) exo.duration else Long.MAX_VALUE
        exo.seekTo(ms.coerceIn(0L, d))
        position = exo.currentPosition
    }

    fun seekBy(deltaMs: Long) = seekTo(exo.currentPosition + deltaMs)

    fun restart() {
        exo.seekTo(0)
        position = 0L
    }

    fun setSpeed(s: Float) {
        speed = s
        exo.setPlaybackSpeed(s)
    }

    fun setQuality(height: Int) {
        quality = height
        val i = info ?: return
        prepare(i, exo.currentPosition.coerceAtLeast(0L))
    }

    fun setLooping(on: Boolean) {
        exo.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun setVolume(v: Float) {
        exo.volume = v
    }

    fun tick() {
        if (released) return
        position = exo.currentPosition.coerceAtLeast(0L)
        buffered = exo.bufferedPosition.coerceAtLeast(0L)
        if (exo.duration > 0) duration = exo.duration
    }

    fun release() {
        if (released) return
        released = true
        job?.cancel()
        scope.cancel()
        exo.removeListener(listener)
        exo.release()
    }
}
