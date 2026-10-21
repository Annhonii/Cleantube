package com.cleantube.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cleantube.app.MainActivity
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Entry point used by the UI: start / stop playing a video's sound in the background (screen off, other apps). */
object BackgroundPlay {
    /** Id of the video currently playing in the background, or null. Observed by the UI. */
    var activeId by mutableStateOf<String?>(null)

    const val ACTION_START = "com.cleantube.app.BG_START"
    const val ACTION_TOGGLE = "com.cleantube.app.BG_TOGGLE"
    const val ACTION_STOP = "com.cleantube.app.BG_STOP"

    fun start(ctx: Context, id: String, title: String, url: String, userAgent: String, startMs: Long) {
        val i = Intent(ctx, BackgroundPlayService::class.java)
            .setAction(ACTION_START)
            .putExtra("id", id)
            .putExtra("title", title)
            .putExtra("url", url)
            .putExtra("ua", userAgent)
            .putExtra("start", startMs)
        ContextCompat.startForegroundService(ctx, i)
        activeId = id
    }

    fun stop(ctx: Context) {
        ctx.stopService(Intent(ctx, BackgroundPlayService::class.java))
        activeId = null
    }
}

/**
 * Plays the audio track of a video with the system MediaPlayer inside a foreground service,
 * so the sound keeps going when the screen is off or the app is closed.
 * A notification offers Pause / Play and Stop.
 */
class BackgroundPlayService : Service() {
    private var player: MediaPlayer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var videoId = ""
    private var title = ""
    private var prepared = false
    private var pausedByFocus = false
    private var noisyRegistered = false

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    @Suppress("DEPRECATION")
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val p = player ?: return@OnAudioFocusChangeListener
        if (!prepared) return@OnAudioFocusChangeListener
        if ((change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) && p.isPlaying) {
            p.pause()
            pausedByFocus = true
            refreshNotification()
        } else if (change == AudioManager.AUDIOFOCUS_GAIN && pausedByFocus) {
            pausedByFocus = false
            p.start()
            refreshNotification()
        }
    }

    /** Headphones unplugged: pause instead of blasting the speaker. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val p = player ?: return
            if (prepared && p.isPlaying) {
                p.pause()
                refreshNotification()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        when (intent?.action) {
            BackgroundPlay.ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            BackgroundPlay.ACTION_TOGGLE -> {
                val p = player
                if (p != null && prepared) {
                    if (p.isPlaying) p.pause() else { p.start(); pausedByFocus = false }
                    refreshNotification()
                }
                return START_NOT_STICKY
            }
        }

        // ACTION_START
        val url = intent?.getStringExtra("url").orEmpty()
        if (url.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        videoId = intent?.getStringExtra("id").orEmpty()
        title = intent?.getStringExtra("title").orEmpty().ifEmpty { "Voidstream" }
        val ua = intent?.getStringExtra("ua").orEmpty().ifEmpty { Np.USER_AGENT }
        val startMs = intent?.getLongExtra("start", 0L) ?: 0L

        startAsForeground(buildNotification(playing = true, loading = true))
        BackgroundPlay.activeId = videoId
        begin(url, ua, startMs)
        return START_NOT_STICKY
    }

    private fun begin(url: String, ua: String, startMs: Long) {
        releasePlayer()
        prepared = false
        pausedByFocus = false
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            if (Build.VERSION.SDK_INT >= 23) {
                mp.setDataSource(RangeSource(url, ua))
            } else {
                mp.setDataSource(applicationContext, Uri.parse(url), mapOf("User-Agent" to ua))
            }
            mp.setOnPreparedListener {
                prepared = true
                if (startMs > 0) it.seekTo(startMs.toInt())
                requestFocus()
                it.start()
                refreshNotification()
            }
            mp.setOnCompletionListener { stopSelf() }
            mp.setOnErrorListener { _, _, _ ->
                Toast.makeText(applicationContext, "Background play failed. Try again.", Toast.LENGTH_LONG).show()
                stopSelf()
                true
            }
            acquireWifiLock()
            registerNoisy()
            mp.prepareAsync()
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Background play failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        runCatching {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "cleantube:bg").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyRegistered = true
    }

    private fun releasePlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private fun startAsForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun refreshNotification() {
        val playing = player?.let { prepared && it.isPlaying } ?: false
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(playing = playing, loading = !prepared))
    }

    private fun buildNotification(playing: Boolean, loading: Boolean): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        val toggle = PendingIntent.getService(
            this, 11, Intent(this, BackgroundPlayService::class.java).setAction(BackgroundPlay.ACTION_TOGGLE), flags
        )
        val stop = PendingIntent.getService(
            this, 12, Intent(this, BackgroundPlayService::class.java).setAction(BackgroundPlay.ACTION_STOP), flags
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (playing) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentTitle(title)
            .setContentText(if (loading) "Loading…" else if (playing) "Playing in background" else "Paused")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
        if (!loading) b.addAction(0, if (playing) "Pause" else "Play", toggle)
        b.addAction(0, "Stop", stop)
        return b.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Background play", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        BackgroundPlay.activeId = null
        if (noisyRegistered) {
            runCatching { unregisterReceiver(noisyReceiver) }
            noisyRegistered = false
        }
        releasePlayer()
        audioManager.abandonAudioFocus(focusListener)
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wifiLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "background_play"
        const val NOTIF_ID = 43
    }
}

/**
 * Feeds googlevideo.com to the MediaPlayer in 2 MB range requests (same trick the downloader uses:
 * YouTube accepts the range either as a `range=a-b` URL parameter or as a Range header, depending on the link).
 */
@androidx.annotation.RequiresApi(23)
private class RangeSource(private val url: String, private val ua: String) : MediaDataSource() {
    private var total = -2L // -2 = not asked yet, -1 = unknown
    private var cacheStart = -1L
    private var cache = ByteArray(0)

    private fun isGoogleVideo() = url.contains("googlevideo.com") || url.contains("/videoplayback")

    private fun withRangeParam(start: Long, end: Long): String {
        val clean = url.replace(Regex("([?&])range=[^&]*&?"), "$1").trimEnd('&', '?')
        return clean + (if (clean.contains("?")) "&" else "?") + "range=$start-$end"
    }

    private fun setHeaders(conn: HttpURLConnection) {
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", ua)
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5")
        if (ua == Np.USER_AGENT && isGoogleVideo()) {
            conn.setRequestProperty("Origin", "https://www.youtube.com")
            conn.setRequestProperty("Referer", "https://www.youtube.com/")
        }
    }

    private fun probeSize(): Long {
        Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()?.let { if (it > 0) return it }
        for (paramStyle in listOf(false, true)) {
            val conn = URL(if (paramStyle) withRangeParam(0, 0) else url).openConnection() as HttpURLConnection
            try {
                setHeaders(conn)
                if (!paramStyle) conn.setRequestProperty("Range", "bytes=0-0")
                conn.responseCode
                val t = conn.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1
                if (t > 0) return t
            } catch (e: Exception) {
                // try the other style
            } finally {
                conn.disconnect()
            }
        }
        return -1
    }

    @Synchronized
    override fun getSize(): Long {
        if (total == -2L) total = probeSize()
        return total
    }

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size <= 0) return 0
        val t = getSize()
        if (t > 0 && position >= t) return -1
        if (cacheStart < 0 || position < cacheStart || position >= cacheStart + cache.size) load(position, t)
        val rel = (position - cacheStart).toInt()
        val n = minOf(size, cache.size - rel)
        if (n <= 0) return -1
        System.arraycopy(cache, rel, buffer, offset, n)
        return n
    }

    private fun load(start: Long, t: Long) {
        var end = start + BLOCK - 1
        if (t > 0) end = minOf(end, t - 1)
        var last: Exception? = null
        for (attempt in 0 until 3) {
            val paramStyle = if (isGoogleVideo()) attempt % 2 == 0 else false
            try {
                val conn = URL(if (paramStyle) withRangeParam(start, end) else url).openConnection() as HttpURLConnection
                try {
                    setHeaders(conn)
                    if (!paramStyle) conn.setRequestProperty("Range", "bytes=$start-$end")
                    val code = conn.responseCode
                    if (code != 206 && code != 200) throw IOException("HTTP $code")
                    if (code == 200 && start > 0) throw IOException("The server ignored the byte range.")
                    val want = (end - start + 1).toInt()
                    val out = ByteArray(want)
                    var got = 0
                    conn.inputStream.use { input ->
                        while (got < want) {
                            val n = input.read(out, got, want - got)
                            if (n < 0) break
                            got += n
                        }
                    }
                    if (got == 0) throw IOException("Empty answer")
                    cache = if (got == want) out else out.copyOf(got)
                    cacheStart = start
                    return
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                last = e
                if (attempt < 2) Thread.sleep(400L * (attempt + 1))
            }
        }
        throw IOException(last?.message ?: "Couldn't read the audio.")
    }

    override fun close() {
        cache = ByteArray(0)
        cacheStart = -1
    }

    private companion object {
        const val BLOCK = 2L * 1024 * 1024
    }
}
