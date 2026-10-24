package com.cleantube.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the stream links the native player plays, and remembers them so the second request for the same
 * video (retry, quality change, Shorts swiped back) is instant. [prefetch] resolves a video in the background
 * before the user gets to it (used by Shorts for the next few clips).
 */
object Playback {
    private class Entry(val info: StreamInfo, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val inflight = HashMap<String, Deferred<StreamInfo>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // googlevideo links live for hours; stay well inside that
    private const val TTL_MS = 90L * 60 * 1000
    private const val MAX_ENTRIES = 60

    // After YouTube refuses this network, background prefetching pauses so it doesn't keep hammering the IP.
    @Volatile private var blockedUntil = 0L
    // Each new refusal in a row doubles the pause (3 -> 6 -> 12 -> 24 -> 30 min); a success resets it.
    private const val BLOCK_PAUSE_MS = 3L * 60 * 1000
    private const val BLOCK_PAUSE_MAX_MS = 30L * 60 * 1000
    @Volatile private var blockStrikes = 0
    // Prefetches run one at a time, with a gap between them.
    private val prefetchGate = Semaphore(1)

    /** True while YouTube is refusing this network. Background work (prefetch, extra feeds, metadata) must stay quiet. */
    fun isBlocked(): Boolean = System.currentTimeMillis() < blockedUntil

    private fun noteBlock(e: Throwable) {
        if (Streams.isBlock(e.message)) {
            val strikes = blockStrikes + 1
            blockStrikes = strikes
            val pause = (BLOCK_PAUSE_MS shl (strikes - 1).coerceAtMost(6)).coerceAtMost(BLOCK_PAUSE_MAX_MS)
            blockedUntil = System.currentTimeMillis() + pause
        }
    }

    fun cached(id: String): StreamInfo? {
        val e = cache[id] ?: return null
        return if (System.currentTimeMillis() - e.at < TTL_MS) e.info else null
    }

    /**
     * [safe] = skip the fast path and use the NewPipe engine (used after a link failed to play).
     * The fast path is one HTTP request (~0.3 s); NewPipe loads the watch page and unscrambles links (1-3 s).
     */
    private suspend fun fetch(id: String, safe: Boolean): StreamInfo {
        if (!safe) {
            try {
                val i = Streams.fetchFast(id)
                if (ladder(i).isNotEmpty()) return i
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fall through to the NewPipe engine: it asks YouTube differently and may still get through.
            }
        }
        return Streams.fetch(id)
    }

    suspend fun resolve(id: String, force: Boolean = false, safe: Boolean = false): StreamInfo {
        if (!force) cached(id)?.let { return it }
        val key = "$id|$safe"
        val job = synchronized(inflight) {
            inflight[key] ?: scope.async { fetch(id, safe) }.also { inflight[key] = it }
        }
        try {
            val info = try {
                job.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                noteBlock(e)
                throw e
            }
            if (cache.size >= MAX_ENTRIES) {
                cache.entries.minByOrNull { it.value.at }?.let { cache.remove(it.key) }
            }
            cache[id] = Entry(info, System.currentTimeMillis())
            blockStrikes = 0
            return info
        } finally {
            if (job.isCompleted) synchronized(inflight) { if (inflight[key] === job) inflight.remove(key) }
        }
    }

    private val extrasCache = ConcurrentHashMap<String, StreamExtras>()

    /** Sound tracks and captions for the settings card (needs the NewPipe engine, so it is fetched on demand and kept). */
    suspend fun extras(id: String): StreamExtras {
        extrasCache[id]?.let { return it }
        val e = Np.extras(id)
        if (extrasCache.size > 40) extrasCache.clear()
        extrasCache[id] = e
        return e
    }

    fun prefetch(id: String) {
        if (!Graph.store.fastStart) return
        if (cached(id) != null) return
        if (System.currentTimeMillis() < blockedUntil) return
        scope.launch {
            prefetchGate.withPermit {
                if (cached(id) != null || System.currentTimeMillis() < blockedUntil) return@withPermit
                runCatching { resolve(id) }
                delay(700)
            }
        }
    }

    /** Loads the extractor and YouTube's player script once, so the first real video doesn't pay for it. */
    fun warmUp() {
        if (!Graph.store.fastStart) return
        scope.launch {
            // One light request only. (The NewPipe engine is no longer pre-loaded at start: that was a second
            // request on every app launch. It loads the first time it is really needed.)
            runCatching { resolve("jNQXAC9IVRw") }
        }
    }

    fun heightOf(o: StreamOption): Int = Regex("(\\d+)p").find(o.label)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** One playable option per height, best first (a merged picture+sound pair wins over a ready-made file of the same height). */
    fun ladder(info: StreamInfo): List<StreamOption> =
        info.options.filter { !it.audioOnly }
            .groupBy { heightOf(it) }
            .map { (_, g) -> g.firstOrNull { it.merge } ?: g.first() }
            .sortedByDescending { heightOf(it) }

    /** Best option not taller than [maxHeight]; when everything is taller, the smallest one. */
    fun pick(info: StreamInfo, maxHeight: Int): StreamOption? {
        val l = ladder(info)
        if (l.isEmpty()) return null
        return l.firstOrNull { heightOf(it) in 1..maxHeight } ?: l.last()
    }
}
