package com.cleantube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.stream.StreamInfo as NpStreamInfo

/** HTTP layer the extractor needs (same job as DownloaderImpl in the NewPipe app). */
private class NpDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val data = request.dataToSend()

        val needsBody = method == "POST" || method == "PUT" || method == "PATCH"
        val body = when {
            data != null -> data.toRequestBody()
            needsBody -> ByteArray(0).toRequestBody()
            else -> null
        }
        val rb = okhttp3.Request.Builder().method(method, body).url(url)
            .addHeader("User-Agent", Np.USER_AGENT)
        request.headers().forEach { (name, values) ->
            rb.removeHeader(name)
            values.forEach { rb.addHeader(name, it) }
        }

        client.newCall(rb.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("YouTube asked for a captcha (HTTP 429)", url)
            val text = resp.body?.string()
            return Response(resp.code, resp.message, resp.headers.toMultimap(), text, resp.request.url.toString())
        }
    }
}

/**
 * The NewPipe engine. NewPipeExtractor knows how to talk to YouTube's current player endpoints and how to
 * unscramble ("deobfuscate") the links so googlevideo.com doesn't answer HTTP 403 / throttle them.
 */
object Np {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    private var started = false

    @Synchronized
    private fun ensure() {
        if (started) return
        started = true
        val country = Graph.store.region
        NewPipe.init(NpDownloader(), Localization("en", country), ContentCountry(country))
    }

    // YoutubeParsingHelper has helpers that say which app-client a link was made for (links only work with that
    // client's User-Agent). They are called through reflection so a renamed helper never breaks the build:
    // we simply fall back to the default User-Agent.
    private val helper by lazy {
        runCatching { Class.forName("org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper") }.getOrNull()
    }

    private fun urlIs(method: String, url: String): Boolean = runCatching {
        helper!!.getMethod(method, String::class.java).invoke(null, url) as Boolean
    }.getOrDefault(false)

    private fun clientAgent(method: String): String? = runCatching {
        helper!!.getMethod(method, Localization::class.java).invoke(null, *arrayOf<Any?>(null)) as String
    }.getOrNull()

    /** The User-Agent a stream link must be fetched with. */
    fun userAgentFor(url: String): String = when {
        urlIs("isAndroidStreamingUrl", url) -> clientAgent("getAndroidUserAgent") ?: USER_AGENT
        urlIs("isIosStreamingUrl", url) -> clientAgent("getIosUserAgent") ?: USER_AGENT
        else -> USER_AGENT
    }

    private fun Stream.directUrl(): String? =
        if (deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && isUrl) content else null

    private fun bitrateOf(s: Stream): Int = when (s) {
        is AudioStream -> s.averageBitrate
        is VideoStream -> s.bitrate
        else -> 0
    }

    private fun sizeOf(s: Stream): Long = runCatching { s.itagItem?.contentLength ?: -1L }.getOrDefault(-1L).takeIf { it > 0 } ?: -1L

    /** Other sound tracks (dubbed languages, or bitrate tiers) and caption files of [videoId]. */
    suspend fun extras(videoId: String): StreamExtras = withContext(Dispatchers.IO) {
        ensure()
        val info = NpStreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")

        val audioAll = info.audioStreams.filter { it.directUrl() != null }
        fun best(list: List<AudioStream>): AudioStream? =
            list.filter { it.format?.suffix == "m4a" }.maxByOrNull { bitrateOf(it) } ?: list.maxByOrNull { bitrateOf(it) }
        val byTrack = audioAll.groupBy { it.audioTrackId.orEmpty() }.filterKeys { it.isNotEmpty() }
        val audio: List<AudioSource> =
            if (byTrack.size > 1) {
                byTrack.values.mapNotNull { group ->
                    val a = best(group) ?: return@mapNotNull null
                    val original = runCatching { a.audioTrackType?.toString() }.getOrNull() == "ORIGINAL"
                    val name = a.audioTrackName.orEmpty().ifBlank { a.audioLocale?.displayLanguage.orEmpty() }.ifBlank { a.audioTrackId.orEmpty() }
                    Triple(original, name + if (original) " (original)" else "", a)
                }.sortedByDescending { it.first }
                    .map { (_, label, a) -> AudioSource(label, a.directUrl()!!, userAgentFor(a.directUrl()!!)) }
            } else {
                val tiers = audioAll.filter { it.format?.suffix == "m4a" }
                    .sortedByDescending { bitrateOf(it) }
                    .distinctBy { bitrateOf(it) / 1000 }
                    .take(3)
                if (tiers.size < 2) emptyList()
                else tiers.mapIndexed { i, a ->
                    val name = listOf("High", "Medium", "Low").getOrElse(i) { "Low" }
                    AudioSource("$name · ${bitrateOf(a) / 1000} kbps", a.directUrl()!!, userAgentFor(a.directUrl()!!))
                }
            }

        val subs = info.subtitles
            .filter { it.format?.suffix == "vtt" && it.content.orEmpty().isNotEmpty() }
            .map {
                SubtitleTrack(
                    label = it.displayLanguageName.orEmpty().ifBlank { it.languageTag.orEmpty() } + if (it.isAutoGenerated) " (auto)" else "",
                    lang = it.languageTag.orEmpty(),
                    url = it.content,
                    auto = it.isAutoGenerated
                )
            }
            .distinctBy { it.lang + it.auto }
            .sortedBy { it.auto }
        StreamExtras(audio, subs)
    }

    /** Every file YouTube lets us download for [videoId], as the app's [StreamOption] list. */
    suspend fun fetch(videoId: String): StreamInfo = withContext(Dispatchers.IO) {
        ensure()
        val info = NpStreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val out = ArrayList<StreamOption>()

        // 1) ready-made files with picture + sound
        info.videoStreams.filter { !it.isVideoOnly }.forEach { v ->
            val url = v.directUrl() ?: return@forEach
            val suffix = v.format?.suffix ?: "mp4"
            out += StreamOption(
                itag = 0,
                label = "${v.resolution.orEmpty().ifEmpty { "Video" }} video + sound",
                audioOnly = false,
                ext = suffix,
                mime = v.format?.mimeType ?: "video/mp4",
                size = sizeOf(v),
                url = url,
                userAgent = userAgentFor(url)
            )
        }

        // 2) audio tracks: prefer the original language when YouTube offers dubbed tracks
        val audioAll = info.audioStreams.filter { it.directUrl() != null }
        val originals = audioAll.filter {
            val t = runCatching { it.audioTrackType?.toString() }.getOrNull()
            t == null || t == "ORIGINAL"
        }
        val audio = originals.ifEmpty { audioAll }
        val bestM4a = audio.filter { it.format?.suffix == "m4a" }.maxByOrNull { bitrateOf(it) }
        val bestWebm = audio.filter { it.format?.suffix == "webm" }.maxByOrNull { bitrateOf(it) }

        // 3) picture-only H.264 tracks of every height + the best m4a sound, joined on the phone after download
        if (bestM4a != null) {
            val aUrl = bestM4a.directUrl()!!
            val aSize = sizeOf(bestM4a)
            info.videoOnlyStreams
                .filter { it.directUrl() != null && it.format?.suffix == "mp4" && it.codec.orEmpty().startsWith("avc1") }
                .groupBy { it.height }
                .forEach { (height, group) ->
                    val v = group.maxByOrNull { bitrateOf(it) } ?: return@forEach
                    val vUrl = v.directUrl()!!
                    val vSize = sizeOf(v)
                    out += StreamOption(
                        itag = 0,
                        label = "${v.resolution.orEmpty().ifEmpty { "${height}p" }} video + sound (MP4)",
                        audioOnly = false,
                        ext = "mp4",
                        mime = "video/mp4",
                        size = if (vSize > 0 && aSize > 0) vSize + aSize else -1,
                        url = vUrl,
                        userAgent = userAgentFor(vUrl),
                        audioUrl = aUrl,
                        audioSize = if (aSize > 0) aSize else 0,
                        audioUserAgent = userAgentFor(aUrl),
                        merge = true
                    )
                }
        }

        // 4) audio only
        listOf(bestM4a to "m4a", bestWebm to "webm").forEach { (s, ext) ->
            val a = s ?: return@forEach
            val url = a.directUrl() ?: return@forEach
            out += StreamOption(
                itag = 0,
                label = "Audio only (${bitrateOf(a) / 1000} kbps $ext)",
                audioOnly = true,
                ext = ext,
                mime = a.format?.mimeType ?: if (ext == "m4a") "audio/mp4" else "audio/webm",
                size = sizeOf(a),
                url = url,
                userAgent = userAgentFor(url)
            )
        }

        val hls = runCatching { info.hlsUrl.orEmpty() }.getOrDefault("")
        if (out.isEmpty() && hls.isEmpty()) throw ApiException(ApiException.Kind.OTHER, "NewPipe found no downloadable streams for this video.")
        val sorted = out.sortedWith(
            compareBy<StreamOption>({ it.audioOnly }, { !it.merge }, { -(Regex("(\\d+)p").find(it.label)?.groupValues?.get(1)?.toIntOrNull() ?: 0) })
        )
        StreamInfo(info.name.orEmpty(), sorted, hls)
    }
}
