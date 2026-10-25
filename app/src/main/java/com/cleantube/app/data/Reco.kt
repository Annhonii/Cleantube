package com.cleantube.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh
import kotlin.random.Random

/**
 * The app's own recommendation engine. Everything runs on the phone, on the user's own signals.
 *
 * It follows the same three stages as the big systems, scaled down:
 *  1. Candidate generation: the newest videos of the subscribed channels (cheap, fast), plus the "up next" videos of
 *     what was opened (adjacent topics) and, for Shorts, a topic search when a topic heats up in the session.
 *  2. Ranking: one score per candidate = freshness + channel affinity + topic affinity + exploration, minus
 *     impression fatigue and negative feedback (dislike / swiped away).
 *  3. Re-ranking: no repeats, at most 2 per channel in a page, and a 70 / 20 / 10 mix of
 *     (best matches / adjacent topics / unknown channels).
 *
 * Shorts have no thumbnail click, so the signal is the dwell time: swiped away within ~2.5 s is negative, watching
 * (almost) to the end or looping is positive. The session state reacts after 2 Shorts, not days later.
 * The weights below are plain starting values: tune them.
 */
object Reco {
    // ------------------------------------------------------------------ tunable weights
    private const val W_FRESH = 3.0
    private const val FRESH_DAYS = 7.0          // freshness halves roughly every ~5 days
    private const val W_CHANNEL = 2.0
    private const val W_TOPIC = 1.5
    private const val W_EXPLORE = 0.6
    private const val FATIGUE_AFTER = 3         // shown this many times without being opened -> pushed down
    private const val W_FATIGUE = 1.2
    private const val MIX_MAIN = 7              // of every 10 slots
    private const val MIX_ADJACENT = 2
    private const val MIX_EXPLORE = 1
    private const val MAX_PER_CHANNEL_PER_PAGE = 2
    private const val HOME_PAGE = 15
    private const val SHORTS_PAGE = 8
    private const val CHANNEL_BATCH = 10
    private const val CHANNEL_TTL_MS = 20L * 60 * 1000
    private const val SUBS_TTL_MS = 6L * 60 * 60 * 1000
    private const val SWIPE_AWAY_MS = 2_500L

    private enum class Src { SUB, RELATED, INJECT, DISCOVER, TOPIC }

    private class Cand(val v: VideoItem, val src: Src)

    // ------------------------------------------------------------------ state
    private var sp: android.content.SharedPreferences? = null
    private val lock = Any()

    private val chan = HashMap<String, Double>()      // channel id -> affinity
    private val topic = HashMap<String, Double>()     // word -> affinity
    private val impressions = HashMap<String, Int>()  // video id -> times shown and not opened
    private val watched = LinkedHashSet<String>()     // opened videos (never recommended again)
    private val servedShorts = LinkedHashSet<String>()
    private val meta = HashMap<String, VideoItem>()   // what we know about a video (for rating signals)
    private var lastDecayDay = 0L
    private var seededAt = 0L
    /** Channels the interests come from (id -> name / logo), so their Shorts can be fetched even when not subscribed. */
    private val chanInfo = LinkedHashMap<String, ChannelItem>()
    private val topicFetchedAt = HashMap<String, Long>()
    private val seedLock = Mutex()

    private val pool = LinkedHashMap<String, Cand>()        // long videos
    private val shortPool = LinkedHashMap<String, Cand>()   // Shorts
    private val homeServed = LinkedHashSet<String>()
    private val chanFetchedAt = HashMap<String, Long>()
    private val chanShortsFetchedAt = HashMap<String, Long>()

    private var subs: List<ChannelItem> = emptyList()
    private var subsAt = 0L
    private val fetchLock = Mutex()
    private val shortsLock = Mutex()

    // Shorts session state (short term, in memory only)
    private val sessionTopic = HashMap<String, Double>()
    private var lastEngagedWords: Set<String> = emptySet()
    private var pendingInject: String? = null
    private var lastInjectAt = 0L

    fun init(context: Context) {
        if (sp != null) return
        val p = context.applicationContext.getSharedPreferences("reco_v1", Context.MODE_PRIVATE)
        sp = p
        runCatching {
            val j = JSONObject(p.getString("state", "{}") ?: "{}")
            j.optJSONObject("chan")?.let { o -> o.keys().forEach { k -> chan[k] = o.optDouble(k) } }
            j.optJSONObject("topic")?.let { o -> o.keys().forEach { k -> topic[k] = o.optDouble(k) } }
            j.optJSONObject("imp")?.let { o -> o.keys().forEach { k -> impressions[k] = o.optInt(k) } }
            j.optJSONArray("watched")?.let { a -> for (i in 0 until a.length()) watched.add(a.optString(i)) }
            j.optJSONArray("shorts")?.let { a -> for (i in 0 until a.length()) servedShorts.add(a.optString(i)) }
            lastDecayDay = j.optLong("decayDay", 0L)
            seededAt = j.optLong("seededAt", 0L)
            j.optJSONObject("ci")?.let { o -> o.keys().forEach { k -> o.optJSONArray(k)?.let { a -> chanInfo[k] = ChannelItem(id = k, title = a.optString(0), thumb = a.optString(1)) } } }
        }
        decayIfNewDay()
    }

    private fun decayIfNewDay() = synchronized(lock) {
        val day = System.currentTimeMillis() / 86_400_000L
        if (lastDecayDay == 0L) lastDecayDay = day
        val days = (day - lastDecayDay).coerceIn(0, 60)
        if (days > 0) {
            val f = Math.pow(0.93, days.toDouble())   // interests fade slowly when not refreshed
            chan.replaceAll { _, v -> v * f }
            topic.replaceAll { _, v -> v * f }
            chan.entries.removeAll { abs(it.value) < 0.05 }
            topic.entries.removeAll { abs(it.value) < 0.05 }
            lastDecayDay = day
            save()
        }
    }

    private fun save() {
        val p = sp ?: return
        synchronized(lock) {
            fun <T> capped(m: Map<String, T>, n: Int, size: (T) -> Double): Map<String, T> =
                if (m.size <= n) m else m.entries.sortedByDescending { size(it.value) }.take(n).associate { it.key to it.value }
            val j = JSONObject()
            j.put("chan", JSONObject(capped(chan, 400) { abs(it) }))
            j.put("topic", JSONObject(capped(topic, 600) { abs(it) }))
            j.put("imp", JSONObject(capped(impressions, 800) { it.toDouble() }))
            j.put("watched", JSONArray(watched.toList().takeLast(1500)))
            j.put("shorts", JSONArray(servedShorts.toList().takeLast(2000)))
            j.put("decayDay", lastDecayDay)
            j.put("seededAt", seededAt)
            val ci = JSONObject()
            chanInfo.entries.sortedByDescending { chan[it.key] ?: 0.0 }.take(150).forEach { ci.put(it.key, JSONArray(listOf(it.value.title, it.value.thumb))) }
            j.put("ci", ci)
            p.edit().putString("state", j.toString()).apply()
        }
    }

    // ------------------------------------------------------------------ text -> topic words
    private val stop = setOf(
        "the", "and", "for", "with", "this", "that", "from", "how", "what", "why", "you", "your", "are", "was", "who",
        "video", "videos", "official", "full", "new", "best", "part", "episode", "ep", "shorts", "short", "live",
        "ka", "ki", "ke", "hai", "hain", "kya", "aur", "se", "ko", "mein", "main", "ye", "yeh", "wo", "vs", "hindi"
    )

    private fun words(title: String): List<String> =
        title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in stop && !it.all { c -> c.isDigit() } }.distinct()

    // ------------------------------------------------------------------ signals
    /** Remember what we know about a video (used later when it is liked / opened). */
    fun remember(v: VideoItem) = synchronized(lock) {
        if (meta.size > 3000) meta.keys.take(1000).forEach { meta.remove(it) }
        meta[v.id] = v
    }

    private fun bump(v: VideoItem?, channelDelta: Double, topicDelta: Double) = synchronized(lock) {
        if (v == null) return@synchronized
        if (v.channelId.isNotEmpty()) chan[v.channelId] = (chan[v.channelId] ?: 0.0) + channelDelta
        words(v.title).take(8).forEach { topic[it] = (topic[it] ?: 0.0) + topicDelta }
    }

    /** A long video was opened. */
    fun onOpen(v: VideoItem) {
        synchronized(lock) {
            remember(v)
            watched.add(v.id)
            impressions.remove(v.id)
        }
        bump(v, 0.3, 0.1)
        save()
    }

    /** The video was watched for a while (counted by the caller after ~30 s). */
    fun onEngaged(videoId: String) {
        bump(meta[videoId], 1.5, 0.8)
        save()
    }

    /** Like / dislike. [status] is "like", "dislike" or "none". */
    fun onRate(videoId: String, status: String) {
        val v = meta[videoId]
        when (status) {
            "like" -> bump(v, 3.0, 1.5)
            "dislike" -> {
                bump(v, -6.0, -3.0)
                synchronized(lock) { watched.add(videoId) }
            }
        }
        save()
    }

    /** Up Next videos of an opened video: adjacent-topic candidates (no extra request). */
    fun addRelated(list: List<VideoItem>) = synchronized(lock) {
        list.filter { !it.short && it.id !in watched }.forEach {
            meta[it.id] = it
            if (it.id !in pool) pool[it.id] = Cand(it, Src.RELATED)
        }
        trimPool()
    }

    /** A Short left the screen after [dwellMs]. This is the Shorts feedback signal. */
    fun onShortLeft(v: VideoItem, dwellMs: Long) {
        remember(v)
        val dur = (if (v.durationSec > 0) v.durationSec else 30L) * 1000L
        val w = words(v.title).take(8)
        when {
            dwellMs < SWIPE_AWAY_MS -> {                           // swiped away: negative
                bump(v, -0.6, -0.4)
                synchronized(lock) { w.forEach { sessionTopic[it] = (sessionTopic[it] ?: 0.0) - 1.0 } }
                lastEngagedWords = emptySet()
            }
            dwellMs >= minOf((dur * 0.8).toLong(), 12_000L) -> {   // watched (almost) to the end, or looped: positive
                val loops = dwellMs >= (dur * 1.3).toLong()
                bump(v, if (loops) 1.5 else 1.0, if (loops) 1.0 else 0.8)
                synchronized(lock) {
                    w.forEach { sessionTopic[it] = (sessionTopic[it] ?: 0.0) + 1.0 }
                    // two engaged Shorts in a row that share a word -> that word is hot right now
                    val common = w.toSet().intersect(lastEngagedWords)
                    if (common.isNotEmpty()) pendingInject = common.maxByOrNull { sessionTopic[it] ?: 0.0 }
                    lastEngagedWords = w.toSet()
                }
            }
        }
        save()
    }

    // ------------------------------------------------------------------ subscriptions
    private suspend fun subscriptions(): List<ChannelItem> {
        val now = System.currentTimeMillis()
        if (subs.isNotEmpty() && now - subsAt < SUBS_TTL_MS) return subs
        val local = runCatching { Graph.store.subscriptions }.getOrDefault(emptyList())
        val remote = if (Session.loggedIn && !Playback.isBlocked()) runCatching { Api.accountChannels() }.getOrDefault(emptyList()) else emptyList()
        val all = (remote + local).distinctBy { it.id }
        if (all.isNotEmpty()) { subs = all; subsAt = now }
        return subs
    }

    /** Stalest / most liked channels first. */
    private fun nextChannels(times: Map<String, Long>, n: Int, from: List<ChannelItem> = subs): List<ChannelItem> {
        val now = System.currentTimeMillis()
        return from.filter { now - (times[it.id] ?: 0L) > CHANNEL_TTL_MS }
            .sortedWith(compareBy<ChannelItem> { times.containsKey(it.id) }.thenByDescending { chan[it.id] ?: 0.0 }.thenBy { times[it.id] ?: 0L })
            .take(n)
    }

    private fun trimPool() {
        while (pool.size > 700) pool.remove(pool.keys.first())
        while (shortPool.size > 500) shortPool.remove(shortPool.keys.first())
    }

    /** Loads the newest videos of the next few channels into the pool. Returns how many channels were fetched. */
    private suspend fun fetchBatch(n: Int = CHANNEL_BATCH): Int = fetchLock.withLock {
        if (Playback.isBlocked()) return 0
        val batch = synchronized(lock) { nextChannels(chanFetchedAt, n) }
        if (batch.isEmpty()) return 0
        val gate = Semaphore(3)
        coroutineScope {
            batch.map { c ->
                async(Dispatchers.Default) {
                    gate.withPermit {
                        val items = runCatching { Api.channelVideos(c.id, null).items }.getOrNull()
                        if (items == null) synchronized(lock) { chanFetchedAt[c.id] = System.currentTimeMillis() }
                        if (items != null) synchronized(lock) {
                            chanFetchedAt[c.id] = System.currentTimeMillis()
                            items.filter { !it.short }.take(10).forEach {
                                val v = it.copy(channelId = c.id, channelTitle = it.channelTitle.ifEmpty { c.title }, channelThumb = it.channelThumb.ifEmpty { c.thumb })
                                meta[v.id] = v
                                pool[v.id] = Cand(v, Src.SUB)
                            }
                            trimPool()
                        }
                    }
                }
            }.awaitAll()
        }
        batch.size
    }

    private suspend fun fetchShortsBatch(n: Int = 8): Int = shortsLock.withLock {
        if (Playback.isBlocked()) return 0
        val batch = synchronized(lock) { nextChannels(chanShortsFetchedAt, n, shortChannels()) }
        if (batch.isEmpty()) return 0
        val gate = Semaphore(3)
        coroutineScope {
            batch.map { c ->
                async(Dispatchers.Default) {
                    gate.withPermit {
                        val items = runCatching { Api.channelVideosTab(c.id, "shorts", null).items }.getOrNull()
                        if (items == null) synchronized(lock) { chanShortsFetchedAt[c.id] = System.currentTimeMillis() }
                        if (items != null) synchronized(lock) {
                            chanShortsFetchedAt[c.id] = System.currentTimeMillis()
                            items.take(12).forEach {
                                val v = it.copy(channelId = c.id, channelTitle = it.channelTitle.ifEmpty { c.title }, short = true)
                                meta[v.id] = v
                                shortPool[v.id] = Cand(v, Src.SUB)
                            }
                            trimPool()
                        }
                    }
                }
            }.awaitAll()
        }
        batch.size
    }

    // ------------------------------------------------------------------ ranking
    private fun score(c: Cand, now: Long, sessionBoost: Boolean): Double {
        val v = c.v
        val days = Api.ageMinutes(v.ageText).let { if (it == Long.MAX_VALUE) 30.0 else it / 1440.0 }
        val fresh = W_FRESH * exp(-days / FRESH_DAYS)
        val chA = tanh((chan[v.channelId] ?: 0.0) / 4.0) * W_CHANNEL
        val tops = words(v.title).map { topic[it] ?: 0.0 }.sortedDescending().take(3)
        val toA = if (tops.isEmpty()) 0.0 else tanh(tops.average() / 3.0) * W_TOPIC
        val explore = if ((chan[v.channelId] ?: 0.0) <= 0.0) Random.nextDouble() * W_EXPLORE else 0.0
        val imp = impressions[v.id] ?: 0
        val fatigue = if (imp >= FATIGUE_AFTER) W_FATIGUE * (imp - FATIGUE_AFTER + 1) else 0.0
        val session = if (sessionBoost) words(v.title).sumOf { sessionTopic[it] ?: 0.0 }.coerceIn(-3.0, 4.0) * 0.5 else 0.0
        val inject = if (c.src == Src.INJECT) 1.5 else 0.0
        return fresh + chA + toA + explore + session + inject - fatigue
    }

    /** Picks [size] items from the queues following [rhythm] (queue numbers), with at most 2 per channel. */
    private fun mixN(lists: List<List<Cand>>, rhythm: List<Int>, size: Int): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        val used = HashSet<String>()
        val perChannel = HashMap<String, Int>()
        val queues = lists.map { ArrayDeque(it) }
        fun take(q: ArrayDeque<Cand>): Boolean {
            while (q.isNotEmpty()) {
                val c = q.removeFirst()
                if (c.v.id in used) continue
                if ((perChannel[c.v.channelId] ?: 0) >= MAX_PER_CHANNEL_PER_PAGE) continue
                used.add(c.v.id)
                perChannel[c.v.channelId] = (perChannel[c.v.channelId] ?: 0) + 1
                out.add(c.v)
                return true
            }
            return false
        }
        var slot = 0
        while (out.size < size && queues.any { it.isNotEmpty() }) {
            val want = rhythm[slot % rhythm.size]
            slot++
            if (!take(queues[want])) {
                // that queue is empty: use whichever other one still has something
                val other = queues.indices.firstOrNull { take(queues[it]) }
                if (other == null) break
            }
        }
        return out
    }

    /** The old 7 / 2 / 1 mix of three queues (Shorts). */
    private fun mix(main: List<Cand>, adj: List<Cand>, exp: List<Cand>, size: Int): List<VideoItem> =
        mixN(listOf(main, adj, exp), List(MIX_MAIN) { 0 } + List(MIX_ADJACENT) { 1 } + List(MIX_EXPLORE) { 2 }, size)

    // ------------------------------------------------------------------ taste from the account
    /**
     * Cold start fix: instead of learning from zero, read what the signed-in account already watched and liked on YouTube
     * (history + Liked videos) and turn it into channel / topic interests. Done at most once every 3 days. Everything
     * seen there is also never recommended again.
     */
    private suspend fun seedFromAccount() = seedLock.withLock {
        val now = System.currentTimeMillis()
        if (!Session.loggedIn || Playback.isBlocked() || now - seededAt < 3L * 86_400_000L) return@withLock
        // the very first time: read (nearly) the whole history; later only the newest part
        val deep = seededAt == 0L
        val hist = mutableListOf<VideoItem>()
        runCatching {
            var t: String? = null
            var n = 0
            do {
                val p = Api.accountHistory(t)
                hist += p.items
                t = p.next
                n++
            } while (t != null && n < (if (deep) 12 else 3))
        }
        val liked = runCatching { Api.playlistVideos("LL", null).items }.getOrDefault(emptyList())
        if (hist.isEmpty() && liked.isEmpty()) return@withLock
        synchronized(lock) {
            // newer history counts more: the first items are the most recent
            val h = hist.take(400)
            h.forEachIndexed { i, v ->
                val w = 1.0 - i / (h.size * 1.3)
                if (v.channelId.isNotEmpty()) {
                    chan[v.channelId] = (chan[v.channelId] ?: 0.0) + 0.5 * w
                    if (v.channelId !in chanInfo) chanInfo[v.channelId] = ChannelItem(id = v.channelId, title = v.channelTitle, thumb = v.channelThumb)
                }
                words(v.title).take(8).forEach { topic[it] = (topic[it] ?: 0.0) + 0.25 * w }
            }
            liked.take(60).forEach { v ->
                if (v.channelId.isNotEmpty()) {
                    chan[v.channelId] = (chan[v.channelId] ?: 0.0) + 1.5
                    if (v.channelId !in chanInfo) chanInfo[v.channelId] = ChannelItem(id = v.channelId, title = v.channelTitle, thumb = v.channelThumb)
                }
                words(v.title).take(8).forEach { topic[it] = (topic[it] ?: 0.0) + 0.8 }
            }
            (hist + liked).forEach { watched.add(it.id) }
            seededAt = now
        }
        save()
    }

    /** Channels whose Shorts we pull: the subscriptions plus the channels the history says you like (even if not subscribed). */
    private fun shortChannels(): List<ChannelItem> = synchronized(lock) {
        val liked = chanInfo.values.filter { (chan[it.id] ?: 0.0) > 0.4 && it.title.isNotEmpty() }
            .sortedByDescending { chan[it.id] ?: 0.0 }.take(40)
        (liked + subs).distinctBy { it.id }
    }

    /** Topic searches for Shorts: your strongest interests right now, a few at a time, not more often than every 20 min each. */
    private suspend fun fetchShortTopics() {
        if (Playback.isBlocked()) return
        val now = System.currentTimeMillis()
        val topics = synchronized(lock) {
            topic.entries.filter { it.value > 0.6 }
                .sortedByDescending { it.value + (sessionTopic[it.key] ?: 0.0) }
                .map { it.key }.filter { now - (topicFetchedAt[it] ?: 0L) > 20 * 60_000L }.take(3)
        }
        if (topics.isEmpty()) return
        topics.forEach { synchronized(lock) { topicFetchedAt[it] = now } }
        coroutineScope {
            topics.map { t ->
                async(Dispatchers.Default) { runCatching { Api.shorts(null, t) }.getOrNull()?.items.orEmpty() }
            }.awaitAll().flatten().let { found ->
                synchronized(lock) {
                    found.forEach {
                        if (it.id !in servedShorts && it.id !in shortPool) { meta[it.id] = it; shortPool[it.id] = Cand(it.copy(short = true), Src.TOPIC) }
                    }
                    trimPool()
                }
            }
        }
    }

    // ------------------------------------------------------------------ discovery (beyond the subscriptions)
    private var ytHomeToken: String? = null
    private var ytHomeFetched = 0
    private val discoverLock = Mutex()
    private var ytShortsToken: String? = null

    /**
     * Candidates from outside the subscriptions, like YouTube's own Home: YouTube's own recommendation feed (personalised
     * when signed in) plus searches for the topics this phone has seen you like most.
     */
    private suspend fun fetchDiscover() = discoverLock.withLock {
        if (Playback.isBlocked()) return@withLock
        coroutineScope {
            val feed = async(Dispatchers.Default) {
                runCatching { Api.youtubeHome(ytHomeToken) }.getOrNull()
            }
            val topics = synchronized(lock) {
                topic.entries.filter { it.value > 1.0 }.sortedByDescending { it.value }.map { it.key }.take(6)
            }
            val query = if (topics.size >= 2) topics.shuffled().take(2).joinToString(" ") else null
            val searched = async(Dispatchers.Default) {
                if (query == null) null else runCatching { Api.searchVideos(query, null) }.getOrNull()
            }
            val f = feed.await()
            val s = searched.await()
            synchronized(lock) {
                if (f != null) {
                    ytHomeToken = f.next
                    ytHomeFetched++
                    f.items.filter { !it.short }.forEach { meta[it.id] = it; if (it.id !in pool) pool[it.id] = Cand(it, Src.DISCOVER) }
                }
                s?.items?.filter { !it.short && it.durationSec !in 1..62 }?.take(10)?.forEach {
                    meta[it.id] = it; if (it.id !in pool) pool[it.id] = Cand(it, Src.DISCOVER)
                }
                trimPool()
            }
        }
    }

    // ------------------------------------------------------------------ Home (long videos)
    /** Null when there is nothing to recommend from (no subscriptions): the caller then uses YouTube's own feed. */
    suspend fun homePage(token: String?): Page<VideoItem>? {
        decayIfNewDay()
        if (subscriptions().isEmpty()) return null
        val first = token == null
        if (first) runCatching { seedFromAccount() }
        if (first) {
            synchronized(lock) { homeServed.clear() }
            ytHomeToken = null
            // a quick first batch; the rest of the channels load as the user scrolls
            if (synchronized(lock) { pool.size } < HOME_PAGE || chanFetchedAt.isEmpty()) fetchBatch(CHANNEL_BATCH)
            else runCatching { fetchBatch(4) }
        }
        // YouTube's own recommendations + topic searches: the part of Home that is NOT about the subscriptions
        if (first || synchronized(lock) { pool.values.count { it.src == Src.DISCOVER && it.v.id !in homeServed } } < HOME_PAGE) {
            if (first || ytHomeToken != null) runCatching { fetchDiscover() }
        }
        var page = buildHomePage()
        var tries = 0
        while (page.size < HOME_PAGE / 2 && tries++ < 3 && fetchBatch(CHANNEL_BATCH) > 0) page = buildHomePage()
        if (page.isEmpty()) return if (first) null else Page(emptyList(), null)
        val more = synchronized(lock) {
            nextChannels(chanFetchedAt, 1).isNotEmpty() || pool.size - homeServed.size > 0 || ytHomeToken != null
        }
        return Page(page, if (more) "L:${synchronized(lock) { homeServed.size }}" else null)
    }

    private fun buildHomePage(): List<VideoItem> = synchronized(lock) {
        val now = System.currentTimeMillis()
        val history = runCatching { Graph.store.history.map { it.id }.toSet() }.getOrDefault(emptySet())
        val ranked = pool.values.filter { it.v.id !in watched && it.v.id !in history && it.v.id !in homeServed }
            .map { it to score(it, now, false) }.sortedByDescending { it.second }.map { it.first }
        val subsKnown = ranked.filter { it.src == Src.SUB && (chan[it.v.channelId] ?: 0.0) > 0.0 }
        val subsOther = ranked.filter { it.src == Src.SUB && (chan[it.v.channelId] ?: 0.0) <= 0.0 }
        val disc = ranked.filter { it.src == Src.DISCOVER }
        val adj = ranked.filter { it.src == Src.RELATED }
        // Like YouTube's Home: about 4 of 10 from outside your subscriptions (YouTube's own picks + topics you like),
        // 3 from channels you watch most, 2 "up next" style, 1 from a subscription you have not watched yet.
        // Before any history exists the subscriptions' freshness ranking leads.
        val known = if (subsKnown.isEmpty()) subsOther else subsKnown
        val other = if (subsKnown.isEmpty()) emptyList() else subsOther.shuffled().take(30)
        val page = mixN(listOf(known, disc, adj, other), listOf(1, 0, 1, 2, 0, 1, 3, 1, 0, 2), HOME_PAGE)
        page.forEach {
            homeServed.add(it.id)
            impressions[it.id] = (impressions[it.id] ?: 0) + 1
        }
        page
    }.also { save() }

    // ------------------------------------------------------------------ Shorts
    /** True when this video is in the YouTube account's history (read at sign-in) or was opened here. */
    fun isWatched(id: String): Boolean = synchronized(lock) { id in watched }

    /** Null when there is nothing to recommend from (no subscriptions): the caller then uses YouTube's own Shorts feed. */
    suspend fun shortsPage(token: String?): Page<VideoItem>? {
        decayIfNewDay()
        subscriptions()
        if (token == null) runCatching { seedFromAccount() }
        // with neither subscriptions nor any taste data there is nothing to go on: YouTube's own feed
        if (subs.isEmpty() && synchronized(lock) { chan.isEmpty() && topic.isEmpty() }) return null
        val first = token == null
        val unserved = { synchronized(lock) { shortPool.values.count { it.v.id !in servedShorts } } }
        if (first && unserved() < SHORTS_PAGE) fetchShortsBatch(8)
        // real-time adaptation: a topic got hot in this session -> pull 3-5 Shorts of it right now
        val hot = synchronized(lock) { pendingInject.also { pendingInject = null } }
        if (hot != null && System.currentTimeMillis() - lastInjectAt > 60_000L && !Playback.isBlocked()) {
            lastInjectAt = System.currentTimeMillis()
            runCatching { Api.shorts(null, hot) }.getOrNull()?.items?.take(5)?.let { found ->
                synchronized(lock) {
                    found.forEach { if (it.id !in servedShorts) { meta[it.id] = it; shortPool[it.id] = Cand(it.copy(short = true), Src.INJECT) } }
                }
            }
        }
        // Shorts of the topics you like (searched), and of the channels you watch most
        if (first || unserved() < SHORTS_PAGE * 2) runCatching { fetchShortTopics() }
        var tries = 0
        while (unserved() < SHORTS_PAGE && tries++ < 3 && fetchShortsBatch(8) > 0) { /* keep filling */ }
        // YouTube's own Shorts recommendations are part of the mix too (not only the subscribed channels' Shorts)
        if (first) ytShortsToken = null
        if (!Playback.isBlocked() && (first || synchronized(lock) { shortPool.values.count { it.src == Src.DISCOVER && it.v.id !in servedShorts } } < SHORTS_PAGE)) {
            runCatching { Api.youtubeShorts(ytShortsToken) }.getOrNull()?.let { p ->
                ytShortsToken = p.next
                synchronized(lock) {
                    p.items.forEach { if (it.id !in servedShorts && it.id !in shortPool) { meta[it.id] = it; shortPool[it.id] = Cand(it.copy(short = true), Src.DISCOVER) } }
                }
            }
        }
        val page = synchronized(lock) {
            val now = System.currentTimeMillis()
            // a Short that is in the app's history, was seen, or is in the YouTube account's history never comes back
            val hist = runCatching { Graph.store.history.mapTo(HashSet()) { it.id } }.getOrDefault(HashSet())
            val ranked = shortPool.values.filter {
                it.v.id !in servedShorts && it.v.id !in watched && it.v.id !in hist &&
                    !runCatching { Graph.store.isShortSeen(it.v.id) }.getOrDefault(false)
            }
                .map { it to score(it, now, true) }.sortedByDescending { it.second }.map { it.first }
            val inj = ranked.filter { it.src == Src.INJECT }
            val disc = ranked.filter { it.src == Src.DISCOVER }
            // what matches your interests (channel you like, or a topic you like), from any source
            fun likes(c: Cand): Boolean = (chan[c.v.channelId] ?: 0.0) > 0.0 || words(c.v.title).any { (topic[it] ?: 0.0) > 0.8 }
            val mine = ranked.filter { it.src != Src.INJECT && it.src != Src.DISCOVER && likes(it) }
            val rest0 = ranked.filter { it.src != Src.INJECT && it.src != Src.DISCOVER && !likes(it) }
            // a hot topic of this session goes first, then: 5 of 8 match your interests, 2 are YouTube's own picks, 1 explores
            val first3 = inj.take(3).map { it.v }
            val rest = mixN(
                listOf(if (mine.isEmpty()) rest0 else mine, disc, if (mine.isEmpty()) emptyList() else rest0.shuffled()),
                listOf(0, 0, 1, 0, 0, 2, 1, 0), SHORTS_PAGE - first3.size
            )
            (first3 + rest).distinctBy { it.id }
        }
        if (page.isEmpty()) return if (first) null else Page(emptyList(), null)
        synchronized(lock) { page.forEach { servedShorts.add(it.id) } }
        save()
        val more = synchronized(lock) {
            nextChannels(chanShortsFetchedAt, 1, shortChannels()).isNotEmpty() || shortPool.values.any { it.v.id !in servedShorts } || topic.isNotEmpty()
        }
        return Page(page, if (more) "L:${synchronized(lock) { servedShorts.size }}" else null)
    }
}
