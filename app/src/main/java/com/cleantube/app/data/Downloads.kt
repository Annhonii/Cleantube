package com.cleantube.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

enum class DlStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

/** [where] is "app" (private folder of this app) or "phone" (public Movies / Music folder). */
class DownloadItem(
    val id: String,
    val videoId: String,
    val title: String,
    var label: String,
    var ext: String,
    var mime: String,
    val audioOnly: Boolean,
    val where: String,
    var url: String,
    var userAgent: String,
    var size: Long,
    var audioUrl: String = "",
    var audioSize: Long = 0,
    var merge: Boolean = false,
    val threads: Int = 1,
    status: DlStatus = DlStatus.QUEUED,
    var location: String = "",
    bytes: Long = 0,
    var error: String = "",
    var audioUserAgent: String = "",
    /** Sub-folder (named after the playlist) inside Voidstream's folder; empty for a single video. */
    val folder: String = "",
    /** Playlist items are queued without links: this is the tallest picture wanted (0 = best) when they are looked up. */
    val maxHeight: Int = 0,
    /** Queue order: the lowest waiting number goes first. */
    val seq: Long = Downloads.nextSeq()
) {
    var status by mutableStateOf(status)
    var progress by mutableFloatStateOf(0f)
    /** Bytes transferred so far / total bytes of all files of this download (0 = not known yet). */
    var bytes by mutableLongStateOf(bytes)
    var total by mutableLongStateOf(size.coerceAtLeast(0))
    /** Current speed in bytes per second and estimated seconds left (-1 = not known yet). */
    var speed by mutableLongStateOf(0L)
    var etaSec by mutableLongStateOf(-1L)
    /** "Merging…" / "Saving…" while the transfer itself is finished. */
    var phase by mutableStateOf("")
    @Volatile var cancelled = false
}

object Downloads {
    val items = mutableStateListOf<DownloadItem>()
    private lateinit var sp: android.content.SharedPreferences
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        sp = context.applicationContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val arr = try { JSONArray(sp.getString("items", "[]")) } catch (e: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val st = runCatching { DlStatus.valueOf(o.optString("status")) }.getOrDefault(DlStatus.FAILED)
            if (st != DlStatus.DONE) continue // unfinished downloads don't survive a restart
            items.add(
                DownloadItem(
                    o.optString("id"), o.optString("vid"), o.optString("title"), o.optString("label"),
                    o.optString("ext"), o.optString("mime"), o.optBoolean("audio"), o.optString("where"),
                    "", "", o.optLong("size"), "", 0, false, 1, DlStatus.DONE, o.optString("loc"), o.optLong("size"),
                    folder = o.optString("folder")
                ).also { it.progress = 1f }
            )
        }
    }

    fun save() {
        val a = JSONArray()
        items.filter { it.status == DlStatus.DONE }.forEach {
            a.put(
                JSONObject().put("id", it.id).put("vid", it.videoId).put("title", it.title).put("label", it.label)
                    .put("ext", it.ext).put("mime", it.mime).put("audio", it.audioOnly).put("where", it.where)
                    .put("size", it.bytes).put("loc", it.location).put("status", "DONE").put("folder", it.folder)
            )
        }
        sp.edit().putString("items", a.toString()).apply()
    }

    private val seqCounter = AtomicLong(0)
    fun nextSeq(): Long = seqCounter.incrementAndGet()

    /** Folder name for a playlist: no characters a file system refuses, no ".." tricks, never empty. */
    fun folderName(playlistTitle: String): String {
        val cleaned = playlistTitle.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim().trim('.', ' ').take(80).trim().trim('.')
        return cleaned.ifEmpty { "Playlist" }
    }

    /**
     * Queues every video of a playlist, saved in its own folder (Movies/Voidstream/<playlist>, or Music/ for audio).
     * The download links are NOT looked up now: a long playlist takes hours and links expire, and asking YouTube for
     * hundreds of videos at once gets the network blocked. Each item gets its link when its turn comes (see [run]).
     * [maxHeight] 0 = best quality.
     */
    fun enqueuePlaylist(
        context: Context, playlistTitle: String, videos: List<VideoItem>,
        audioOnly: Boolean, maxHeight: Int, where: String, threads: Int
    ) {
        val folder = folderName(playlistTitle)
        val label = when {
            audioOnly -> "Audio only"
            maxHeight > 0 -> "Up to ${maxHeight}p"
            else -> "Best quality"
        }
        val made = videos.map { v ->
            DownloadItem(
                UUID.randomUUID().toString(), v.id, v.title.ifEmpty { v.id }, label,
                if (audioOnly) "m4a" else "mp4", if (audioOnly) "audio/mp4" else "video/mp4", audioOnly, where,
                "", "", -1, threads = threads.coerceIn(1, 32), folder = folder, maxHeight = maxHeight
            )
        }
        items.addAll(0, made) // shown in playlist order; the first video has the lowest queue number
        startService(context)
    }

    /** Drops every download that has not started yet (a big playlist is otherwise removed one by one). */
    fun removeWaiting() {
        items.removeAll { it.status == DlStatus.QUEUED }
        save()
    }

    fun enqueue(context: Context, videoId: String, title: String, o: StreamOption, where: String, threads: Int) {
        val item = DownloadItem(
            UUID.randomUUID().toString(), videoId, title, o.label, o.ext, o.mime, o.audioOnly, where,
            o.url, o.userAgent, o.size, o.audioUrl, o.audioSize, o.merge, threads.coerceIn(1, 32),
            audioUserAgent = o.audioUserAgent
        )
        items.add(0, item)
        startService(context)
    }

    private fun startService(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }

    /** Puts a failed / cancelled download back in the queue. */
    fun retry(context: Context, item: DownloadItem) {
        if (item.status == DlStatus.RUNNING || item.status == DlStatus.QUEUED) return
        item.cancelled = false
        item.error = ""
        item.progress = 0f
        item.bytes = 0
        item.speed = 0
        item.etaSec = -1
        item.status = DlStatus.QUEUED
        startService(context)
    }

    /** Stops a running download but keeps it in the list (so it can be retried). */
    fun cancel(item: DownloadItem) {
        item.cancelled = true
    }

    fun remove(context: Context, item: DownloadItem, deleteFile: Boolean) {
        item.cancelled = true
        if (deleteFile && item.location.isNotEmpty()) {
            runCatching {
                if (item.location.startsWith("content://")) context.contentResolver.delete(Uri.parse(item.location), null, null)
                else File(item.location).delete()
            }
        }
        items.remove(item)
        save()
    }

    /**
     * Copies a download that lives inside the app into the phone's public folder (Movies/Voidstream, or Music/Voidstream
     * for audio, with the playlist's sub-folder if it has one), so the gallery and file manager can see it. The copy
     * inside the app stays where it is. Returns the folder it was saved to; throws [java.io.IOException] with a
     * readable message when it can't.
     */
    suspend fun saveToStorage(ctx: Context, item: DownloadItem): String = withContext(Dispatchers.IO) {
        if (item.where == "phone") throw java.io.IOException("This download is already in phone storage.")
        val src = File(item.location)
        if (item.location.isEmpty() || !src.exists()) throw java.io.IOException("The file is gone (deleted or moved).")
        val name = "${safeName(item.title)} [${item.videoId}].${item.ext}"
        val base = if (item.audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val sub = "Voidstream" + (if (item.folder.isNotEmpty()) "/${item.folder}" else "")
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = if (item.audioOnly) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$base/$sub")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(collection, values) ?: throw java.io.IOException("Couldn't create the file in phone storage.")
            try {
                val out = ctx.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Couldn't open the file.")
                out.use { o -> src.inputStream().use { it.copyTo(o, 256 * 1024) } }
                ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                runCatching { ctx.contentResolver.delete(uri, null, null) }
                throw if (e is java.io.IOException) e else java.io.IOException(e.message ?: "Couldn't save the file.")
            }
        } else {
            // Android 9 and older: plain public folder (needs the storage permission)
            val dir = File(Environment.getExternalStoragePublicDirectory(base), sub)
            if (!dir.exists() && !dir.mkdirs()) throw java.io.IOException("Couldn't create the folder in phone storage.")
            val target = File(dir, name)
            try {
                src.copyTo(target, overwrite = true)
            } catch (e: Exception) {
                runCatching { target.delete() }
                throw java.io.IOException(e.message ?: "Couldn't save the file.")
            }
            runCatching { android.media.MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), arrayOf(item.mime), null) }
        }
        "$base/$sub"
    }

    fun uriOf(item: DownloadItem): Uri =
        if (item.location.startsWith("content://")) Uri.parse(item.location) else Uri.fromFile(File(item.location))

    fun exists(context: Context, item: DownloadItem): Boolean = runCatching {
        if (item.location.startsWith("content://")) context.contentResolver.openInputStream(Uri.parse(item.location))?.use { true } ?: false
        else File(item.location).exists()
    }.getOrDefault(false)

    // ------------------------------------------------------------- the actual transfer

    private fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(80).ifEmpty { "video" }

    private class Target(val out: OutputStream, val location: String, val finish: () -> Unit, val abort: () -> Unit)

    private fun openTarget(ctx: Context, item: DownloadItem): Target {
        val name = "${safeName(item.title)} [${item.videoId}].${item.ext}"
        val usePhone = item.where == "phone"
        if (usePhone && Build.VERSION.SDK_INT >= 29) {
            val collection = if (item.audioOnly) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            // a playlist gets its own sub-folder (Movies/Voidstream/<playlist>); a single video stays directly in Voidstream
            val folder = (if (item.audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/Voidstream" +
                (if (item.folder.isNotEmpty()) "/${item.folder}" else "")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(collection, values) ?: throw java.io.IOException("Couldn't create the file in phone storage.")
            val out = ctx.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Couldn't open the file.")
            return Target(
                out, uri.toString(),
                finish = {
                    val v = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    ctx.contentResolver.update(uri, v, null, null)
                },
                abort = { runCatching { ctx.contentResolver.delete(uri, null, null) } }
            )
        }
        val dir: File = if (usePhone) {
            // Android 9 and older: plain public folder (needs the storage permission; falls back below if denied)
            File(
                Environment.getExternalStoragePublicDirectory(if (item.audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES),
                "Voidstream"
            )
        } else {
            File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "downloads")
        }
        val place = if (item.folder.isNotEmpty()) File(dir, item.folder) else dir
        if (!place.exists() && !place.mkdirs()) throw java.io.IOException("Couldn't create the folder. Try \"In app\" storage.")
        val f = File(place, name)
        return Target(f.outputStream(), f.absolutePath, finish = {}, abort = { f.delete() })
    }

    private class DlCancelled : java.io.IOException("cancelled")

    /** The server (googlevideo.com) said no: the link is probably stale or was made for another client. */
    private class Refused(msg: String) : java.io.IOException(msg)

    private const val CHUNK = 2L * 1024 * 1024

    private fun isGoogleVideo(url: String) = url.contains("googlevideo.com") || url.contains("/videoplayback")

    /** YouTube accepts the byte range either as a `range=a-b` URL parameter (what NewPipe / yt-dlp use) or as a Range header. */
    private fun withRangeParam(url: String, start: Long, end: Long): String {
        val clean = url.replace(Regex("([?&])range=[^&]*&?"), "$1").trimEnd('&', '?')
        return clean + (if (clean.contains("?")) "&" else "?") + "range=$start-$end"
    }

    private fun setHeaders(conn: HttpURLConnection, url: String, ua: String) {
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", ua)
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5")
        // Links made for the website expect the site as origin; app-client links (Android / iOS) must not send it.
        if (ua == Np.USER_AGENT && isGoogleVideo(url)) {
            conn.setRequestProperty("Origin", "https://www.youtube.com")
            conn.setRequestProperty("Referer", "https://www.youtube.com/")
        }
    }

    /** Total size of a file: the `clen=` value inside the link, else asked from the server. */
    private fun probeSize(url: String, ua: String): Long {
        Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()?.let { if (it > 0) return it }
        for (paramStyle in listOf(false, true)) {
            val u = if (paramStyle) withRangeParam(url, 0, 0) else url
            val conn = URL(u).openConnection() as HttpURLConnection
            try {
                setHeaders(conn, url, ua)
                if (!paramStyle) conn.setRequestProperty("Range", "bytes=0-0")
                conn.responseCode
                val total = conn.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1
                if (total > 0) return total
            } catch (e: Exception) {
                // try the other style
            } finally {
                conn.disconnect()
            }
        }
        return -1
    }

    /**
     * One 2 MB range request, written at its own offset. Tried up to 3 times, switching between the URL-parameter
     * and the header way of asking for a range, because which one YouTube accepts depends on the link.
     */
    private fun fetchChunk(
        url: String, ua: String, start: Long, end: Long, ch: java.nio.channels.FileChannel,
        stop: () -> Boolean, done: AtomicLong, buf: ByteArray, tick: () -> Unit
    ) {
        var lastError: Exception? = null
        var refused = 0
        for (attempt in 0 until 3) {
            var got = 0L
            val paramFirst = isGoogleVideo(url)
            val paramStyle = if (paramFirst) attempt % 2 == 0 else false
            try {
                val conn = URL(if (paramStyle) withRangeParam(url, start, end) else url).openConnection() as HttpURLConnection
                try {
                    setHeaders(conn, url, ua)
                    if (!paramStyle) conn.setRequestProperty("Range", "bytes=$start-$end")
                    val code = conn.responseCode
                    if (code == 403 || code == 410 || code == 404) throw Refused("YouTube refused the download (HTTP $code).")
                    if (code != 206 && code != 200) throw java.io.IOException("YouTube answered HTTP $code.")
                    if (!paramStyle && code == 200 && start > 0) throw java.io.IOException("The server ignored the byte range.")
                    val len = conn.contentLengthLong
                    if (len > 0 && len != end - start + 1) throw java.io.IOException("The server sent the wrong part of the file.")
                    var pos = start
                    conn.inputStream.use { input ->
                        while (pos <= end) {
                            if (stop()) throw DlCancelled()
                            val want = minOf(buf.size.toLong(), end + 1 - pos).toInt()
                            val n = input.read(buf, 0, want)
                            if (n < 0) break
                            ch.write(java.nio.ByteBuffer.wrap(buf, 0, n), pos)
                            pos += n
                            got += n
                            done.addAndGet(n.toLong())
                            tick()
                        }
                    }
                    if (pos != end + 1) throw java.io.IOException("Connection ended early.")
                    return
                } finally {
                    conn.disconnect()
                }
            } catch (e: DlCancelled) {
                throw e
            } catch (e: Exception) {
                done.addAndGet(-got) // this chunk starts over
                lastError = e
                if (e is Refused) refused++
                if (stop()) throw DlCancelled()
                if (attempt < 2) Thread.sleep(400L * (attempt + 1))
            }
        }
        if (refused > 0 && lastError is Refused) throw lastError
        throw lastError ?: java.io.IOException("Download failed.")
    }

    /** Downloads one file of known [size] with [threads] parallel connections. */
    private suspend fun fetchFile(url: String, ua: String, size: Long, dest: File, threads: Int, item: DownloadItem, done: AtomicLong, tick: () -> Unit) {
        val chunks = ((size + CHUNK - 1) / CHUNK).toInt()
        val workers = threads.coerceIn(1, 32).coerceAtMost(chunks)
        val next = java.util.concurrent.atomic.AtomicInteger(0)
        val failed = java.util.concurrent.atomic.AtomicBoolean(false)
        val stop = { item.cancelled || failed.get() }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(workers)
        val raf = java.io.RandomAccessFile(dest, "rw")
        try {
            raf.setLength(size)
            val ch = raf.channel
            kotlinx.coroutines.coroutineScope {
                repeat(workers) {
                    launch(pool.asCoroutineDispatcher()) {
                        val buf = ByteArray(64 * 1024)
                        try {
                            while (true) {
                                if (stop()) throw DlCancelled()
                                val i = next.getAndIncrement()
                                if (i >= chunks) break
                                val start = i * CHUNK
                                val end = minOf(start + CHUNK, size) - 1
                                fetchChunk(url, ua, start, end, ch, stop, done, buf, tick)
                            }
                        } catch (e: Throwable) {
                            if (e !is DlCancelled) failed.set(true)
                            throw e
                        }
                    }
                }
            }
        } finally {
            pool.shutdown()
            runCatching { raf.close() }
        }
    }

    /** Speed (bytes/s) smoothed over the last few seconds. */
    private class SpeedMeter {
        private var lastT = 0L
        private var lastB = 0L
        private var ema = 0.0

        @Synchronized
        fun update(bytes: Long): Long {
            val now = SystemClock.elapsedRealtime()
            if (lastT == 0L) { lastT = now; lastB = bytes; return 0 }
            val dt = now - lastT
            if (dt < 500) return ema.toLong()
            val inst = ((bytes - lastB).coerceAtLeast(0)) * 1000.0 / dt
            ema = if (ema == 0.0) inst else ema * 0.7 + inst * 0.3
            lastT = now
            lastB = bytes
            return ema.toLong()
        }
    }

    /** Joins a picture-only and a sound-only MP4 into one file, interleaved by time. */
    private fun mux(video: File, audio: File, out: File) {
        val ve = android.media.MediaExtractor()
        val ae = android.media.MediaExtractor()
        var muxer: android.media.MediaMuxer? = null
        var started = false
        try {
            ve.setDataSource(video.absolutePath)
            ae.setDataSource(audio.absolutePath)
            fun track(ex: android.media.MediaExtractor, prefix: String): Int {
                for (i in 0 until ex.trackCount) {
                    if (ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME)?.startsWith(prefix) == true) return i
                }
                return -1
            }
            val vt = track(ve, "video/")
            val at = track(ae, "audio/")
            if (vt < 0 || at < 0) throw java.io.IOException("Couldn't read the downloaded picture or sound.")
            ve.selectTrack(vt)
            ae.selectTrack(at)
            val m = android.media.MediaMuxer(out.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val vi = m.addTrack(ve.getTrackFormat(vt))
            val ai = m.addTrack(ae.getTrackFormat(at))
            m.start()
            started = true
            val buf = java.nio.ByteBuffer.allocate(8 * 1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            var vDone = false
            var aDone = false
            while (!vDone || !aDone) {
                val useVideo = !vDone && (aDone || ve.sampleTime <= ae.sampleTime)
                val ex = if (useVideo) ve else ae
                buf.clear()
                val n = ex.readSampleData(buf, 0)
                if (n < 0) {
                    if (useVideo) vDone = true else aDone = true
                    continue
                }
                info.set(0, n, ex.sampleTime, ex.sampleFlags)
                m.writeSampleData(if (useVideo) vi else ai, buf, info)
                ex.advance()
            }
        } finally {
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { ve.release() }
            runCatching { ae.release() }
        }
    }

    /** YouTube is refusing this network: stop asking for the links of the other waiting playlist items. */
    private fun pauseWaiting(except: DownloadItem) {
        items.filter { it !== except && it.status == DlStatus.QUEUED && it.url.isEmpty() }.forEach {
            it.error = "Paused: YouTube is refusing this network right now. Tap Retry in a few minutes."
            it.status = DlStatus.FAILED
        }
    }

    /** Playlist items are queued without links; this finds them (and the real size / format) when the item's turn comes. */
    private suspend fun resolveLinks(item: DownloadItem, onProgress: () -> Unit) {
        item.phase = "Getting the link…"
        onProgress()
        val info = try {
            Streams.fetch(item.videoId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Couldn't get the download link."
            if (Streams.isBlock(msg)) pauseWaiting(item)
            throw java.io.IOException(msg)
        }
        if (item.cancelled) throw DlCancelled()
        val o = if (item.audioOnly) info.options.firstOrNull { it.audioOnly }
        else Playback.pick(info, if (item.maxHeight > 0) item.maxHeight else Int.MAX_VALUE)
        if (o == null) {
            throw java.io.IOException(
                if (item.audioOnly) "YouTube offers no audio file for this video." else "YouTube offers no video file for this video."
            )
        }
        item.label = o.label
        item.ext = o.ext
        item.mime = o.mime
        item.size = o.size
        item.url = o.url
        item.userAgent = o.userAgent
        item.audioUrl = o.audioUrl
        item.audioSize = o.audioSize
        item.audioUserAgent = o.audioUserAgent
        item.merge = o.merge
        item.phase = ""
    }

    /** Asks the NewPipe engine for fresh links of the same quality (links expire or get refused). */
    private suspend fun refreshLinks(item: DownloadItem): Boolean = try {
        val info = Streams.fetch(item.videoId)
        val match = info.options.firstOrNull { it.label == item.label && it.audioOnly == item.audioOnly }
            ?: info.options.firstOrNull { it.audioOnly == item.audioOnly && it.merge == item.merge && it.ext == item.ext }
        if (match == null) false else {
            item.url = match.url
            item.userAgent = match.userAgent
            item.audioUrl = match.audioUrl
            item.audioUserAgent = match.audioUserAgent
            true
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** Transfers the file(s) of [item] into [work]; returns the finished file (joined when picture and sound are separate). */
    private suspend fun transfer(item: DownloadItem, work: File, onProgress: () -> Unit): File {
        val mainUa = item.userAgent.ifEmpty { Np.USER_AGENT }
        val audioUa = item.audioUserAgent.ifEmpty { mainUa }

        item.phase = "Starting…"
        onProgress()
        val mainKnown = if (item.merge) item.size - item.audioSize else item.size
        val mainSize = if (mainKnown > 0) mainKnown else probeSize(item.url, mainUa)
        if (mainSize <= 0) throw java.io.IOException("YouTube didn't say how big the file is.")
        var audioSize = 0L
        if (item.merge) {
            audioSize = if (item.audioSize > 0) item.audioSize else probeSize(item.audioUrl, audioUa)
            if (audioSize <= 0) throw java.io.IOException("YouTube didn't say how big the sound file is.")
        }
        val total = mainSize + audioSize
        item.total = total
        item.phase = ""

        val done = AtomicLong(0)
        val meter = SpeedMeter()
        val lastUi = AtomicLong(0)
        val tick = {
            val now = SystemClock.elapsedRealtime()
            if (now - lastUi.get() >= 250) {
                lastUi.set(now)
                val d = done.get().coerceAtLeast(0)
                item.bytes = d
                item.progress = (d.toFloat() / total).coerceIn(0f, 0.99f)
                val sp = meter.update(d)
                item.speed = sp
                item.etaSec = if (sp > 1024) ((total - d) / sp).coerceAtLeast(0) else -1
                onProgress()
            }
        }

        val first = File(work, "main.tmp")
        fetchFile(item.url, mainUa, mainSize, first, item.threads, item, done, tick)
        if (!item.merge) return first
        val sound = File(work, "audio.tmp")
        fetchFile(item.audioUrl, audioUa, audioSize, sound, item.threads, item, done, tick)
        item.speed = 0
        item.etaSec = -1
        item.phase = "Merging…"
        item.progress = 0.99f
        onProgress()
        val joined = File(work, "joined.mp4")
        mux(first, sound, joined)
        return joined
    }

    /** Downloads (several connections at once), joins picture and sound if needed, then saves to the chosen place. */
    suspend fun run(ctx: Context, item: DownloadItem, onProgress: () -> Unit) = withContext(Dispatchers.IO) {
        item.status = DlStatus.RUNNING
        item.error = ""
        val work = File(ctx.cacheDir, "dl/${item.id}")
        var target: Target? = null
        try {
            work.deleteRecursively()
            work.mkdirs()
            if (item.url.isEmpty()) resolveLinks(item, onProgress) // playlist item: its turn has come
            var source: File
            try {
                source = transfer(item, work, onProgress)
            } catch (e: Refused) {
                // Stale link: get fresh ones from the NewPipe engine and try once more.
                item.phase = "Getting a fresh link…"
                onProgress()
                if (item.cancelled || !refreshLinks(item)) throw e
                work.deleteRecursively()
                work.mkdirs()
                item.bytes = 0
                source = transfer(item, work, onProgress)
            }
            if (item.cancelled) throw DlCancelled()
            item.phase = "Saving…"
            item.speed = 0
            item.etaSec = -1
            onProgress()
            val t = openTarget(ctx, item)
            target = t
            source.inputStream().use { input -> input.copyTo(t.out, 256 * 1024) }
            t.out.close()
            t.finish()
            item.location = t.location
            item.bytes = source.length()
            item.progress = 1f
            item.status = DlStatus.DONE
        } catch (e: DlCancelled) {
            runCatching { target?.out?.close() }
            target?.abort?.invoke()
            item.status = DlStatus.CANCELLED
        } catch (e: kotlinx.coroutines.CancellationException) {
            runCatching { target?.out?.close() }
            target?.abort?.invoke()
            item.error = "Interrupted."
            item.status = DlStatus.FAILED
            work.deleteRecursively()
            throw e
        } catch (e: Exception) {
            runCatching { target?.out?.close() }
            target?.abort?.invoke()
            item.error = e.message ?: "Download failed."
            item.status = DlStatus.FAILED
        } finally {
            item.phase = ""
            item.speed = 0
            item.etaSec = -1
            work.deleteRecursively()
        }
        save()
    }
}
