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
    val live: Boolean = false
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
    val handle: String = ""
)

data class PlaylistItem(
    val id: String,
    val title: String,
    val channelTitle: String = "",
    val thumb: String = "",
    val count: Int = -1
)

data class Comment(
    val author: String,
    val avatar: String,
    val text: String,
    val likes: Long,
    val publishedAt: String,
    val replies: Int
)

data class VideoDetails(
    val video: VideoItem,
    val description: String,
    val likes: Long,
    val commentCount: Long,
    val embeddable: Boolean
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

fun JSONObject.toVideo(): VideoItem = VideoItem(
    id = optString("id"),
    title = optString("title"),
    channelId = optString("cid"),
    channelTitle = optString("ct"),
    publishedAt = optString("pub"),
    durationSec = optLong("dur", -1),
    views = optLong("views", -1),
    live = optBoolean("live", false)
)

fun ChannelItem.toJson(): JSONObject = JSONObject()
    .put("id", id).put("title", title).put("thumb", thumb).put("subs", subs)

fun JSONObject.toChannel(): ChannelItem = ChannelItem(
    id = optString("id"),
    title = optString("title"),
    thumb = optString("thumb"),
    subs = optLong("subs", -1)
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
