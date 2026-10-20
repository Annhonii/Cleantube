package com.cleantube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One downloadable file of a video. */
data class StreamOption(
    val itag: Int,
    val label: String,
    val audioOnly: Boolean,
    val ext: String,
    val mime: String,
    val size: Long,
    val url: String,
    val userAgent: String,
    /** For "merge" options: a second file (the sound) that is joined with [url] (the picture) after download. */
    val audioUrl: String = "",
    val audioSize: Long = 0,
    /** User-Agent for [audioUrl]; empty = same as [userAgent]. */
    val audioUserAgent: String = "",
    val merge: Boolean = false
)

/** [hlsUrl] is set for live streams (no plain files exist for them); empty otherwise. */
data class StreamInfo(val title: String, val options: List<StreamOption>, val hlsUrl: String = "")

/**
 * Gets direct media links. First choice is the NewPipe engine ([Np]); if it fails (YouTube changed something it
 * doesn't know yet) the older built-in method below is tried as a fallback.
 *
 * The fallback gets links from YouTube's "player" endpoint.
 *
 * It asks as a few different official-app clients (VR, iOS, Android) because YouTube changes which of them
 * hand out plain links. Offers every H.264 height (picture and sound are downloaded separately and joined on the phone),
 * ready-made files if YouTube gives any, and audio-only files. VP9 / AV1 (usually 1440p and above) are not offered.
 */
object Streams {

    private class Client(val name: String, val id: Int, val version: String, val ua: String, val extra: JSONObject.() -> Unit)

    private val clients = listOf(
        Client(
            "ANDROID_VR", 28, "1.60.19",
            "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        ) {
            put("deviceMake", "Oculus"); put("deviceModel", "Quest 3")
            put("osName", "Android"); put("osVersion", "12L"); put("androidSdkVersion", 32)
        },
        Client(
            "IOS", 5, "20.10.4",
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
        ) {
            put("deviceMake", "Apple"); put("deviceModel", "iPhone16,2")
            put("osName", "iPhone"); put("osVersion", "18.3.2.22D82")
        },
        Client(
            "ANDROID", 3, "19.44.38",
            "com.google.android.youtube/19.44.38 (Linux; U; Android 11) gzip"
        ) {
            put("osName", "Android"); put("osVersion", "11"); put("androidSdkVersion", 30)
        }
    )

    private val blockRegex = Regex(
        "not a bot|unusual traffic|captcha|too many requests|HTTP 429|temporarily blocked|IP.{0,30}blocked|blocked.{0,30}IP",
        RegexOption.IGNORE_CASE
    )

    /** True when [msg] looks like YouTube refusing this network / IP (bot check, captcha, rate limit). */
    fun isBlock(msg: String?): Boolean = !msg.isNullOrEmpty() && blockRegex.containsMatchIn(msg)

    fun blockMessage(original: String?): String {
        val o = original.orEmpty()
        if (o.startsWith("YouTube said:")) return o   // already wrapped
        return blockText(o)
    }

    private fun blockText(original: String): String =
        "YouTube said: ${original.take(300)}\n\n" +
            "Your network looks temporarily blocked (bot check / too many requests). " +
            "Wait a few minutes or switch between Wi-Fi and mobile data, then retry."

    suspend fun fetch(videoId: String): StreamInfo {
        val npProblem = try {
            return Np.fetch(videoId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.message ?: e.javaClass.simpleName
        }
        // The IP is being refused: asking again through the legacy path only makes the block last longer.
        if (isBlock(npProblem)) throw ApiException(ApiException.Kind.OTHER, blockMessage(npProblem))
        return try {
            fetchLegacy(videoId)
        } catch (e: ApiException) {
            throw ApiException(ApiException.Kind.OTHER, "NewPipe engine: $npProblem\nFallback: ${e.message}")
        }
    }

    /** Fast path: a single request to YouTube's player endpoint (no page, no script unscrambling). */
    suspend fun fetchFast(videoId: String): StreamInfo = fetchLegacy(videoId)

    private suspend fun fetchLegacy(videoId: String): StreamInfo = withContext(Dispatchers.IO) {
        var lastProblem = "YouTube didn't give a download link for this video."
        for (c in clients) {
            try {
                val j = request(c, videoId)
                val status = j.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
                if (status != "OK") {
                    val reason = j.optJSONObject("playabilityStatus")?.optString("reason").orEmpty()
                    if (reason.isNotEmpty()) lastProblem = reason
                    // Same IP for every client: once YouTube refuses the network, stop asking.
                    if (isBlock(reason)) break
                    continue
                }
                val opts = options(j, c.ua)
                if (opts.isNotEmpty()) {
                    return@withContext StreamInfo(j.optJSONObject("videoDetails")?.optString("title").orEmpty(), opts)
                }
            } catch (e: Exception) {
                lastProblem = e.message ?: lastProblem
                if (isBlock(lastProblem)) break
            }
        }
        throw ApiException(
            ApiException.Kind.OTHER,
            if (isBlock(lastProblem)) blockMessage(lastProblem) else lastProblem
        )
    }

    private fun request(c: Client, videoId: String): JSONObject {
        val client = JSONObject()
            .put("clientName", c.name).put("clientVersion", c.version)
            .put("hl", "en").put("gl", Graph.store.region)
        c.extra(client)
        val body = JSONObject()
            .put("videoId", videoId)
            .put("contentCheckOk", true).put("racyCheckOk", true)
            .put("context", JSONObject().put("client", client))
        val conn = URL("https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", c.ua)
            conn.setRequestProperty("X-YouTube-Client-Name", c.id.toString())
            conn.setRequestProperty("X-YouTube-Client-Version", c.version)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException(ApiException.Kind.OTHER, "YouTube answered HTTP $code")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun options(j: JSONObject, ua: String): List<StreamOption> {
        val sd = j.optJSONObject("streamingData") ?: return emptyList()
        val out = ArrayList<StreamOption>()

        // muxed: picture + sound in one file
        val formats = sd.optJSONArray("formats")
        for (i in 0 until (formats?.length() ?: 0)) {
            val f = formats!!.optJSONObject(i) ?: continue
            val url = f.optString("url")
            if (url.isEmpty()) continue // ciphered links need signature decoding: not supported
            val mime = f.optString("mimeType")
            out += StreamOption(
                itag = f.optInt("itag"),
                label = (f.optString("qualityLabel").ifEmpty { "Video" }) + " video + sound",
                audioOnly = false,
                ext = if (mime.contains("webm")) "webm" else "mp4",
                mime = mime.substringBefore(";"),
                size = f.optString("contentLength").toLongOrNull() ?: -1,
                url = url,
                userAgent = ua
            )
        }

        val adaptive = sd.optJSONArray("adaptiveFormats")
        val audio = ArrayList<JSONObject>()
        val video = ArrayList<JSONObject>()
        for (i in 0 until (adaptive?.length() ?: 0)) {
            val f = adaptive!!.optJSONObject(i) ?: continue
            if (f.optString("url").isEmpty()) continue
            val mime = f.optString("mimeType")
            if (mime.startsWith("audio/")) audio.add(f)
            else if (mime.startsWith("video/mp4") && mime.contains("avc1")) video.add(f)
        }
        val bestM4a = audio.filter { it.optString("mimeType").startsWith("audio/mp4") }.maxByOrNull { it.optInt("bitrate") }

        // picture-only tracks (every height) + the best m4a sound, joined on the phone after download
        if (bestM4a != null) {
            val audioSize = bestM4a.optString("contentLength").toLongOrNull() ?: 0L
            video.groupBy { it.optInt("height") }.forEach { (_, group) ->
                val f = group.maxByOrNull { it.optInt("bitrate") } ?: return@forEach
                val vSize = f.optString("contentLength").toLongOrNull() ?: 0L
                out += StreamOption(
                    itag = f.optInt("itag"),
                    label = f.optString("qualityLabel").ifEmpty { "${f.optInt("height")}p" } + " video + sound (MP4)",
                    audioOnly = false,
                    ext = "mp4",
                    mime = "video/mp4",
                    size = if (vSize > 0) vSize + audioSize else -1,
                    url = f.optString("url"),
                    userAgent = ua,
                    audioUrl = bestM4a.optString("url"),
                    audioSize = audioSize,
                    merge = true
                )
            }
        }

        // audio only: the best m4a and the best webm/opus
        listOf("audio/mp4" to "m4a", "audio/webm" to "webm").forEach { (prefix, ext) ->
            val best = audio.filter { it.optString("mimeType").startsWith(prefix) }.maxByOrNull { it.optInt("bitrate") } ?: return@forEach
            out += StreamOption(
                itag = best.optInt("itag"),
                label = "Audio only (${best.optInt("bitrate") / 1000} kbps ${ext})",
                audioOnly = true,
                ext = ext,
                mime = best.optString("mimeType").substringBefore(";"),
                size = best.optString("contentLength").toLongOrNull() ?: -1,
                url = best.optString("url"),
                userAgent = ua
            )
        }
        return out.sortedWith(compareBy<StreamOption>({ it.audioOnly }, { !it.merge }, { -(Regex("(\\d+)p").find(it.label)?.groupValues?.get(1)?.toIntOrNull() ?: 0) }))
    }
}
