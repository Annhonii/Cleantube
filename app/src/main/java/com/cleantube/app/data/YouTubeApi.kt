package com.cleantube.app.data

import com.cleantube.app.data.ApiException.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin client for the official YouTube Data API v3 (https://developers.google.com/youtube/v3).
 * Uses the user's own API key, stored in [Store]. Quota costs: search = 100 units, everything else = 1.
 */
object YouTubeApi {

    private const val BASE = "https://www.googleapis.com/youtube/v3/"
    private const val TTL_MS = 5 * 60_000L
    private val cache = LinkedHashMap<String, Pair<Long, JSONObject>>()

    // ---------------------------------------------------------------- HTTP

    private suspend fun get(
        endpoint: String,
        params: Map<String, String>,
        keyOverride: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val key = keyOverride ?: Graph.store.apiKey
        if (key.isBlank()) throw ApiException(Kind.NO_KEY, "Add your YouTube API key in Settings.")

        val query = params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
        val cacheKey = "$endpoint?$query"
        if (keyOverride == null) {
            val hit = synchronized(cache) { cache[cacheKey] }
            if (hit != null && System.currentTimeMillis() - hit.first < TTL_MS) return@withContext hit.second
        }

        val conn = URL("$BASE$endpoint?$query&key=${enc(key)}").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw toException(code, body)
            val json = JSONObject(body)
            if (keyOverride == null) {
                synchronized(cache) {
                    if (cache.size > 120) cache.clear()
                    cache[cacheKey] = System.currentTimeMillis() to json
                }
            }
            json
        } catch (e: IOException) {
            throw ApiException(Kind.NETWORK, "No internet connection.")
        } catch (e: JSONException) {
            throw ApiException(Kind.OTHER, "Unexpected response from YouTube.")
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun toException(code: Int, body: String): ApiException {
        var reason = ""
        var msg = "YouTube API error ($code)"
        try {
            val err = JSONObject(body).optJSONObject("error")
            if (err != null) {
                msg = err.optString("message", msg)
                reason = err.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
            }
        } catch (e: Exception) {
            // keep defaults
        }
        val kind = when {
            reason == "quotaExceeded" || reason == "dailyLimitExceeded" || reason == "rateLimitExceeded" -> Kind.QUOTA
            reason == "keyInvalid" || reason == "keyExpired" || reason == "accessNotConfigured" ||
                msg.contains("API key not valid", ignoreCase = true) -> Kind.BAD_KEY
            reason == "commentsDisabled" -> Kind.COMMENTS_OFF
            code == 404 -> Kind.NOT_FOUND
            else -> Kind.OTHER
        }
        return ApiException(kind, msg)
    }

    /** Returns null when the key works, otherwise a human readable problem. */
    suspend fun testKey(key: String): String? = try {
        get("videos", mapOf("part" to "id", "id" to "dQw4w9WgXcQ"), keyOverride = key.trim())
        null
    } catch (e: ApiException) {
        e.message ?: "Key rejected"
    }

    // ------------------------------------------------------------- parsing

    private fun JSONObject.items(): JSONArray = optJSONArray("items") ?: JSONArray()

    private fun nextToken(j: JSONObject): String? = j.optString("nextPageToken").ifEmpty { null }

    private fun fixUrl(u: String): String = if (u.startsWith("//")) "https:$u" else u

    private fun thumbOf(snippet: JSONObject?): String {
        val t = snippet?.optJSONObject("thumbnails") ?: return ""
        val pick = t.optJSONObject("medium") ?: t.optJSONObject("high") ?: t.optJSONObject("default")
        return fixUrl(pick?.optString("url").orEmpty())
    }

    private fun parseVideo(o: JSONObject): VideoItem? {
        val id = o.optString("id")
        if (id.isEmpty()) return null
        val sn = o.optJSONObject("snippet")
        val cd = o.optJSONObject("contentDetails")
        val st = o.optJSONObject("statistics")
        val durStr = cd?.optString("duration").orEmpty()
        val dur = if (durStr.isEmpty()) -1L else parseIsoDuration(durStr)
        val live = sn?.optString("liveBroadcastContent") == "live" || dur == 0L
        return VideoItem(
            id = id,
            title = sn?.optString("title").orEmpty(),
            channelId = sn?.optString("channelId").orEmpty(),
            channelTitle = sn?.optString("channelTitle").orEmpty(),
            publishedAt = sn?.optString("publishedAt").orEmpty(),
            durationSec = dur,
            views = st?.optString("viewCount")?.toLongOrNull() ?: -1L,
            live = live
        )
    }

    private fun parseChannel(o: JSONObject): ChannelItem? {
        val id = o.optString("id")
        if (id.isEmpty()) return null
        val sn = o.optJSONObject("snippet")
        val st = o.optJSONObject("statistics")
        val hidden = st?.optBoolean("hiddenSubscriberCount", false) ?: false
        val subs = if (hidden) -1L else st?.optString("subscriberCount")?.toLongOrNull() ?: -1L
        val banner = o.optJSONObject("brandingSettings")?.optJSONObject("image")
            ?.optString("bannerExternalUrl").orEmpty()
        return ChannelItem(
            id = id,
            title = sn?.optString("title").orEmpty(),
            thumb = thumbOf(sn),
            subs = subs,
            description = sn?.optString("description").orEmpty(),
            banner = if (banner.isEmpty()) "" else "$banner=w1060",
            handle = sn?.optString("customUrl").orEmpty()
        )
    }

    // ------------------------------------------------------------- videos

    suspend fun videosByIds(ids: List<String>): List<VideoItem> {
        if (ids.isEmpty()) return emptyList()
        val byId = HashMap<String, VideoItem>()
        for (chunk in ids.distinct().chunked(50)) {
            val j = get(
                "videos",
                mapOf(
                    "part" to "snippet,contentDetails,statistics",
                    "id" to chunk.joinToString(","),
                    "maxResults" to "50"
                )
            )
            j.items().mapObjects { parseVideo(it) }.forEach { byId[it.id] = it }
        }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun trending(region: String, categoryId: String?, page: String?): Page<VideoItem> {
        val p = mutableMapOf(
            "part" to "snippet,contentDetails,statistics",
            "chart" to "mostPopular",
            "regionCode" to region,
            "maxResults" to "25"
        )
        if (categoryId != null) p["videoCategoryId"] = categoryId
        if (page != null) p["pageToken"] = page
        val j = get("videos", p)
        return Page(j.items().mapObjects { parseVideo(it) }, nextToken(j))
    }

    suspend fun videoDetails(id: String): VideoDetails {
        val j = get(
            "videos",
            mapOf("part" to "snippet,contentDetails,statistics,status", "id" to id)
        )
        val o = j.items().optJSONObject(0) ?: throw ApiException(Kind.NOT_FOUND, "Video not found.")
        val v = parseVideo(o) ?: throw ApiException(Kind.NOT_FOUND, "Video not found.")
        val st = o.optJSONObject("statistics")
        return VideoDetails(
            video = v,
            description = o.optJSONObject("snippet")?.optString("description").orEmpty(),
            likes = st?.optString("likeCount")?.toLongOrNull() ?: -1L,
            commentCount = st?.optString("commentCount")?.toLongOrNull() ?: -1L,
            embeddable = o.optJSONObject("status")?.optBoolean("embeddable", true) ?: true
        )
    }

    // ------------------------------------------------------------- search

    private suspend fun searchRaw(q: String, type: String, page: String?): JSONObject {
        val p = mutableMapOf(
            "part" to "snippet",
            "q" to q,
            "type" to type,
            "maxResults" to "20"
        )
        if (type == "video") p["videoEmbeddable"] = "true"
        if (page != null) p["pageToken"] = page
        return get("search", p)
    }

    suspend fun searchVideos(q: String, page: String?): Page<VideoItem> {
        val j = searchRaw(q, "video", page)
        val ids = j.items().mapObjects { o ->
            o.optJSONObject("id")?.optString("videoId")?.takeIf { it.isNotEmpty() }
        }
        return Page(videosByIds(ids), nextToken(j))
    }

    suspend fun searchChannels(q: String, page: String?): Page<ChannelItem> {
        val j = searchRaw(q, "channel", page)
        val list = j.items().mapObjects { o ->
            val id = o.optJSONObject("id")?.optString("channelId").orEmpty()
            val sn = o.optJSONObject("snippet")
            if (id.isEmpty() || sn == null) null
            else ChannelItem(
                id = id,
                title = unescapeHtml(sn.optString("title")),
                thumb = thumbOf(sn),
                description = unescapeHtml(sn.optString("description"))
            )
        }
        return Page(list, nextToken(j))
    }

    suspend fun searchPlaylists(q: String, page: String?): Page<PlaylistItem> {
        val j = searchRaw(q, "playlist", page)
        val list = j.items().mapObjects { o ->
            val id = o.optJSONObject("id")?.optString("playlistId").orEmpty()
            val sn = o.optJSONObject("snippet")
            if (id.isEmpty() || sn == null) null
            else PlaylistItem(
                id = id,
                title = unescapeHtml(sn.optString("title")),
                channelTitle = unescapeHtml(sn.optString("channelTitle")),
                thumb = thumbOf(sn)
            )
        }
        val counts = playlistsByIds(list.map { it.id }).associateBy { it.id }
        return Page(list.map { p -> counts[p.id]?.let { p.copy(count = it.count) } ?: p }, nextToken(j))
    }

    // ----------------------------------------------------------- channels

    suspend fun channelsByIds(ids: List<String>): List<ChannelItem> {
        val out = ArrayList<ChannelItem>()
        for (chunk in ids.distinct().chunked(50)) {
            val j = get(
                "channels",
                mapOf("part" to "snippet,statistics", "id" to chunk.joinToString(","), "maxResults" to "50")
            )
            out += j.items().mapObjects { parseChannel(it) }
        }
        return out
    }

    suspend fun channel(id: String): ChannelItem {
        val j = get(
            "channels",
            mapOf("part" to "snippet,statistics,brandingSettings", "id" to id)
        )
        return j.items().mapObjects { parseChannel(it) }.firstOrNull()
            ?: throw ApiException(Kind.NOT_FOUND, "Channel not found.")
    }

    suspend fun channelByHandle(handle: String): ChannelItem {
        val h = if (handle.startsWith("@")) handle else "@$handle"
        val j = get(
            "channels",
            mapOf("part" to "snippet,statistics,brandingSettings", "forHandle" to h)
        )
        return j.items().mapObjects { parseChannel(it) }.firstOrNull()
            ?: throw ApiException(Kind.NOT_FOUND, "Channel not found.")
    }

    // ---------------------------------------------------------- playlists

    suspend fun playlistsByIds(ids: List<String>): List<PlaylistItem> {
        if (ids.isEmpty()) return emptyList()
        val out = ArrayList<PlaylistItem>()
        for (chunk in ids.distinct().chunked(50)) {
            val j = get(
                "playlists",
                mapOf("part" to "snippet,contentDetails", "id" to chunk.joinToString(","), "maxResults" to "50")
            )
            out += j.items().mapObjects { o ->
                val id = o.optString("id")
                val sn = o.optJSONObject("snippet")
                if (id.isEmpty()) null
                else PlaylistItem(
                    id = id,
                    title = sn?.optString("title").orEmpty(),
                    channelTitle = sn?.optString("channelTitle").orEmpty(),
                    thumb = thumbOf(sn),
                    count = o.optJSONObject("contentDetails")?.optInt("itemCount", -1) ?: -1
                )
            }
        }
        return out
    }

    suspend fun playlist(id: String): PlaylistItem =
        playlistsByIds(listOf(id)).firstOrNull() ?: throw ApiException(Kind.NOT_FOUND, "Playlist not found.")

    /** Videos of any playlist, including a channel's "uploads" playlist (UU...). */
    suspend fun playlistVideos(playlistId: String, page: String?): Page<VideoItem> {
        val p = mutableMapOf(
            "part" to "contentDetails",
            "playlistId" to playlistId,
            "maxResults" to "25"
        )
        if (page != null) p["pageToken"] = page
        val j = get("playlistItems", p)
        val ids = j.items().mapObjects { o ->
            o.optJSONObject("contentDetails")?.optString("videoId")?.takeIf { it.isNotEmpty() }
        }
        return Page(videosByIds(ids), nextToken(j))
    }

    // --------------------------------------------------------------- feed

    /** Latest uploads of the given channels, newest first. Costs about 1 quota unit per channel. */
    suspend fun feed(channels: List<ChannelItem>): List<VideoItem> = coroutineScope {
        val gate = Semaphore(4)
        val found = channels.map { ch ->
            async {
                gate.withPermit {
                    try {
                        val j = get(
                            "playlistItems",
                            mapOf(
                                "part" to "contentDetails",
                                "playlistId" to uploadsPlaylistOf(ch.id),
                                "maxResults" to "5"
                            )
                        )
                        j.items().mapObjects { o ->
                            val cd = o.optJSONObject("contentDetails")
                            val id = cd?.optString("videoId").orEmpty()
                            if (id.isEmpty()) null else id to cd?.optString("videoPublishedAt").orEmpty()
                        }
                    } catch (e: ApiException) {
                        // A single broken channel must not kill the feed, but key/quota problems should surface.
                        if (e.kind == Kind.QUOTA || e.kind == Kind.BAD_KEY || e.kind == Kind.NO_KEY ||
                            e.kind == Kind.NETWORK
                        ) throw e
                        emptyList<Pair<String, String>>()
                    }
                }
            }
        }.awaitAll().flatten()

        val newest = found.sortedByDescending { it.second }.take(60).map { it.first }
        videosByIds(newest).sortedByDescending { it.publishedAt }
    }

    // ----------------------------------------------------------- comments

    suspend fun comments(videoId: String, page: String?): Page<Comment> {
        val p = mutableMapOf(
            "part" to "snippet",
            "videoId" to videoId,
            "maxResults" to "20",
            "order" to "relevance",
            "textFormat" to "plainText"
        )
        if (page != null) p["pageToken"] = page
        val j = get("commentThreads", p)
        val list = j.items().mapObjects { o ->
            val top = o.optJSONObject("snippet")
            val c = top?.optJSONObject("topLevelComment")?.optJSONObject("snippet")
            if (top == null || c == null) null
            else Comment(
                author = c.optString("authorDisplayName"),
                avatar = fixUrl(c.optString("authorProfileImageUrl")),
                text = c.optString("textDisplay"),
                likes = c.optLong("likeCount", 0),
                publishedAt = c.optString("publishedAt"),
                replies = top.optInt("totalReplyCount", 0)
            )
        }
        return Page(list, nextToken(j))
    }
}
