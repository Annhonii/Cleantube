package com.cleantube.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
    val label: String,
    val ext: String,
    val mime: String,
    val audioOnly: Boolean,
    val where: String,
    val url: String,
    val userAgent: String,
    val size: Long,
    val audioUrl: String = "",
    val audioSize: Long = 0,
    val merge: Boolean = false,
    val threads: Int = 1,
    status: DlStatus = DlStatus.QUEUED,
    var location: String = "",
    var bytes: Long = 0,
    var error: String = ""
) {
    var status by androidx.compose.runtime.mutableStateOf(status)
    var progress by androidx.compose.runtime.mutableStateOf(0f)
    /** "Merging…" / "Saving…" while the transfer itself is finished. */
    var phase by androidx.compose.runtime.mutableStateOf("")
    var cancelled = false
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
                    "", "", o.optLong("size"), "", 0, false, 1, DlStatus.DONE, o.optString("loc"), o.optLong("size")
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
                    .put("size", it.bytes).put("loc", it.location).put("status", "DONE")
            )
        }
        sp.edit().putString("items", a.toString()).apply()
    }

    fun enqueue(context: Context, videoId: String, title: String, o: StreamOption, where: String, threads: Int) {
        val item = DownloadItem(
            UUID.randomUUID().toString(), videoId, title, o.label, o.ext, o.mime, o.audioOnly, where,
            o.url, o.userAgent, o.size, o.audioUrl, o.audioSize, o.merge, threads.coerceIn(1, 32)
        )
        items.add(0, item)
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
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
            val folder = (if (item.audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/CleanTube"
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
                "CleanTube"
            )
        } else {
            File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "downloads")
        }
        if (!dir.exists() && !dir.mkdirs()) throw java.io.IOException("Couldn't create the folder. Try \"In app\" storage.")
        val f = File(dir, name)
        return Target(f.outputStream(), f.absolutePath, finish = {}, abort = { f.delete() })
    }

    private class DlCancelled : java.io.IOException("cancelled")

    private const val CHUNK = 2L * 1024 * 1024

    /** Total size of a file, asking the server when the format list didn't say. */
    private fun probeSize(url: String, ua: String): Long {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", ua)
            conn.setRequestProperty("Range", "bytes=0-0")
            conn.responseCode
            return conn.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1
        } finally {
            conn.disconnect()
        }
    }

    /** One 2 MB range request, written at its own offset; retried a few times. */
    private fun fetchChunk(url: String, ua: String, start: Long, end: Long, ch: java.nio.channels.FileChannel, item: DownloadItem, done: AtomicLong, buf: ByteArray, tick: () -> Unit) {
        var lastError: Exception? = null
        repeat(3) {
            var got = 0L
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 20_000
                    conn.setRequestProperty("User-Agent", ua)
                    conn.setRequestProperty("Range", "bytes=$start-$end")
                    val code = conn.responseCode
                    if (code != 206 && code != 200) throw java.io.IOException("YouTube refused the download (HTTP $code).")
                    var pos = start
                    conn.inputStream.use { input ->
                        while (true) {
                            if (item.cancelled) throw DlCancelled()
                            val n = input.read(buf)
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
            }
        }
        throw lastError ?: java.io.IOException("Download failed.")
    }

    /** Downloads one file with [threads] parallel connections. */
    private suspend fun fetchFile(url: String, ua: String, knownSize: Long, dest: File, threads: Int, item: DownloadItem, done: AtomicLong, grand: () -> Long, onProgress: () -> Unit) {
        val size = if (knownSize > 0) knownSize else probeSize(url, ua)
        if (size <= 0) throw java.io.IOException("YouTube didn't say how big the file is.")
        val chunks = ((size + CHUNK - 1) / CHUNK).toInt()
        val workers = threads.coerceIn(1, 32).coerceAtMost(chunks)
        val next = java.util.concurrent.atomic.AtomicInteger(0)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(workers)
        val raf = java.io.RandomAccessFile(dest, "rw")
        try {
            raf.setLength(size)
            val ch = raf.channel
            val tick = {
                val g = grand()
                if (g > 0) item.progress = (done.get().toFloat() / g).coerceIn(0f, 0.99f)
                item.bytes = done.get()
                onProgress()
            }
            kotlinx.coroutines.coroutineScope {
                repeat(workers) {
                    launch(pool.asCoroutineDispatcher()) {
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (item.cancelled) throw DlCancelled()
                            val i = next.getAndIncrement()
                            if (i >= chunks) break
                            val start = i * CHUNK
                            val end = minOf(start + CHUNK, size) - 1
                            fetchChunk(url, ua, start, end, ch, item, done, buf, tick)
                        }
                    }
                }
            }
        } finally {
            pool.shutdown()
            runCatching { raf.close() }
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

    /** Downloads (several connections at once), joins picture and sound if needed, then saves to the chosen place. */
    suspend fun run(ctx: Context, item: DownloadItem, onProgress: () -> Unit) = withContext(Dispatchers.IO) {
        item.status = DlStatus.RUNNING
        val work = File(ctx.cacheDir, "dl/${item.id}")
        var target: Target? = null
        try {
            work.mkdirs()
            val done = AtomicLong(0)
            val grand = if (item.size > 0) item.size else 0L
            val first = File(work, "main.tmp")
            fetchFile(item.url, item.userAgent, if (item.merge) item.size - item.audioSize else item.size, first, item.threads, item, done, { grand }, onProgress)
            var source = first
            if (item.merge) {
                val sound = File(work, "audio.tmp")
                fetchFile(item.audioUrl, item.userAgent, item.audioSize, sound, item.threads, item, done, { grand }, onProgress)
                item.phase = "Merging…"
                item.progress = 0.99f
                onProgress()
                val joined = File(work, "joined.mp4")
                mux(first, sound, joined)
                source = joined
            }
            if (item.cancelled) throw DlCancelled()
            item.phase = "Saving…"
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
            work.deleteRecursively()
        }
        save()
    }
}
