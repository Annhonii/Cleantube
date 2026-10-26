package com.cleantube.app.data

import org.json.JSONArray
import org.json.JSONObject

data class VideoItem(
    val id: String,
    val title: String,
    val channelId: String = "",
    val channelTitle: String = "",
    val publishedAt: String = "",
    val durationSec: Long = -1,
    val views: Long = -1,
    val live: Boolean = false,
    /** Display texts straight from YouTube ("1.6M views", "3 days ago"); empty when unknown. */
    val viewsText: String = "",
    val ageText: String = "",
    val short: Boolean = false,
    /** Channel logo when the feed carried it (empty otherwise; the UI then looks it up). */
    val channelThumb: String = "",
    /** YouTube feedback tokens from the feed menu: "Not interested" / "Don't recommend channel" (empty when none). */
    val notInterestedToken: String = "",
    val dontRecommendChannelToken: String = ""
) {
    val thumb: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}

data class ChannelItem(
    val id: String,
    val title: String,
    val thumb: String = "",
    val subs: Long = -1,
    val description: String = "",
    val banner: String = "",
    val handle: String = "",
    val subsText: String = "",
    val videosText: String = ""
)

data class PlaylistItem(
    val id: String,
    val title: String,
    val channelTitle: String = "",
    val thumb: String = "",
    val count: Int = -1,
    val countText: String = "",
    /** Only filled for a playlist's own page ("76 views", "Updated today"); empty in lists. */
    val viewsText: String = "",
    val updatedText: String = ""
)

data class Comment(
    val author: String,
    val avatar: String,
    val text: String,
    val likes: Long,
    val publishedAt: String,
    val replies: Int,
    val likesText: String = "",
    /** Continuation that loads this comment's replies; empty when it has none. */
    val repliesToken: String = "",
    /** Token YouTube wants when replying to this comment; empty when YouTube didn't give one. */
    val replyParams: String = "",
    /** The actions YouTube gave for this comment's like / dislike buttons (and for taking them back). */
    val likeAction: String = "",
    val dislikeAction: String = "",
    val unlikeAction: String = "",
    val undislikeAction: String = ""
)

data class VideoDetails(
    val video: VideoItem,
    val description: String,
    val likes: Long,
    val commentCount: Long,
    val embeddable: Boolean,
    val likesText: String = "",
    val commentCountText: String = "",
    val channel: ChannelItem? = null,
    val related: List<VideoItem> = emptyList(),
    val relatedNext: String? = null,
    /** What YouTube says about your own rating of this video: LIKE, DISLIKE or empty. */
    val likeStatus: String = ""
)

/** One entry of YouTube's notification inbox (new upload, comment, reply, mention, live...). */
data class NotificationItem(
    val id: String,
    val message: String,
    val time: String,
    /** Channel / profile picture. */
    val avatar: String,
    /** Video thumbnail (empty when the notification has none). */
    val thumb: String,
    val read: Boolean,
    /** upload | live | premiere | comment | reply | mention | other */
    val kind: String,
    val videoId: String = "",
    val channelId: String = "",
    val handle: String = ""
)

data class LocalPlaylist(
    val id: String,
    val name: String,
    val videos: List<VideoItem>
)

/** One page of results; [next] is the API page token, or null on the last page. */
data class Page<out T>(val items: List<T>, val next: String?)

class ApiException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NO_KEY, BAD_KEY, QUOTA, NETWORK, NOT_FOUND, COMMENTS_OFF, OTHER }
}

// ---------- JSON (for local storage + backup) ----------

fun VideoItem.toJson(): JSONObject = JSONObject()
    .put("id", id).put("title", title).put("cid", channelId).put("ct", channelTitle)
    .put("pub", publishedAt).put("dur", durationSec).put("views", views).put("live", live)
    .put("vt", viewsText).put("at", ageText).put("short", short)

fun JSONObject.toVideo(): VideoItem = VideoItem(
    id = optString("id"),
    title = optString("title"),
    channelId = optString("cid"),
    channelTitle = optString("ct"),
    publishedAt = optString("pub"),
    durationSec = optLong("dur", -1),
    views = optLong("views", -1),
    live = optBoolean("live", false),
    viewsText = optString("vt"),
    ageText = optString("at"),
    short = optBoolean("short", false)
)

fun ChannelItem.toJson(): JSONObject = JSONObject()
    .put("id", id).put("title", title).put("thumb", thumb).put("subs", subs).put("st", subsText)

fun JSONObject.toChannel(): ChannelItem = ChannelItem(
    id = optString("id"),
    title = optString("title"),
    thumb = optString("thumb"),
    subs = optLong("subs", -1),
    subsText = optString("st")
)

fun LocalPlaylist.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name)
    .put("videos", JSONArray().also { a -> videos.forEach { a.put(it.toJson()) } })

fun JSONObject.toLocalPlaylist(): LocalPlaylist {
    val arr = optJSONArray("videos") ?: JSONArray()
    val vids = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toVideo() }
    return LocalPlaylist(optString("id"), optString("name"), vids)
}

/** Maps every JSON object in the array; entries for which [f] returns null are skipped. */
fun <T : Any> JSONArray.mapObjects(f: (JSONObject) -> T?): List<T> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let { f(it) } }

/** A post on a channel's Posts tab. */
data class Post(
    val id: String,
    val author: String,
    val avatar: String,
    val text: String,
    val age: String,
    val likes: String,
    val comments: String,
    val image: String,
    val video: String,
    val videoTitle: String,
    val poll: String,
    /** The channel the post belongs to (set when it comes from a channel's Posts tab). */
    val channelId: String = ""
)
