@file:OptIn(UnstableApi::class)

package com.cleantube.app.ui

import android.content.Context
import android.net.Uri
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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.cleantube.app.data.AudioSource
import com.cleantube.app.data.Np
import com.cleantube.app.data.Playback
import com.cleantube.app.data.StreamInfo
import com.cleantube.app.data.StreamOption
import com.cleantube.app.data.SubtitleTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private val rangeParam = Regex("([?&])range=[^&]*&?")

/**
 * googlevideo.com slows a plain "give me the whole file" request down to about playback speed. Asking with its own
 * `range=start-end` URL parameter (the same trick yt-dlp and NewPipe use) is served at full speed. The player
 * still thinks it reads a normal file; seeking just becomes a new range request.
 */
private fun ranged(upstream: DataSource.Factory): DataSource.Factory =
    ResolvingDataSource.Factory(upstream, ResolvingDataSource.Resolver { spec ->
        val url = spec.uri.toString()
        if (!url.contains("googlevideo.com") || !url.contains("/videoplayback")) return@Resolver spec
        val clen = spec.uri.getQueryParameter("clen")?.toLongOrNull() ?: return@Resolver spec
        if (clen <= 0L || spec.position >= clen) return@Resolver spec
        val unset = C.LENGTH_UNSET.toLong()
        val end = if (spec.length != unset) minOf(clen - 1, spec.position + spec.length - 1) else clen - 1
        val clean = url.replace(rangeParam, "$1").trimEnd('&', '?')
        val joined = clean + (if (clean.contains("?")) "&" else "?") + "range=${spec.position}-$end"
        spec.buildUpon().setUri(Uri.parse(joined)).setPosition(0L).setLength(unset).build()
    })

private fun progressive(url: String, ua: String): MediaSource =
    ProgressiveMediaSource.Factory(ranged(httpFactory(ua)))
        // ask the player every 256 KB whether it still wants more (default 1 MB), so loading stops right at the limit
        .setContinueLoadingCheckIntervalBytes(256 * 1024)
        .createMediaSource(MediaItem.fromUri(url))

/** Picture and sound come as two files for most qualities: ExoPlayer plays them together. */
private fun buildSource(o: StreamOption, audio: AudioSource? = null): MediaSource {
    val video = progressive(o.url, o.userAgent)
    if (!o.merge || o.audioUrl.isEmpty()) return video
    // another sound track picked in the settings card replaces the one that came with the picture
    if (audio != null && audio.url.isNotEmpty()) {
        return MergingMediaSource(video, progressive(audio.url, audio.userAgent.ifEmpty { o.userAgent }))
    }
    return MergingMediaSource(video, progressive(o.audioUrl, o.audioUserAgent.ifEmpty { o.userAgent }))
}

/** One caption line with the time it is shown. */
internal data class CaptionCue(val startMs: Long, val endMs: Long, val text: String)

private val vttTime = Regex("((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{3})\\s*-->\\s*((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{3})")
private val tagRegex = Regex("<[^>]*>")

private fun vttMs(s: String): Long {
    val p = s.replace(',', '.').split(':')
    val h = if (p.size == 3) p[0].toLong() else 0L
    val m = p[p.size - 2].toLong()
    val sec = p.last().toDouble()
    return ((h * 3600 + m * 60 + sec) * 1000).toLong()
}

/** Reads a WebVTT caption file. Very short cues (YouTube's auto captions repeat lines for a few ms) are dropped. */
internal fun parseVtt(text: String): List<CaptionCue> {
    val lines = text.replace("\r", "").split("\n")
    val out = ArrayList<CaptionCue>()
    var i = 0
    while (i < lines.size) {
        val m = vttTime.find(lines[i])
        if (m == null) { i++; continue }
        val st = vttMs(m.groupValues[1])
        val en = vttMs(m.groupValues[2])
        i++
        val body = ArrayList<String>()
        while (i < lines.size && lines[i].isNotBlank()) { body += lines[i]; i++ }
        val txt = body.joinToString("\n") { tagRegex.replace(it, "") }
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
            .trim()
        if (txt.isNotEmpty() && en - st >= 150) out += CaptionCue(st, en, txt)
    }
    return out
}

/**
 * Voidstream's own video player: ExoPlayer fed with the NewPipe stream links. Everything the UI needs
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
                // start after a fraction of a second of data; after a stall resume after about a second.
                // Only a little is loaded ahead (loading starts again below 15 s, stops at 30 s) so no data is wasted
                // on parts that are never watched and the video starts as soon as possible.
                .setBufferDurationsMs(
                    if (shorts) 3_000 else 15_000,
                    if (shorts) 15_000 else 30_000,
                    if (shorts) 200 else 350,
                    if (shorts) 800 else 1_200
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                // keep the last 10 s behind the playhead: jumping back (double tap, bar) needs no new download
                .setBackBuffer(if (shorts) 0 else 10_000, true)
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
    var speed by mutableFloatStateOf(if (shorts) 1f else com.cleantube.app.data.Graph.store.defaultSpeed); private set
    /** What the user wants (false = paused by the user / by the screen). */
    var wantPlay by mutableStateOf(true); private set
    /** Heights offered in the quality menu, ascending. */
    var heights by mutableStateOf<List<Int>>(emptyList()); private set
    /** 0 = Auto, otherwise the chosen height. */
    var quality by mutableIntStateOf(
        if (preferredQuality >= 0) preferredQuality else settingsQuality(app, shorts)
    ); private set
    var currentHeight by mutableIntStateOf(0); private set

    init { if (speed != 1f) exo.setPlaybackSpeed(speed) }

    /** Sound tracks: index 0 is always "Default" (the one that comes with the picture). */
    var audioSources by mutableStateOf(listOf(AudioSource.DEFAULT)); private set
    var audioIndex by mutableIntStateOf(0); private set
    var subtitles by mutableStateOf<List<SubtitleTrack>>(emptyList()); private set
    var captionsOn by mutableStateOf(preferredCaptions); private set
    var captionIndex by mutableIntStateOf(0); private set
    internal var cues by mutableStateOf<List<CaptionCue>>(emptyList()); private set
    /** 0 = not asked yet, 1 = loading the sound tracks / captions, 2 = done (lists may be empty). */
    var extrasState by mutableIntStateOf(0); private set
    /** True when loading the sound tracks / captions failed (the card then offers a retry). */
    var extrasFailed by mutableStateOf(false); private set
    /** 0 = nothing, 1 = loading the caption file, 2 = ready, 3 = the file could not be read. */
    var cueState by mutableIntStateOf(0); private set
    private var cueJob: Job? = null

    private var info: StreamInfo? = null
    private var job: Job? = null
    private var stallJob: Job? = null
    private var retries = 0
    private var safe = false
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
                safe = true // the fast link failed: use the NewPipe engine's links from now on
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
                val i = Playback.resolve(videoId, force, safe)
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
        val audio = audioSources.getOrNull(audioIndex)
        val src: MediaSource = when {
            opt != null -> {
                currentHeight = Playback.heightOf(opt)
                buildSource(opt, audio)
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
        watchForStall()
    }

    /**
     * A fast link that neither plays nor fails (the server just doesn't answer) would keep the spinner turning until
     * the network times out. If nothing at all is loaded 6 s after start, give up on it once and take the NewPipe
     * engine's links instead.
     */
    private fun watchForStall() {
        stallJob?.cancel()
        if (retries > 0 || safe) return
        stallJob = scope.launch {
            delay(6_000)
            if (!released && !firstFrame && error == null && retries == 0 && !safe &&
                exo.bufferedPosition - exo.currentPosition < 500
            ) {
                retries = 1
                safe = true
                load(force = true, resumeMs = exo.currentPosition.coerceAtLeast(0L))
            }
        }
    }

    private fun fail(msg: String) {
        error = msg
        buffering = false
        if (!released) exo.stop()
    }

    fun retry() {
        retries = 0
        safe = true
        load(force = true, resumeMs = position)
    }

    fun playWhen(on: Boolean) {
        wantPlay = on
        if (!released) exo.playWhenReady = on
    }

    fun togglePlay() {
        if (ended) {
            exo.seekTo(0)
            playWhen(true)
        } else {
            playWhen(!wantPlay)
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

    fun changeSpeed(s: Float) {
        speed = s
        exo.setPlaybackSpeed(s)
    }

    fun changeQuality(height: Int) {
        quality = height
        val i = info ?: return
        prepare(i, exo.currentPosition.coerceAtLeast(0L))
    }

    /** Switches to another sound track (picture keeps playing from the same second). */
    fun changeAudio(index: Int) {
        if (index == audioIndex || index !in audioSources.indices) return
        audioIndex = index
        val i = info ?: return
        prepare(i, exo.currentPosition.coerceAtLeast(0L))
    }

    /** Loads the other sound tracks and the caption languages (once per video). */
    fun ensureExtras() {
        if (extrasState != 0 || released) return
        extrasState = 1
        extrasFailed = false
        scope.launch {
            try {
                val e = Playback.extras(videoId)
                audioSources = listOf(AudioSource.DEFAULT) + e.audio
                subtitles = e.subtitles
                captionIndex = subtitles.indexOfFirst { it.lang.startsWith("en") && !it.auto }
                    .takeIf { it >= 0 } ?: subtitles.indexOfFirst { !it.auto }.coerceAtLeast(0)
                extrasState = 2
                if (captionsOn) loadCues()
            } catch (e: CancellationException) {
                extrasState = 0
                throw e
            } catch (e: Exception) {
                extrasState = 0
                extrasFailed = true
            }
        }
    }

    fun setCaptions(on: Boolean) {
        captionsOn = on
        preferredCaptions = on
        if (on) {
            ensureExtras()
            if (extrasState == 2) loadCues()
        }
    }

    fun selectCaption(index: Int) {
        if (index !in subtitles.indices) return
        captionIndex = index
        loadCues()
    }

    private fun loadCues() {
        val t = subtitles.getOrNull(captionIndex) ?: run { cues = emptyList(); return }
        cueJob?.cancel()
        cueState = 1
        cueJob = scope.launch {
            val parsed = try {
                withContext(Dispatchers.IO) {
                    val req = okhttp3.Request.Builder().url(t.url).header("User-Agent", Np.USER_AGENT).build()
                    playerHttp.newCall(req).execute().use { r -> parseVtt(r.body?.string().orEmpty()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            cues = parsed
            cueState = if (parsed.isEmpty()) 3 else 2
        }
    }

    /** Play this video again when it ends (the toggle in the playback settings). */
    var looping by mutableStateOf(false); private set

    /** Picks a caption language and turns captions on in one go. */
    fun pickCaption(index: Int) {
        if (index !in subtitles.indices) return
        captionIndex = index
        captionsOn = true
        preferredCaptions = true
        loadCues()
    }

    fun setLooping(on: Boolean) {
        looping = on
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
        stallJob?.cancel()
        cueJob?.cancel()
        scope.cancel()
        exo.removeListener(listener)
        exo.release()
    }

    companion object {
        /** The quality picked in the menu stays for the next videos (until the app is closed). */
        var preferredQuality = -1

        /** The fixed quality chosen in Settings for the connection in use (Wi-Fi or mobile data); 0 = Auto. */
        fun settingsQuality(ctx: Context, shorts: Boolean): Int {
            if (shorts) return 0
            val store = com.cleantube.app.data.Graph.store
            val onWifi = try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))
            } catch (e: Exception) {
                true
            }
            return if (onWifi) store.wifiQuality else store.mobileQuality
        }

        /** Captions switched on stay on for the next videos (until the app is closed). */
        var preferredCaptions = false
    }
}
