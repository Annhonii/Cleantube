package com.cleantube.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Title, channel name and channel (with logo) of a Short, for the many Shorts whose feed entry and watch page
 * carry none of them. Uses YouTube's public oEmbed lookup (title + author) and then the channel page for the logo.
 */
object ShortMeta {
    class Info(val title: String, val author: String, val channel: ChannelItem?)

    private val cache = ConcurrentHashMap<String, Info>()

    suspend fun load(videoId: String): Info? {
        cache[videoId]?.let { return it }
        val j = withContext(Dispatchers.IO) {
            try {
                val u = "https://www.youtube.com/oembed?format=json&url=" +
                    URLEncoder.encode("https://www.youtube.com/shorts/$videoId", "UTF-8")
                val c = URL(u).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 6_000
                    c.readTimeout = 8_000
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
                    if (c.responseCode !in 200..299) null
                    else JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    c.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        } ?: return null

        val author = j.optString("author_name")
        val url = j.optString("author_url")
        val channel: ChannelItem? = try {
            when {
                "/channel/UC" in url ->
                    Api.channel(url.substringAfter("/channel/").substringBefore('/').substringBefore('?'))
                "/@" in url ->
                    Api.channelByHandle("@" + url.substringAfter("/@").substringBefore('/').substringBefore('?'))
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val info = Info(j.optString("title"), author, channel)
        if (info.title.isNotEmpty() || info.author.isNotEmpty() || channel != null) cache[videoId] = info
        return info
    }
}
