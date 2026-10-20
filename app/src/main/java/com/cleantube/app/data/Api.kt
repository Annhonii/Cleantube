package com.cleantube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * What the screens talk to. Everything goes through YouTube's own InnerTube API (via [Yt]),
 * so no API key is needed, results are YouTube's real ranking, and with a login you get your own
 * home recommendations, subscriptions feed and history.
 */
object Api {

    // ---------------------------------------------------------------- JSON -> models

    private fun JSONObject.video(): VideoItem = VideoItem(
        id = optString("id"),
        title = optString("title"),
        channelId = optString("chId"),
        channelTitle = optString("ch"),
        durationSec = optLong("dur", -1),
        live = optBoolean("live"),
        viewsText = optString("views"),
        ageText = optString("age"),
        short = optBoolean("short")
    )

    private fun JSONObject.channelItem(): ChannelItem = ChannelItem(
        id = optString("id"),
        title = optString("title"),
        thumb = optString("thumb"),
        description = optString("desc"),
        handle = optString("handle"),
        subsText = optString("subs")
    )

    private fun JSONObject.playlistItem(): PlaylistItem = PlaylistItem(
        id = optString("id"),
        title = optString("title"),
        channelTitle = optString("ch"),
        thumb = optString("thumb"),
        countText = optString("count"),
        count = Regex("(\\d[\\d,]*)").find(optString("count"))?.value?.replace(",", "")?.toIntOrNull() ?: -1
    )

    private fun JSONObject.nextToken(): String? = optString("next").takeIf { it.isNotEmpty() && it != "null" }

    private inline fun <T : Any> JSONObject.pick(type: String, f: (JSONObject) -> T): List<T> {
        val a = optJSONArray("items") ?: JSONArray()
        val out = ArrayList<T>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("t") == type && o.optString("id").isNotEmpty()) out.add(f(o))
        }
        return out
    }

    private fun args(vararg kv: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        kv.forEach { (k, v) -> if (v != null) o.put(k, v) }
        return o
    }

    private fun videosPage(j: JSONObject, dropShorts: Boolean = false): Page<VideoItem> {
        var list = j.pick("v") { it.video() }
        if (dropShorts) list = list.filter { !it.short }
        return Page(list, j.nextToken())
    }

    // ---------------------------------------------------------------- account feeds

    suspend fun home(token: String?): Page<VideoItem> {
        // Home shows long videos only: Shorts have their own tab.
        val page = videosPage(Yt.run("home", args("token" to token)), dropShorts = true)
        // Signed-out home can come back empty: fall back to a search so the tab is never blank.
        if (token == null && page.items.isEmpty()) {
            val s = searchVideos("trending now", null)
            return Page(s.items.filter { !it.short }, s.next)
        }
        return page
    }

    suspend fun accountFeed(token: String?): Page<VideoItem> =
        videosPage(Yt.run("feed", args("token" to token)), dropShorts = true)

    suspend fun accountHistory(token: String?): Page<VideoItem> =
        videosPage(Yt.run("history", args("token" to token)))

    /** The channels the signed-in account is subscribed to (up to ~10 pages). */
    suspend fun accountChannels(): List<ChannelItem> {
        val out = ArrayList<ChannelItem>()
        var token: String? = null
        var pages = 0
        do {
            val j = Yt.run("channels", args("token" to token))
            out += j.pick("c") { it.channelItem() }
            token = j.nextToken()
        } while (token != null && ++pages < 10)
        return out
    }

    suspend fun subscribe(channelId: String, on: Boolean) {
        Yt.run("subscribe", args("id" to channelId, "on" to on))
    }

    /** Rates a video on the signed-in account. [status] is "like", "dislike" or "none". */
    suspend fun rate(videoId: String, status: String) {
        Yt.run("rate", args("id" to videoId, "status" to status))
    }

    // ---------------------------------------------------------------- search

    suspend fun searchVideos(q: String, page: String?): Page<VideoItem> =
        videosPage(Yt.run("search", args("q" to q, "filter" to "video", "token" to page)), dropShorts = false)

    /**
     * Shorts feed. With no topic it is YouTube's own personalised Shorts feed (reel_watch_sequence), the same one the
     * official app shows for the signed-in account. Tokens of that feed start with "R:". If it can't be loaded, or a
     * topic chip is selected, it falls back to searching "#shorts" (plus topic) and keeping only real Shorts.
     */
    suspend fun shorts(token: String?, topic: String = ""): Page<VideoItem> {
        if (topic.isEmpty() && (token == null || token.startsWith("R:"))) {
            val official = runCatching {
                val j = Yt.run("shorts", args("token" to token?.removePrefix("R:")))
                videosPage(j).let { p -> Page(p.items, p.next?.let { "R:$it" }) }
            }.getOrNull()
            if (official != null && official.items.isNotEmpty()) return official
            // a continuation that expired: start a fresh official sequence instead of ending the feed
            if (token != null) {
                runCatching {
                    val j = Yt.run("shorts")
                    videosPage(j).let { p -> Page(p.items, p.next?.let { "R:$it" }) }
                }.getOrNull()?.let { if (it.items.isNotEmpty()) return it }
            }
            if (token != null) return Page(emptyList(), null)
        }
        val q = if (topic.isEmpty()) "#shorts" else "#shorts $topic"
        val p = searchVideos(q, token?.takeUnless { it.startsWith("R:") })
        return Page(p.items.filter { it.short || it.durationSec in 1..62 }, p.next)
    }

    suspend fun searchChannels(q: String, page: String?): Page<ChannelItem> {
        val j = Yt.run("search", args("q" to q, "filter" to "channel", "token" to page))
        return Page(j.pick("c") { it.channelItem() }, j.nextToken())
    }

    suspend fun searchPlaylists(q: String, page: String?): Page<PlaylistItem> {
        val j = Yt.run("search", args("q" to q, "filter" to "playlist", "token" to page))
        return Page(j.pick("p") { it.playlistItem() }, j.nextToken())
    }

    // ---------------------------------------------------------------- video

    private class Watch(val commentsToken: String?)
    private val watches = ConcurrentHashMap<String, Watch>()
    /** videoId -> token YouTube wants when posting a new comment (read from the first comments page). */
    private val createParams = ConcurrentHashMap<String, String>()

    suspend fun videoDetails(id: String): VideoDetails {
        val j = Yt.run("watch", args("id" to id))
        val o = j.optJSONObject("owner") ?: JSONObject()
        val ownerId = o.optString("id")
        val channel = if (ownerId.isNotEmpty()) ChannelItem(
            id = ownerId, title = o.optString("name"), thumb = o.optString("thumb"), subsText = o.optString("subs")
        ) else null
        val title = j.optString("title")
        val vid = VideoItem(
            id = id, title = title,
            channelId = ownerId, channelTitle = o.optString("name"),
            viewsText = j.optString("views"), ageText = j.optString("age")
        )
        watches[id] = Watch(j.optString("commentsToken").takeIf { it.isNotEmpty() && it != "null" })
        val related = (j.optJSONArray("related") ?: JSONArray()).mapObjects { it.video() }
        return VideoDetails(
            video = vid,
            description = j.optString("description"),
            likes = -1,
            commentCount = -1,
            embeddable = true,
            likesText = j.optString("likes"),
            commentCountText = j.optString("commentCount"),
            channel = channel,
            related = related,
            relatedNext = j.optString("relatedNext").takeIf { it.isNotEmpty() && it != "null" }
        )
    }

    suspend fun related(token: String?): Page<VideoItem> {
        if (token == null) return Page(emptyList(), null)
        return videosPage(Yt.run("related", args("token" to token)))
    }

    suspend fun comments(videoId: String, page: String?): Page<Comment> {
        val token = page ?: watches[videoId]?.commentsToken
        ?: throw ApiException(ApiException.Kind.COMMENTS_OFF, "Comments are turned off for this video.")
        val j = Yt.run("comments", args("token" to token))
        if (j.optBoolean("disabled")) throw ApiException(ApiException.Kind.COMMENTS_OFF, "Comments are turned off for this video.")
        if (page == null) j.optString("create").takeIf { it.isNotEmpty() }?.let { createParams[videoId] = it }
        val list = (j.optJSONArray("items") ?: JSONArray()).mapObjects {
            Comment(
                author = it.optString("author"),
                avatar = it.optString("avatar"),
                text = it.optString("text"),
                likes = 0,
                publishedAt = it.optString("age"),
                replies = it.optString("replies").toIntOrNull() ?: 0,
                likesText = it.optString("likes"),
                repliesToken = it.optString("replyToken")
            )
        }
        return Page(list, j.nextToken())
    }

    /** Posts a new top-level comment on the signed-in account. */
    suspend fun postComment(videoId: String, text: String) {
        val p = createParams[videoId]
            ?: throw ApiException(ApiException.Kind.OTHER, "The comment box isn't available for this video (sign in and reopen the comments).")
        Yt.run("postComment", args("params" to p, "text" to text))
    }

    // ---------------------------------------------------------------- channel

    private val channelCache = ConcurrentHashMap<String, Pair<ChannelItem, Page<VideoItem>>>()

    private suspend fun loadChannel(id: String): Pair<ChannelItem, Page<VideoItem>> {
        channelCache[id]?.let { return it }
        val j = Yt.run("channel", args("id" to id))
        val c = j.optJSONObject("channel") ?: JSONObject()
        val ch = ChannelItem(
            id = c.optString("id").ifEmpty { id },
            title = c.optString("title"),
            thumb = c.optString("thumb"),
            description = c.optString("desc"),
            banner = c.optString("banner"),
            handle = c.optString("handle"),
            subsText = c.optString("subs"),
            videosText = c.optString("videos")
        )
        val vids = j.pick("v") { it.video() }.map {
            if (it.channelTitle.isEmpty()) it.copy(channelId = ch.id, channelTitle = ch.title) else it.copy(channelId = ch.id)
        }
        val r = ch to Page(vids, j.nextToken())
        if (ch.title.isNotEmpty()) channelCache[id] = r
        return r
    }

    suspend fun channel(id: String): ChannelItem = loadChannel(id).first

    suspend fun channelByHandle(handle: String): ChannelItem {
        val h = if (handle.startsWith("@")) handle else "@$handle"
        val id = Yt.run("handle", args("handle" to h)).optString("id")
        if (id.isEmpty()) throw ApiException(ApiException.Kind.NOT_FOUND, "Channel not found.")
        return channel(id)
    }

    suspend fun channelsByIds(ids: List<String>): List<ChannelItem> = coroutineScope {
        val gate = Semaphore(3)
        ids.map { id -> async(Dispatchers.Default) { gate.withPermit { runCatching { channel(id) }.getOrNull() } } }
            .awaitAll().filterNotNull()
    }

    suspend fun channelVideos(id: String, token: String?): Page<VideoItem> {
        if (token == null) return loadChannel(id).second
        val ch = channelCache[id]?.first
        val j = Yt.run("channel", args("id" to id, "token" to token))
        val vids = j.pick("v") { it.video() }.map {
            it.copy(channelId = id, channelTitle = it.channelTitle.ifEmpty { ch?.title.orEmpty() })
        }
        return Page(vids, j.nextToken())
    }

    // ---------------------------------------------------------------- playlist

    private val playlistTitles = ConcurrentHashMap<String, String>()

    suspend fun playlist(id: String): PlaylistItem {
        val j = Yt.run("playlist", args("id" to id))
        val t = j.optString("title")
        playlistTitles[id] = t
        return PlaylistItem(id = id, title = t)
    }

    suspend fun playlistVideos(id: String, token: String?): Page<VideoItem> {
        val j = Yt.run("playlist", args("id" to id, "token" to token))
        return videosPage(j)
    }

    // ---------------------------------------------------------------- local subscriptions feed (not signed in)

    /** Newest videos from locally stored subscriptions: one channel page each, merged by upload age. */
    suspend fun localFeed(channels: List<ChannelItem>): List<VideoItem> = coroutineScope {
        val gate = Semaphore(4)
        channels.take(40).map { c ->
            async(Dispatchers.Default) {
                gate.withPermit { runCatching { channelVideos(c.id, null).items.filter { !it.short }.take(8) }.getOrDefault(emptyList()) }
            }
        }.awaitAll().flatten().sortedBy { ageMinutes(it.ageText) }.take(120)
    }

    /** "3 hours ago" -> 180. Unknown text sorts last. */
    fun ageMinutes(t: String): Long {
        val m = Regex("(\\d+)\\s*(second|minute|hour|day|week|month|year)", RegexOption.IGNORE_CASE).find(t) ?: return Long.MAX_VALUE
        val n = m.groupValues[1].toLong()
        return n * when (m.groupValues[2].lowercase()) {
            "second" -> 0L
            "minute" -> 1L
            "hour" -> 60L
            "day" -> 1_440L
            "week" -> 10_080L
            "month" -> 43_200L
            else -> 525_600L
        }
    }
}
