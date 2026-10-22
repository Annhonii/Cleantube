package com.cleantube.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Everything lives on the phone (like NewPipe): no account, no server. */
class Store(context: Context) {

    private val sp = context.getSharedPreferences("cleantube", Context.MODE_PRIVATE)

    // ------------------------------------------------------------ settings

    private val keyState = mutableStateOf(sp.getString("api_key", "").orEmpty())
    var apiKey: String
        get() = keyState.value
        set(value) {
            keyState.value = value.trim()
            sp.edit().putString("api_key", value.trim()).apply()
        }

    private val regionState = mutableStateOf(sp.getString("region", "IN").orEmpty().ifEmpty { "IN" })
    var region: String
        get() = regionState.value
        set(value) {
            val v = value.trim().uppercase().take(2).ifEmpty { "IN" }
            regionState.value = v
            sp.edit().putString("region", v).apply()
        }

    private val dlWhereState = mutableStateOf(sp.getString("dl_where", "app").orEmpty())
    /** Default place for downloads: "app" (private to this app) or "phone" (Movies / Music folder). */
    var downloadWhere: String
        get() = dlWhereState.value
        set(value) {
            dlWhereState.value = value
            sp.edit().putString("dl_where", value).apply()
        }

    private val dlThreadsState = mutableStateOf(sp.getInt("dl_threads", 8))
    /** How many connections one download uses (1 to 32). */
    var downloadThreads: Int
        get() = dlThreadsState.value
        set(value) {
            val v = value.coerceIn(1, 32)
            dlThreadsState.value = v
            sp.edit().putInt("dl_threads", v).apply()
        }

    private val autoPopupState = mutableStateOf(sp.getBoolean("auto_popup", true))
    /** Leaving the app while a video plays opens the floating popup window by itself. */
    var autoPopup: Boolean
        get() = autoPopupState.value
        set(value) {
            autoPopupState.value = value
            sp.edit().putBoolean("auto_popup", value).apply()
        }

    private val miniWidthState = mutableStateOf(sp.getFloat("mini_width_dp", 200f).coerceIn(140f, 320f))
    /** Width of the floating mini player in dp (the user resizes it with the corner handle). */
    var miniWidthDp: Float
        get() = miniWidthState.value
        set(value) {
            val v = value.coerceIn(140f, 320f)
            miniWidthState.value = v
            sp.edit().putFloat("mini_width_dp", v).apply()
        }

    // ---------------------------------------------------------------- tabs

    private fun readCsv(key: String): List<String> =
        sp.getString(key, "").orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private val tabOrderState = mutableStateOf(readCsv("tab_order").map { if (it == "history") "notifications" else it }.distinct())
    /** Tab ids in the order the user wants them (empty = default order). The UI fills in missing tabs. */
    var tabOrder: List<String>
        get() = tabOrderState.value
        set(value) {
            tabOrderState.value = value
            sp.edit().putString("tab_order", value.joinToString(",")).apply()
        }

    private val hiddenTabsState = mutableStateOf(if (sp.contains("tab_hidden")) readCsv("tab_hidden").map { if (it == "history") "notifications" else it }.toSet() else DefaultHiddenTabs)
    /** Tab ids the user turned off. */
    var hiddenTabs: Set<String>
        get() = hiddenTabsState.value
        set(value) {
            hiddenTabsState.value = value
            sp.edit().putString("tab_hidden", value.joinToString(",")).apply()
        }

    private val videoColorState = mutableStateOf(sp.getBoolean("video_color", true))
    /** The app colours follow the thumbnail of the video that is playing. */
    var videoColor: Boolean
        get() = videoColorState.value
        set(value) {
            videoColorState.value = value
            sp.edit().putBoolean("video_color", value).apply()
        }

    private val pureBlackState = mutableStateOf(sp.getBoolean("pure_black", false))
    /** Dark mode uses pure black (AMOLED) instead of the default greyish black. */
    var pureBlack: Boolean
        get() = pureBlackState.value
        set(value) {
            pureBlackState.value = value
            sp.edit().putBoolean("pure_black", value).apply()
        }

    private val fontState = mutableStateOf(sp.getString("app_font", "poppins").orEmpty().ifEmpty { "poppins" })
    /** App font: "poppins" (default) or "system". */
    var appFont: String
        get() = fontState.value
        set(value) {
            fontState.value = value
            sp.edit().putString("app_font", value).apply()
        }

    // ---------------------------------------------------------------- data

    var subscriptions by mutableStateOf(readArray("subs").mapObjects { it.toChannel() })
        private set
    var history by mutableStateOf(readArray("history").mapObjects { it.toVideo() })
        private set
    var searches by mutableStateOf(readStrings("searches"))
        private set
    var playlists by mutableStateOf(readArray("playlists").mapObjects { it.toLocalPlaylist() })
        private set

    private fun readArray(name: String): JSONArray =
        try {
            JSONArray(sp.getString(name, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }

    private fun readStrings(name: String): List<String> {
        val a = readArray(name)
        return (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
    }

    private fun <T> save(name: String, items: List<T>, toJson: (T) -> Any) {
        val a = JSONArray()
        items.forEach { a.put(toJson(it)) }
        sp.edit().putString(name, a.toString()).apply()
    }

    // -------------------------------------------------------- subscriptions

    fun isSubscribed(channelId: String): Boolean = subscriptions.any { it.id == channelId }

    fun toggleSubscription(c: ChannelItem) {
        subscriptions =
            if (isSubscribed(c.id)) subscriptions.filter { it.id != c.id } else subscriptions + c
        save("subs", subscriptions) { it.toJson() }
    }

    /** Adds channels that aren't subscribed locally yet. */
    fun importChannels(list: List<ChannelItem>) {
        val known = subscriptions.map { it.id }.toMutableSet()
        val fresh = list.filter { known.add(it.id) }
        if (fresh.isEmpty()) return
        subscriptions = subscriptions + fresh
        save("subs", subscriptions) { it.toJson() }
    }

    /** Fill in avatars / names for subscriptions that were imported without them. */
    fun refreshSubscriptions(fresh: List<ChannelItem>) {
        val byId = fresh.associateBy { it.id }
        subscriptions = subscriptions.map { byId[it.id] ?: it }
        save("subs", subscriptions) { it.toJson() }
    }

    // -------------------------------------------------------------- history

    fun addHistory(v: VideoItem) {
        history = (listOf(v) + history.filter { it.id != v.id }).take(500)
        save("history", history) { it.toJson() }
    }

    fun removeHistory(id: String) {
        history = history.filter { it.id != id }
        save("history", history) { it.toJson() }
    }

    fun clearHistory() {
        history = emptyList()
        save("history", history) { it.toJson() }
        seenShorts.clear()
        sp.edit().remove("seen_shorts").apply()
    }

    // ---------------------------------------------------------- seen Shorts

    /** Ids of Shorts that were already on screen once (newest last). The Shorts feed never shows these again. */
    private val seenShorts = LinkedHashSet<String>(readCsv("seen_shorts"))

    fun isShortSeen(id: String): Boolean = synchronized(seenShorts) { id in seenShorts }

    fun markShortSeen(id: String) {
        synchronized(seenShorts) {
            if (!seenShorts.add(id)) return
            while (seenShorts.size > 4000) seenShorts.remove(seenShorts.first())
            sp.edit().putString("seen_shorts", seenShorts.joinToString(",")).apply()
        }
    }

    // ------------------------------------------------------------ quick picks cache

    /** Last Quick picks pool, kept so Home shows it instantly on the next app start. */
    fun loadQuickPicks(): List<VideoItem> = readArray("quick_picks").mapObjects { it.toVideo() }.filter { it.id.isNotEmpty() }

    fun saveQuickPicks(list: List<VideoItem>) = save("quick_picks", list) { it.toJson() }

    // ------------------------------------------------------------- searches

    fun addSearch(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        searches = (listOf(t) + searches.filter { !it.equals(t, ignoreCase = true) }).take(30)
        save("searches", searches) { it }
    }

    fun removeSearch(q: String) {
        searches = searches.filter { it != q }
        save("searches", searches) { it }
    }

    fun clearSearches() {
        searches = emptyList()
        save("searches", searches) { it }
    }

    // ------------------------------------------------------------ playlists

    fun playlist(id: String): LocalPlaylist? = playlists.firstOrNull { it.id == id }

    fun createPlaylist(name: String, first: VideoItem? = null): LocalPlaylist {
        val p = LocalPlaylist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Playlist" },
            videos = listOfNotNull(first)
        )
        playlists = playlists + p
        save("playlists", playlists) { it.toJson() }
        return p
    }

    fun toggleInPlaylist(playlistId: String, v: VideoItem) {
        playlists = playlists.map { p ->
            if (p.id != playlistId) p
            else if (p.videos.any { it.id == v.id }) p.copy(videos = p.videos.filter { it.id != v.id })
            else p.copy(videos = p.videos + v)
        }
        save("playlists", playlists) { it.toJson() }
    }

    fun deletePlaylist(id: String) {
        playlists = playlists.filter { it.id != id }
        save("playlists", playlists) { it.toJson() }
    }

    // ------------------------------------------------------ backup / import

    fun exportJson(): String = JSONObject()
        .put("cleantube", 1)
        .put("subscriptions", JSONArray().also { a -> subscriptions.forEach { a.put(it.toJson()) } })
        .put("playlists", JSONArray().also { a -> playlists.forEach { a.put(it.toJson()) } })
        .put("history", JSONArray().also { a -> history.forEach { a.put(it.toJson()) } })
        .toString(2)

    /**
     * Accepts Voidstream's own export and NewPipe's "subscriptions" JSON export.
     * Returns how many new subscriptions were added. Throws on invalid JSON.
     */
    fun importJson(text: String): Int {
        val root = JSONObject(text)
        var added = 0

        root.optJSONArray("subscriptions")?.let { arr ->
            val known = subscriptions.map { it.id }.toMutableSet()
            val fresh = arr.mapObjects { parseImportedChannel(it) }.filter { known.add(it.id) }
            if (fresh.isNotEmpty()) {
                subscriptions = subscriptions + fresh
                save("subs", subscriptions) { it.toJson() }
                added = fresh.size
            }
        }

        root.optJSONArray("playlists")?.let { arr ->
            val ids = playlists.map { it.id }.toSet()
            val fresh = arr.mapObjects { it.toLocalPlaylist() }.filter { it.id.isNotEmpty() && it.id !in ids }
            if (fresh.isNotEmpty()) {
                playlists = playlists + fresh
                save("playlists", playlists) { it.toJson() }
            }
        }

        root.optJSONArray("history")?.let { arr ->
            val ids = history.map { it.id }.toSet()
            val fresh = arr.mapObjects { it.toVideo() }.filter { it.id.isNotEmpty() && it.id !in ids }
            if (fresh.isNotEmpty()) {
                history = (history + fresh).take(300)
                save("history", history) { it.toJson() }
            }
        }
        return added
    }

    private fun parseImportedChannel(o: JSONObject): ChannelItem? {
        val id = o.optString("id").ifEmpty {
            Regex("channel/(UC[0-9A-Za-z_-]{22})").find(o.optString("url"))?.groupValues?.get(1).orEmpty()
        }
        if (id.isEmpty()) return null
        val title = o.optString("title").ifEmpty { o.optString("name") }
        return ChannelItem(
            id = id,
            title = title.ifEmpty { id },
            thumb = o.optString("thumb"),
            subs = o.optLong("subs", -1)
        )
    }
}

/** Tabs that start turned off (they can be turned on in Settings). */
val DefaultHiddenTabs: Set<String> = emptySet()
