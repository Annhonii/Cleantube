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
    status: DlStatus = DlStatus.QUEUED,
    var location: String = "",
    var bytes: Long = 0,
    var error: String = ""
) {
    var status by androidx.compose.runtime.mutableStateOf(status)
    var progress by androidx.compose.runtime.mutableStateOf(0f)
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
                    "", "", o.optLong("size"), DlStatus.DONE, o.optString("loc"), o.optLong("size")
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

    fun enqueue(context: Context, videoId: String, title: String, o: StreamOption, where: String) {
        val item = DownloadItem(
            UUID.randomUUID().toString(), videoId, title, o.label, o.ext, o.mime, o.audioOnly, where,
            o.url, o.userAgent, o.size
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

    /** Downloads in 4 MB range requests (what YouTube's own apps do; it also avoids throttling). */
    suspend fun run(ctx: Context, item: DownloadItem, onProgress: () -> Unit) = withContext(Dispatchers.IO) {
        item.status = DlStatus.RUNNING
        var target: Target? = null
        try {
            val t = openTarget(ctx, item)
            target = t
            var total = item.size
            var pos = 0L
            val chunk = 4L * 1024 * 1024
            val buf = ByteArray(64 * 1024)
            while (total < 0 || pos < total) {
                if (item.cancelled) throw java.util.concurrent.CancellationException("cancelled")
                val end = if (total > 0) minOf(pos + chunk, total) - 1 else pos + chunk - 1
                val conn = URL(item.url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 20_000
                    conn.setRequestProperty("User-Agent", item.userAgent)
                    conn.setRequestProperty("Range", "bytes=$pos-$end")
                    val code = conn.responseCode
                    if (code != 206 && code != 200) throw java.io.IOException("Download refused by YouTube (HTTP $code).")
                    if (total < 0) {
                        conn.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { total = it }
                    }
                    var got = 0L
                    conn.inputStream.use { input ->
                        while (true) {
                            if (item.cancelled) throw java.util.concurrent.CancellationException("cancelled")
                            val n = input.read(buf)
                            if (n < 0) break
                            t.out.write(buf, 0, n)
                            got += n
                            item.bytes = pos + got
                            if (total > 0) item.progress = (item.bytes.toFloat() / total).coerceIn(0f, 1f)
                            onProgress()
                        }
                    }
                    if (got == 0L) break
                    pos += got
                    if (code == 200) break // server ignored the range and sent everything
                } finally {
                    conn.disconnect()
                }
            }
            t.out.close()
            t.finish()
            item.location = t.location
            item.progress = 1f
            item.status = DlStatus.DONE
        } catch (e: java.util.concurrent.CancellationException) {
            runCatching { target?.out?.close() }
            target?.abort?.invoke()
            item.status = DlStatus.CANCELLED
        } catch (e: Exception) {
            runCatching { target?.out?.close() }
            target?.abort?.invoke()
            item.error = e.message ?: "Download failed."
            item.status = DlStatus.FAILED
        }
        save()
    }
}
