package com.cleantube.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    private const val W_CHANNEL = 0.6         // who made it matters little ...
    private const val W_TOPIC = 1.5           // title words: only a small hint
    private const val W_SEED = 4.0            // what YouTube itself relates to what you watched: the main signal
    private const val SEED_TTL_MS = 6L * 60 * 60 * 1000
    private const val CHAN_SHARE = 0.3      // a channel learns at 30 % of the speed ...
    private const val TOPIC_GAIN = 1.5      // ... a topic at 150 %
    private const val PER_CHANNEL_POOL = 4  // newest videos kept per channel (no channel can flood the pool)
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
    private const val SUB_EVERY = 20

    private enum class Src { SUB, RELATED, INJECT, DISCOVER, TOPIC, SEED }

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

    // shown by the "analysing your taste" screen while the first (deep) read of the account's history runs
    var seeding by mutableStateOf(false)
        private set
    var seedStage by mutableStateOf("")
        private set
    var seedProgress by mutableFloatStateOf(0f)
        private set
    /** The screen was dismissed by the user: the analysis keeps running in the background. */
    var seedHidden by mutableStateOf(false)

    /** Starts the account read right after sign-in (not tied to any screen). */
    fun requestSeed() {
        TabCache.appScope.launch { runCatching { seedFromAccount() } }
    }

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
    /** Shorts shown since the last one from a subscribed channel (those come about 1 in [SUB_EVERY]). */
    private var shortsSinceSub = 0
    /** Channels of the last Shorts served: the next batch avoids them, so one channel never shows up twice close together. */
    private val recentShortChannels = ArrayDeque<String>()
    /** Same for Home: channels of the last ~25 videos served, so a channel does not fill the page again and again. */
    private val recentHomeChannels = ArrayDeque<String>()

    // Content graph: the videos you really watched / liked are "seeds"; YouTube's own "related to this video" lists of
    // the seeds are the candidates. A candidate that many (recent, strongly watched) seeds point to is content you like,
    // no matter which channel made it and without looking at words in titles.
    private val seeds = LinkedHashMap<String, Double>()        // video id -> weight (history 0.5, watched 1, liked 2); newest last
    private val seedFetchedAt = HashMap<String, Long>()
    private val seedScore = LinkedHashMap<String, Double>()    // candidate id -> how strongly the seeds point to it
    private val relatedLock = Mutex()

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
            j.optJSONArray("seeds")?.let { a -> for (i in 0 until a.length()) a.optString(i).split("|").let { p -> if (p.size == 2) seeds[p[0]] = p[1].toDoubleOrNull() ?: 0.5 } }
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
            j.put("seeds", JSONArray(seeds.entries.map { "${it.key}|${it.value}" }))
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
        if (v.channelId.isNotEmpty()) chan[v.channelId] = (chan[v.channelId] ?: 0.0) + channelDelta * CHAN_SHARE
        words(v.title).take(8).forEach { topic[it] = (topic[it] ?: 0.0) + topicDelta * TOPIC_GAIN }
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
        addSeed(videoId, 1.0)
        bump(meta[videoId], 1.5, 0.8)
        save()
    }

    /** A video you really watched / liked becomes a seed of the content graph (newest last, 60 kept). */
    private fun addSeed(id: String, w: Double) = synchronized(lock) {
        val old = seeds.remove(id) ?: 0.0
        seeds[id] = maxOf(w, old)
        while (seeds.size > 60) seeds.remove(seeds.keys.first())
    }

    /** Like / dislike. [status] is "like", "dislike" or "none". */
    fun onRate(videoId: String, status: String) {
        val v = meta[videoId]
        when (status) {
            "like" -> { addSeed(videoId, 2.0); bump(v, 3.0, 1.5) }
            "dislike" -> {
                synchronized(lock) { seeds.remove(videoId) }
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

    /**
     * A Short left the screen after [dwellMs] of a clip that is [durationMs] long (0 = unknown). The share watched is the
     * Shorts feedback signal: under ~2.5 s = swiped away, under 30 % = left early (both negative), 50-80 % = liked (its
     * topic gets pushed right away), 80 % or more / looped = loved.
     */
    fun onShortLeft(v: VideoItem, dwellMs: Long, durationMs: Long = 0L) {
        remember(v)
        val dur = when {
            durationMs > 0 -> durationMs
            v.durationSec > 0 -> v.durationSec * 1000L
            else -> 30_000L
        }
        val frac = dwellMs.toDouble() / dur
        val w = words(v.title).take(8)
        when {
            dwellMs < SWIPE_AWAY_MS -> {                           // swiped away: negative
                bump(v, -0.6, -0.4)
                synchronized(lock) { w.forEach { sessionTopic[it] = (sessionTopic[it] ?: 0.0) - 1.0 } }
                lastEngagedWords = emptySet()
            }
            frac >= 0.5 -> {                                        // watched half or more: positive
                val strong = frac >= 0.8
                val loops = dwellMs >= (dur * 1.3).toLong()
                bump(v, if (loops) 1.5 else if (strong) 1.0 else 0.8, if (loops) 1.2 else if (strong) 1.0 else 0.9)
                synchronized(lock) {
                    w.forEach { sessionTopic[it] = (sessionTopic[it] ?: 0.0) + (if (strong) 1.3 else 1.0) }
                    // its topic is pulled into the next batch right away (no need for two in a row)
                    pendingInject = w.maxByOrNull { (topic[it] ?: 0.0) + (sessionTopic[it] ?: 0.0) }
                    lastEngagedWords = w.toSet()
                }
            }
            frac < 0.3 -> {                                         // left early: a little negative
                bump(v, -0.25, -0.15)
                lastEngagedWords = emptySet()
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
                            items.filter { !it.short }.take(PER_CHANNEL_POOL).forEach {
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

    private suspend fun fetchShortsBatch(n: Int = 8, from: List<ChannelItem>? = null): Int = shortsLock.withLock {
        if (Playback.isBlocked()) return 0
        val batch = synchronized(lock) { nextChannels(chanShortsFetchedAt, n, from ?: shortChannels()) }
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
        val session = if (sessionBoost) words(v.title).sumOf { sessionTopic[it] ?: 0.0 }.coerceIn(-3.0, 4.0) * 0.8 else 0.0
        val inject = if (c.src == Src.INJECT) 1.5 else 0.0
        val graph = tanh((seedScore[v.id] ?: 0.0) / 1.5) * W_SEED
        return fresh + chA + toA + explore + session + inject + graph - fatigue
    }

    /** Picks [size] items from the queues following [rhythm] (queue numbers), with at most 2 per channel. */
    private fun mixN(lists: List<List<Cand>>, rhythm: List<Int>, size: Int, maxPerChannel: Int = MAX_PER_CHANNEL_PER_PAGE): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        val used = HashSet<String>()
        val perChannel = HashMap<String, Int>()
        val queues = lists.map { ArrayDeque(it) }
        fun take(q: ArrayDeque<Cand>): Boolean {
            while (q.isNotEmpty()) {
                val c = q.removeFirst()
                if (c.v.id in used) continue
                val key = chKey(c.v)
                if (key.isNotEmpty() && (perChannel[key] ?: 0) >= maxPerChannel) continue
                used.add(c.v.id)
                if (key.isNotEmpty()) perChannel[key] = (perChannel[key] ?: 0) + 1
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

    /** Which channel a video belongs to (the name when the id is missing, as for many Shorts); empty = unknown. */
    private fun chKey(v: VideoItem): String = v.channelId.ifEmpty { v.channelTitle }

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
        if (deep) { seedHidden = false; seeding = true; seedStage = "Reading your watch history…"; seedProgress = 0.05f }
        try {
        val hist = mutableListOf<VideoItem>()
        runCatching {
            var t: String? = null
            var n = 0
            do {
                val p = Api.accountHistory(t)
                hist += p.items
                t = p.next
                n++
                if (deep) seedProgress = 0.05f + 0.6f * n / 12f
            } while (t != null && n < (if (deep) 12 else 3))
        }
        if (deep) { seedStage = "Reading your liked videos…"; seedProgress = 0.7f }
        val liked = runCatching { Api.playlistVideos("LL", null).items }.getOrDefault(emptyList())
        if (hist.isEmpty() && liked.isEmpty()) return@withLock
        if (deep) { seedStage = "Finding the topics and channels you love…"; seedProgress = 0.85f }
        synchronized(lock) {
            // newer history counts more: the first items are the most recent
            val h = hist.take(400)
            h.forEachIndexed { i, v ->
                val w = 1.0 - i / (h.size * 1.3)
                if (v.channelId.isNotEmpty()) {
                    chan[v.channelId] = (chan[v.channelId] ?: 0.0) + 0.15 * w
                    if (v.channelId !in chanInfo) chanInfo[v.channelId] = ChannelItem(id = v.channelId, title = v.channelTitle, thumb = v.channelThumb)
                }
                words(v.title).take(8).forEach { topic[it] = (topic[it] ?: 0.0) + 0.4 * w }
            }
            liked.take(60).forEach { v ->
                if (v.channelId.isNotEmpty()) {
                    chan[v.channelId] = (chan[v.channelId] ?: 0.0) + 0.45
                    if (v.channelId !in chanInfo) chanInfo[v.channelId] = ChannelItem(id = v.channelId, title = v.channelTitle, thumb = v.channelThumb)
                }
                words(v.title).take(8).forEach { topic[it] = (topic[it] ?: 0.0) + 1.2 }
            }
            // the newest watched / liked long videos are seeds of the content graph (oldest first, so the newest ends last)
            liked.filter { !it.short }.take(15).reversed().forEach { addSeed(it.id, 1.5) }
            hist.filter { !it.short }.take(25).reversed().forEach { addSeed(it.id, 0.5) }
            (hist + liked).forEach { watched.add(it.id) }
            seededAt = now
        }
        save()
        if (deep) {
            seedStage = "Your feed is ready"
            seedProgress = 1f
            kotlinx.coroutines.delay(900)
        }
        } finally {
            if (deep) seeding = false
        }
    }

    /** Channels whose Shorts we pull for the main feed: the ones the history says you like and are NOT subscribed to. */
    private fun shortChannels(): List<ChannelItem> = synchronized(lock) {
        val subIds = subs.mapTo(HashSet()) { it.id }
        chanInfo.values.filter { (chan[it.id] ?: 0.0) > 0.4 && it.title.isNotEmpty() && it.id !in subIds }
            .sortedByDescending { chan[it.id] ?: 0.0 }.take(40)
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

    /** No seeds yet (first run of this version): start from what was watched lately, in the account and in this app. */
    private suspend fun ensureSeeds() {
        if (synchronized(lock) { seeds.isNotEmpty() }) return
        val account = if (Session.loggedIn && !Playback.isBlocked()) runCatching { Api.accountHistory(null).items }.getOrDefault(emptyList()) else emptyList()
        val local = runCatching { Graph.store.history }.getOrDefault(emptyList())
        synchronized(lock) {
            local.filter { !it.short }.take(15).reversed().forEach { addSeed(it.id, 0.7) }
            account.filter { !it.short }.take(25).reversed().forEach { addSeed(it.id, 0.5) }
        }
        save()
    }

    /** Asks YouTube "what is related to this?" for a few seeds (weighted by strength and recency) and pools the answers. */
    private suspend fun fetchFromSeeds(n: Int = 4) = relatedLock.withLock {
        if (Playback.isBlocked()) return@withLock
        val now = System.currentTimeMillis()
        val pick = synchronized(lock) {
            val list = seeds.entries.toList()
            list.mapIndexed { i, e -> e.key to e.value * (0.4 + (i + 1.0) / list.size) * Random.nextDouble() }
                .filter { now - (seedFetchedAt[it.first] ?: 0L) > SEED_TTL_MS }
                .sortedByDescending { it.second }.take(n).map { it.first }
        }
        if (pick.isEmpty()) return@withLock
        val gate = Semaphore(3)
        coroutineScope {
            pick.map { id ->
                async(Dispatchers.Default) {
                    gate.withPermit {
                        val rel = runCatching { Api.relatedOf(id) }.getOrNull()
                        synchronized(lock) {
                            seedFetchedAt[id] = System.currentTimeMillis()
                            if (rel != null) {
                                val w = seeds[id] ?: 0.5
                                rel.forEachIndexed { rank, v ->
                                    if (v.id in watched) return@forEachIndexed
                                    meta[v.id] = v
                                    // earlier in YouTube's list = closer to the seed
                                    seedScore[v.id] = (seedScore[v.id] ?: 0.0) + w * (1.0 - rank / (rel.size + 5.0))
                                    if (v.id !in pool) pool[v.id] = Cand(v, Src.SEED)
                                }
                                while (seedScore.size > 1500) seedScore.remove(seedScore.keys.first())
                                trimPool()
                            }
                        }
                    }
                }
            }.awaitAll()
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
        // content graph: what YouTube relates to the videos you really watched (more of it when the supply runs low)
        val seedLeft = synchronized(lock) { pool.values.count { (seedScore[it.v.id] ?: 0.0) > 0.0 && it.v.id !in homeServed } }
        if (first) runCatching { ensureSeeds() }
        if (first) runCatching { fetchFromSeeds(5) } else if (seedLeft < HOME_PAGE) runCatching { fetchFromSeeds(3) }
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
        val recent = recentHomeChannels.toSet()
        // stable sort: channels shown in the last pages go to the back (still used if nothing else is left)
        val ranked = pool.values.filter { it.v.id !in watched && it.v.id !in history && it.v.id !in homeServed }
            .map { it to score(it, now, false) }.sortedByDescending { it.second }.map { it.first }
            .sortedBy { chKey(it.v).let { k -> k.isNotEmpty() && k in recent } }
        // Content first: what you get is chosen by what the video is about (topics you watch), whoever made it.
        fun likesContent(c: Cand): Boolean = words(c.v.title).any { (topic[it] ?: 0.0) > 0.8 }
        // first what YouTube relates to your watched videos (content graph), then title-word matches as a fallback
        val graphMatch = ranked.filter { (seedScore[it.v.id] ?: 0.0) > 0.0 }
        val graphIds = graphMatch.mapTo(HashSet()) { it.v.id }
        val match = graphMatch + ranked.filter { it.v.id !in graphIds && likesContent(it) }
        val matchIds = match.mapTo(HashSet()) { it.v.id }
        val rest = ranked.filter { it.v.id !in matchIds }
        val disc = rest.filter { it.src == Src.DISCOVER }
        val adj = rest.filter { it.src == Src.RELATED }
        val newSubs = rest.filter { it.src == Src.SUB }
        // 4 of 10 match your topics (from any source), 3 are YouTube's own picks, 2 "up next" style, 1 new upload of a
        // subscription. Before any history exists the subscriptions' freshness ranking leads.
        val known = if (match.isEmpty()) newSubs else match
        val other = if (match.isEmpty()) emptyList() else newSubs.shuffled().take(30)
        val rhythm = listOf(0, 1, 0, 2, 1, 0, 3, 0, 1, 2)
        // one video per channel on a page; only if that leaves a very short page, allow two
        var page = mixN(listOf(known, disc, adj, other), rhythm, HOME_PAGE, maxPerChannel = 1)
        if (page.size < HOME_PAGE / 2) page = mixN(listOf(known, disc, adj, other), rhythm, HOME_PAGE, maxPerChannel = 2)
        page.forEach {
            homeServed.add(it.id)
            impressions[it.id] = (impressions[it.id] ?: 0) + 1
            chKey(it).takeIf { k -> k.isNotEmpty() }?.let { k -> recentHomeChannels.addLast(k) }
        }
        while (recentHomeChannels.size > 25) recentHomeChannels.removeFirst()
        page
    }.also { save() }

    // ------------------------------------------------------------------ Shorts
    /** True when this video is in the YouTube account's history (read at sign-in) or was opened here. */
    fun isWatched(id: String): Boolean = synchronized(lock) { id in watched }

    /**
     * Null when there is nothing to recommend from: the caller then uses YouTube's own Shorts feed.
     * Shorts of subscribed channels are only about 1 in [SUB_EVERY]; everything else is discovery (YouTube's own picks,
     * topics you like, channels you watch but are not subscribed to), pushed harder for what you watch 50 % or more of.
     */
    suspend fun shortsPage(token: String?): Page<VideoItem>? {
        decayIfNewDay()
        subscriptions()
        if (token == null) runCatching { seedFromAccount() }
        // with neither subscriptions nor any taste data there is nothing to go on: YouTube's own feed
        if (subs.isEmpty() && synchronized(lock) { chan.isEmpty() && topic.isEmpty() }) return null
        val first = token == null
        val subIds = subs.mapTo(HashSet()) { it.id }
        // Shorts that are NOT from a subscribed channel / that are
        val unserved = { synchronized(lock) { shortPool.values.count { it.v.id !in servedShorts && it.v.channelId !in subIds } } }
        val unservedSub = { synchronized(lock) { shortPool.values.count { it.v.id !in servedShorts && it.v.channelId in subIds } } }
        if (first && unserved() < SHORTS_PAGE) fetchShortsBatch(6)
        // real-time adaptation: a topic got hot in this session -> pull 3-6 Shorts of it right now
        val hot = synchronized(lock) { pendingInject.also { pendingInject = null } }
        if (hot != null && System.currentTimeMillis() - lastInjectAt > 20_000L && !Playback.isBlocked()) {
            lastInjectAt = System.currentTimeMillis()
            runCatching { Api.shorts(null, hot) }.getOrNull()?.items?.take(6)?.let { found ->
                synchronized(lock) {
                    found.forEach { if (it.id !in servedShorts) { meta[it.id] = it; shortPool[it.id] = Cand(it.copy(short = true), Src.INJECT) } }
                }
            }
        }
        // Shorts of the topics you like (searched), and of the channels you watch most
        if (first || unserved() < SHORTS_PAGE * 2) runCatching { fetchShortTopics() }
        var tries = 0
        while (unserved() < SHORTS_PAGE && tries++ < 3 && fetchShortsBatch(8) > 0) { /* keep filling */ }
        // YouTube's own Shorts recommendations: the biggest part of the mix
        if (first) ytShortsToken = null
        var d = 0
        while (!Playback.isBlocked() && d < 3 && ((d == 0 && first) || unserved() < SHORTS_PAGE * 2)) {
            d++
            val p = runCatching { Api.youtubeShorts(ytShortsToken) }.getOrNull() ?: break
            ytShortsToken = p.next
            synchronized(lock) {
                p.items.forEach { if (it.id !in servedShorts && it.id !in shortPool) { meta[it.id] = it; shortPool[it.id] = Cand(it.copy(short = true), Src.DISCOVER) } }
            }
            if (p.next == null) break
        }
        // the rare Short of a subscribed channel: load a couple of them only when one is almost due
        if (shortsSinceSub >= SUB_EVERY - 8 && unservedSub() == 0) runCatching { fetchShortsBatch(2, subs) }
        val page = synchronized(lock) {
            val now = System.currentTimeMillis()
            // a Short that is in the app's history, was seen, or is in the YouTube account's history never comes back
            val hist = runCatching { Graph.store.history.mapTo(HashSet()) { it.id } }.getOrDefault(HashSet())
            val ranked = shortPool.values.filter {
                it.v.id !in servedShorts && it.v.id !in watched && it.v.id !in hist &&
                    !runCatching { Graph.store.isShortSeen(it.v.id) }.getOrDefault(false)
            }.map { it to score(it, now, true) }.sortedByDescending { it.second }.map { it.first }
            val recent = recentShortChannels.toSet()
            // stable sort: channels shown in the last batches go to the back of every queue (still usable if nothing else is left)
            val nonSub = ranked.filter { it.v.channelId !in subIds }.sortedBy { chKey(it.v) in recent }
            val subOnly = ranked.filter { it.v.channelId in subIds }
            val inj = nonSub.filter { it.src == Src.INJECT }
            val disc = nonSub.filter { it.src == Src.DISCOVER }
            // what matches your interests (channel you like, or a topic you like), from any source
            fun likes(c: Cand): Boolean = words(c.v.title).any { (topic[it] ?: 0.0) > 0.8 }
            val mine = nonSub.filter { it.src != Src.INJECT && it.src != Src.DISCOVER && likes(it) }
            val rest0 = nonSub.filter { it.src != Src.INJECT && it.src != Src.DISCOVER && !likes(it) }
            // a hot topic of this session goes first, then: 4 of 8 match your interests, 3 are YouTube's own picks, 1 explores
            val first3 = inj.distinctBy { chKey(it.v) }.take(3).map { it.v }
            val rest = mixN(
                listOf(if (mine.isEmpty()) rest0 else mine, disc, if (mine.isEmpty()) emptyList() else rest0.shuffled()),
                listOf(0, 1, 0, 1, 0, 2, 1, 0), SHORTS_PAGE - first3.size, maxPerChannel = 1
            )
            var out = (first3 + rest).distinctBy { it.id }.distinctBy { chKey(it).ifEmpty { it.id } }
            shortsSinceSub += out.size
            val subPick = subOnly.firstOrNull { c -> out.none { chKey(it) == chKey(c.v) } && chKey(c.v) !in recent }
            if (shortsSinceSub >= SUB_EVERY && subPick != null) {
                // one Short from a subscribed channel, somewhere in the middle of the batch
                out = out.toMutableList().also { it.add(minOf(it.size, 2 + Random.nextInt(4)), subPick.v) }
                shortsSinceSub = 0
            } else if (out.isEmpty()) {
                out = subOnly.take(3).map { it.v }      // nothing else to show right now: better than an empty feed
            }
            out
        }
        if (page.isEmpty()) return if (first) null else Page(emptyList(), null)
        synchronized(lock) {
            page.forEach { servedShorts.add(it.id) }
            page.forEach { v -> chKey(v).takeIf { it.isNotEmpty() }?.let { recentShortChannels.addLast(it) } }
            while (recentShortChannels.size > 10) recentShortChannels.removeFirst()
        }
        save()
        val more = synchronized(lock) {
            nextChannels(chanShortsFetchedAt, 1, shortChannels()).isNotEmpty() || shortPool.values.any { it.v.id !in servedShorts } ||
                topic.isNotEmpty() || ytShortsToken != null
        }
        return Page(page, if (more) "L:${synchronized(lock) { servedShorts.size }}" else null)
    }
}
