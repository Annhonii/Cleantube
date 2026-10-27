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
        short = optBoolean("short"),
        channelThumb = optString("chThumb"),
        notInterestedToken = optString("ni"),
        dontRecommendChannelToken = optString("nc"),
        historyDay = optString("day"),
        removeFromHistoryToken = optString("rm")
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

    /** Creates a playlist on the signed-in YouTube account, not in local app storage. */
    suspend fun createYouTubePlaylist(title: String, videoId: String? = null): String {
        if (!Session.loggedIn) throw IllegalStateException("Sign in to your YouTube account first.")
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) throw IllegalArgumentException("Enter a playlist name.")
        val ids = JSONArray()
        videoId?.takeIf { it.isNotBlank() }?.let(ids::put)
        val result = Yt.run(
            "createPlaylist",
            args("title" to cleanTitle, "videoIds" to ids, "privacyStatus" to "PRIVATE")
        )
        return result.optString("id").takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("YouTube did not confirm playlist creation. Check the account session and try again.")
    }

    /** Removes a playlist from the signed-in YouTube account. */
    suspend fun deleteYouTubePlaylist(id: String) {
        if (!Session.loggedIn) throw IllegalStateException("Sign in to your YouTube account first.")
        Yt.run("deletePlaylist", args("id" to id))
    }

    private val markedWatched = HashSet<String>()

    /** Asks YouTube whether it treats this app as a signed-in account (shown by Settings > Player > Connection check). */
    suspend fun connectionCheck(): String = try {
        Yt.run("diag").toString(2)
    } catch (e: Exception) {
        "error: ${e.message}"
    }

    /** What the last "watched" ping did (shown in Settings > Player so a silent failure is visible). */
    @Volatile var lastWatchPing: String = "no ping sent yet"

    /**
     * Tells YouTube this video was watched (what the official app does when playback starts), so the account's
     * recommendations keep learning from what is watched in this app. Signed in only, once per video per run.
     */
    suspend fun markWatched(videoId: String, durationSec: Long = 0): Boolean {
        if (!Graph.store.syncHistory || Graph.store.historyPaused || videoId.isEmpty()) return true
        if (!Session.loggedIn) { lastWatchPing = "not signed in (Session.loggedIn is false)"; return true }
        if (Playback.isBlocked()) { lastWatchPing = "paused: network is blocked"; return false }
        val first = synchronized(markedWatched) { markedWatched.add(videoId) }
        if (!first) return true
        // a Short can be shorter than the usual 10 s ping: never report more watch time than the clip has
        val pos = if (durationSec in 1L..10L) maxOf(1, (durationSec - 1).toInt()) else 10
        try {
            val r = Yt.run("markWatched", args("id" to videoId, "pos" to pos))
            lastWatchPing = "$videoId: $r"
            android.util.Log.d("Voidstream", "markWatched $lastWatchPing")
            val ok = r.optBoolean("ok")
            if (!ok) synchronized(markedWatched) { markedWatched.remove(videoId) }
            return ok
        } catch (e: kotlinx.coroutines.CancellationException) {
            synchronized(markedWatched) { markedWatched.remove(videoId) }
            throw e
        } catch (e: Exception) {
            lastWatchPing = "$videoId: error ${e.message}"
            synchronized(markedWatched) { markedWatched.remove(videoId) }
            return false
        }
    }


    private fun videosPage(j: JSONObject, dropShorts: Boolean = false): Page<VideoItem> {
        var list = j.pick("v") { it.video() }
        if (dropShorts) list = list.filter { !it.short }
        return Page(list, j.nextToken())
    }

    // ---------------------------------------------------------------- account feeds

    /** YouTube's own Home recommendations (long videos only), personalised when signed in. Used as a candidate source by Reco. */
    suspend fun youtubeHome(token: String?): Page<VideoItem> =
        videosPage(Yt.run("home", args("token" to token)), dropShorts = true)

    /** YouTube's own Shorts recommendations (one page); [token] is the raw continuation of the previous page. */
    suspend fun youtubeShorts(token: String?): Page<VideoItem> =
        videosPage(Yt.run("shorts", args("token" to token)))

    suspend fun home(token: String?): Page<VideoItem> {
        // Default: YouTube's own Home feed. With "official recommendations" off, the app's own engine (Reco) ranks on the phone.
        // With no subscriptions at all there is nothing to learn from yet, so YouTube's own feed is shown instead.
        if (!Graph.store.officialReco) if (token == null || token.startsWith("L:")) {
            Reco.homePage(token)?.let { return Page(it.items.filter { v -> !Graph.store.isHidden(v) }, it.next) }
            if (token != null) return Page(emptyList(), null)
        }
        val p = videosPage(Yt.run("home", args("token" to token)), dropShorts = true)
        val clean = p.items.filter { !Graph.store.isHidden(it) }
        return if (Graph.store.officialReco) Page(spreadChannels(clean, first = token == null), p.next) else Page(clean, p.next)
    }

    private val recentHomeChannelIds = ArrayDeque<String>()

    /**
     * YouTube's feed sometimes puts several videos of one channel together. Keep the order, but show a channel only once
     * per page and not again within the last 12 channels shown. (If that would leave a tiny page, the dropped ones refill it.)
     */
    private fun spreadChannels(list: List<VideoItem>, first: Boolean): List<VideoItem> = synchronized(recentHomeChannelIds) {
        if (first) recentHomeChannelIds.clear()
        val used = HashSet<String>()
        val kept = ArrayList<VideoItem>()
        val dropped = ArrayList<VideoItem>()
        for (v in list) {
            val ch = v.channelId.ifEmpty { v.channelTitle }
            if (ch.isNotEmpty() && (ch in used || ch in recentHomeChannelIds)) dropped += v else { kept += v; if (ch.isNotEmpty()) used += ch }
        }
        if (kept.size < 5) dropped.take(5 - kept.size).forEach { kept += it }
        kept.forEach { v -> v.channelId.ifEmpty { v.channelTitle }.takeIf { it.isNotEmpty() }?.let { recentHomeChannelIds.addLast(it) } }
        while (recentHomeChannelIds.size > 12) recentHomeChannelIds.removeFirst()
        kept
    }

    /** Trending tab: YouTube has no trending feed any more, so this is a search for what is popular today (no Shorts). */
    suspend fun trending(token: String?): Page<VideoItem> {
        val p = searchVideos("trending videos today", token)
        return Page(p.items.filter { !it.short }, p.next)
    }

    suspend fun accountFeed(token: String?): Page<VideoItem> =
        videosPage(Yt.run("feed", args("token" to token)), dropShorts = true)

    suspend fun accountHistory(token: String?): Page<VideoItem> =
        videosPage(Yt.run("history", args("token" to token)))

    /** Clears the signed-in account's whole YouTube watch history. True when YouTube accepted it. */
    suspend fun clearAccountHistory(): Boolean = try {
        Yt.run("clearHistory", args()).optBoolean("ok")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** The playlists the signed-in account owns / saved (YouTube's "Playlists" library page, up to ~6 pages). */
    suspend fun accountPlaylists(): List<PlaylistItem> {
        val out = ArrayList<PlaylistItem>()
        var token: String? = null
        var pages = 0
        do {
            val j = Yt.run("playlists", args("token" to token))
            out += j.pick("p") { it.playlistItem() }
            token = j.nextToken()
        } while (token != null && ++pages < 6)
        return out.distinctBy { it.id }
    }

    private fun JSONObject.notification(): NotificationItem = NotificationItem(
        id = optString("id"),
        message = optString("msg"),
        time = optString("time"),
        avatar = optString("avatar"),
        thumb = optString("thumb"),
        read = optBoolean("read"),
        kind = optString("kind").ifEmpty { "other" },
        videoId = optString("vid"),
        channelId = optString("chId"),
        handle = optString("handle")
    )

    /** YouTube's notification bell of the signed-in account: new uploads, live, comments, replies, mentions... */
    suspend fun notifications(token: String?): Page<NotificationItem> {
        val j = Yt.run("notifications", args("token" to token))
        return Page(j.pick("n") { it.notification() }, j.nextToken())
    }

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

    /** Signed in: the channels the Google / YouTube account is already subscribed to become subscriptions here too. */
    suspend fun syncSubscriptionsFromAccount() {
        if (!Session.loggedIn) return
        val list = try {
            accountChannels()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        if (list.isEmpty()) return
        Graph.store.importChannels(list)
        Graph.store.refreshSubscriptions(list)
    }

    /** Tells YouTube "Not interested" / "Don't recommend channel" with the token of a feed entry (best effort, signed-in only). */
    suspend fun sendFeedback(token: String) {
        if (token.isEmpty() || !Session.loggedIn) return
        try {
            Yt.run("feedback", args("token" to token))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // the local block below still applies
        }
    }

    suspend fun subscribe(channelId: String, on: Boolean) {
        Yt.run("subscribe", args("id" to channelId, "on" to on))
    }

    /** Rates a video on the signed-in account. [status] is "like", "dislike" or "none". */
    suspend fun rate(videoId: String, status: String) {
        try {
            Yt.run("rate", args("id" to videoId, "status" to status))
        } catch (e: ApiException) {
            // second try with the params YouTube's own buttons send (read from the watch page), if there are any
            val p = rateParams[videoId]?.optString(status)?.takeIf { it.isNotEmpty() } ?: throw e
            Yt.run("rate", args("id" to videoId, "status" to status, "params" to p))
        }
        Reco.onRate(videoId, status)   // the recommendations learn from it too
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
        // The app's own Shorts engine (Reco) first: subscribed channels' Shorts, reacting to what is watched / swiped.
        if ((!Graph.store.officialReco || token?.startsWith("L:") == true) && topic.isEmpty() && (token == null || token.startsWith("L:"))) {
            Reco.shortsPage(token)?.let { return it }
            if (token != null) return Page(emptyList(), null)
        }
        if (topic.isEmpty() && (token == null || token.startsWith("R:"))) {
            // Only YouTube's own Shorts recommendations: never swapped for a "#shorts" search.
            var failure: Exception? = null
            val official = try {
                val j = Yt.run("shorts", args("token" to token?.removePrefix("R:")))
                videosPage(j).let { p -> Page(p.items, p.next?.let { "R:$it" }) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
                null
            }
            if (official != null && official.items.isNotEmpty()) return official
            // a continuation that expired: start a fresh official sequence instead of ending the feed
            if (token != null) {
                try {
                    val j = Yt.run("shorts")
                    videosPage(j).let { p -> Page(p.items, p.next?.let { "R:$it" }) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }?.let { if (it.items.isNotEmpty()) return it }
                return Page(emptyList(), null)
            }
            // YouTube's feed failed on the first page: the app's own engine still has something to show
            if (token == null) Reco.shortsPage(null)?.let { return it }
            throw failure ?: ApiException(ApiException.Kind.OTHER, "YouTube sent no Shorts recommendations right now. Try again in a moment.")
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
    /** videoId -> params of YouTube's like / dislike / remove-like buttons (keys "like", "dislike", "none"). */
    private val rateParams = ConcurrentHashMap<String, JSONObject>()

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
        j.optJSONObject("rateParams")?.let { rateParams[id] = it }
        val related = (j.optJSONArray("related") ?: JSONArray()).mapObjects { it.video() }
        Reco.remember(vid.copy(title = title))
        Reco.addRelated(related)   // up next videos = adjacent-topic candidates for Home
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
            relatedNext = j.optString("relatedNext").takeIf { it.isNotEmpty() && it != "null" },
            likeStatus = j.optString("likeStatus")
        )
    }

    /** YouTube's own "related to this video" list (the up-next column), long videos only. */
    suspend fun relatedOf(id: String): List<VideoItem> {
        val j = Yt.run("watch", args("id" to id))
        return (j.optJSONArray("related") ?: JSONArray()).mapObjects { it.video() }.filter { !it.short }
    }

    suspend fun related(token: String?): Page<VideoItem> {
        if (token == null) return Page(emptyList(), null)
        return videosPage(Yt.run("related", args("token" to token)), dropShorts = true)
    }

    suspend fun comments(videoId: String, page: String?): Page<Comment> {
        val token = page ?: watches[videoId]?.commentsToken
        ?: throw ApiException(ApiException.Kind.COMMENTS_OFF, "Comments are turned off for this video.")
        val j = Yt.run("comments", args("token" to token))
        if (j.optBoolean("disabled")) throw ApiException(ApiException.Kind.COMMENTS_OFF, "Comments are turned off for this video.")
        j.optString("debug").takeIf { it.isNotEmpty() }?.let { replyDebug = it }
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
                repliesToken = it.optString("replyToken"),
                replyParams = it.optString("replyParams"),
                likeAction = it.optJSONObject("acts")?.optString("like").orEmpty(),
                dislikeAction = it.optJSONObject("acts")?.optString("dislike").orEmpty(),
                unlikeAction = it.optJSONObject("acts")?.optString("unlike").orEmpty(),
                undislikeAction = it.optJSONObject("acts")?.optString("undislike").orEmpty()
            )
        }
        return Page(list, j.nextToken())
    }

    /** Posts a new top-level comment on the signed-in account. */
    suspend fun postComment(videoId: String, text: String) {
        val first = createParams[videoId] ?: loadCreateParams(videoId)
            ?: throw ApiException(ApiException.Kind.OTHER, "The comment box isn't available for this video. Make sure you are signed in and comments are on.")
        try {
            Yt.run("postComment", args("params" to first, "text" to text))
        } catch (e: ApiException) {
            // the token may have gone stale: read a fresh one and try once more
            val again = loadCreateParams(videoId) ?: throw e
            Yt.run("postComment", args("params" to again, "text" to text))
        }
    }

    /** What YouTube's comment data looks like when it carried no reply token (to find where it keeps it). */
    @Volatile var replyDebug: String = ""

    /** Likes / dislikes a comment (or takes the vote back) with the action YouTube supplied for that button. */
    suspend fun commentAction(action: String) {
        if (action.isEmpty()) throw ApiException(ApiException.Kind.OTHER, "YouTube didn't allow this from here.")
        Yt.run("commentAction", args("action" to action))
    }

    /** Posts a reply to a comment on the signed-in account. */
    suspend fun postReply(replyParams: String, text: String) {
        if (replyParams.isEmpty()) {
            throw ApiException(ApiException.Kind.OTHER, "YouTube didn't allow replying to this comment from here.")
        }
        Yt.run("postReply", args("params" to replyParams, "text" to text))
    }

    /** Reads the first comments page again, only to get the token YouTube wants for a new comment. */
    private suspend fun loadCreateParams(videoId: String): String? {
        val token = watches[videoId]?.commentsToken ?: return null
        val j = Yt.run("comments", args("token" to token))
        return j.optString("create").takeIf { it.isNotEmpty() }?.also { createParams[videoId] = it }
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

    /** A channel's Posts tab (empty list when the channel has none). */
    suspend fun channelPosts(id: String, token: String?): Page<Post> {
        val j = Yt.run("posts", args("id" to id, "token" to (token ?: "")))
        val list = (j.optJSONArray("items") ?: JSONArray()).mapObjects {
            Post(
                id = it.optString("id"),
                author = it.optString("author"),
                avatar = it.optString("avatar"),
                text = it.optString("text"),
                age = it.optString("age"),
                likes = it.optString("likes"),
                comments = it.optString("comments"),
                image = it.optString("image"),
                video = it.optString("video"),
                videoTitle = it.optString("videoTitle"),
                poll = it.optString("poll"),
                channelId = id,
                commentsBrowseId = it.optString("cb"),
                commentsParams = it.optString("cp")
            )
        }
        return Page(list, j.nextToken())
    }

    /** The comments of a community post (read only), page by page. */
    suspend fun postComments(post: Post, page: String?): Page<Comment> {
        if (page == null && post.commentsParams.isEmpty()) return Page(emptyList(), null)
        val j = Yt.run("postComments", args("token" to page, "cb" to post.commentsBrowseId, "cp" to post.commentsParams))
        val list = (j.optJSONArray("items") ?: JSONArray()).mapObjects {
            Comment(
                author = it.optString("author"),
                avatar = it.optString("avatar"),
                text = it.optString("text"),
                likes = 0,
                publishedAt = it.optString("age"),
                replies = 0,
                likesText = it.optString("likes")
            )
        }
        return Page(list, j.nextToken())
    }

    /** A channel's Shorts or Live tab. */
    suspend fun channelVideosTab(id: String, tab: String, token: String?): Page<VideoItem> {
        val j = Yt.run("channelTab", args("id" to id, "tab" to tab, "token" to token))
        val ch = channelCache[id]?.first
        val vids = j.pick("v") { it.video() }.map {
            it.copy(
                channelId = id, channelTitle = it.channelTitle.ifEmpty { ch?.title.orEmpty() },
                short = it.short || tab == "shorts"
            )
        }
        return Page(vids, j.nextToken())
    }

    /** A channel's Playlists tab. */
    suspend fun channelPlaylists(id: String, token: String?): Page<PlaylistItem> {
        val j = Yt.run("channelTab", args("id" to id, "tab" to "playlists", "token" to token))
        return Page(j.pick("p") { it.playlistItem() }, j.nextToken())
    }

    // ---------------------------------------------------------------- playlist

    private val playlistTitles = ConcurrentHashMap<String, String>()

    suspend fun playlist(id: String): PlaylistItem {
        val j = Yt.run("playlist", args("id" to id))
        val t = j.optString("title")
        playlistTitles[id] = t
        val count = j.optString("count")
        return PlaylistItem(
            id = id,
            title = t,
            channelTitle = j.optString("owner"),
            thumb = j.optString("thumb"),
            count = Regex("(\\d[\\d,]*)").find(count)?.value?.replace(",", "")?.toIntOrNull() ?: -1,
            countText = count,
            viewsText = j.optString("views"),
            updatedText = j.optString("updated")
        )
    }

    suspend fun playlistVideos(id: String, token: String?): Page<VideoItem> {
        val j = Yt.run("playlist", args("id" to id, "token" to token))
        return videosPage(j)
    }

    /** Every video of a playlist (all pages, repeats dropped), for "Download all". [onProgress] gets the running count. */
    suspend fun allPlaylistVideos(id: String, onProgress: (Int) -> Unit = {}): List<VideoItem> {
        val out = LinkedHashMap<String, VideoItem>()
        var token: String? = null
        var pages = 0
        do {
            val page = playlistVideos(id, token)
            page.items.forEach { out.putIfAbsent(it.id, it) }
            onProgress(out.size)
            token = page.next
            pages++
        } while (token != null && pages < 200)
        return out.values.toList()
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
