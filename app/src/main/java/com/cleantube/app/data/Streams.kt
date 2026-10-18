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
    val userAgent: String
)

data class StreamInfo(val title: String, val options: List<StreamOption>)

/**
 * Gets direct media links from YouTube's "player" endpoint.
 *
 * It asks as a few different official-app clients (VR, iOS, Android) because YouTube changes which of them
 * hand out plain links. Only files that already contain sound AND picture (up to 720p) and audio-only
 * files are offered: joining separate video and audio tracks isn't supported.
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

    suspend fun fetch(videoId: String): StreamInfo = withContext(Dispatchers.IO) {
        var lastProblem = "YouTube didn't give a download link for this video."
        for (c in clients) {
            try {
                val j = request(c, videoId)
                val status = j.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
                if (status != "OK") {
                    val reason = j.optJSONObject("playabilityStatus")?.optString("reason").orEmpty()
                    if (reason.isNotEmpty()) lastProblem = reason
                    continue
                }
                val opts = options(j, c.ua)
                if (opts.isNotEmpty()) {
                    return@withContext StreamInfo(j.optJSONObject("videoDetails")?.optString("title").orEmpty(), opts)
                }
            } catch (e: Exception) {
                lastProblem = e.message ?: lastProblem
            }
        }
        throw ApiException(ApiException.Kind.OTHER, lastProblem)
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

        // audio only: the best m4a and the best webm/opus
        val adaptive = sd.optJSONArray("adaptiveFormats")
        val audio = ArrayList<JSONObject>()
        for (i in 0 until (adaptive?.length() ?: 0)) {
            val f = adaptive!!.optJSONObject(i) ?: continue
            if (f.optString("mimeType").startsWith("audio/") && f.optString("url").isNotEmpty()) audio.add(f)
        }
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
        return out.sortedWith(compareBy<StreamOption>({ it.audioOnly }, { -it.itag }))
    }
}
