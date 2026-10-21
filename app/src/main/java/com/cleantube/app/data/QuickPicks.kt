package com.cleantube.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/**
 * Home's "Quick picks": the newest videos of the channels you are subscribed to that you have not watched yet.
 *
 *  - Signed in: YouTube's own subscriptions feed (first pages). Not signed in: the locally saved subscriptions.
 *  - The raw pool (newest first) is cached on the phone, so Home shows it instantly on the next start.
 *  - [refresh] runs when the app is opened cleanly (new process, e.g. after removing it from recents), on
 *    pull-to-refresh, and when the account / subscriptions change. Not on a timer, not on plain resume. Videos that were not in the previous pool are flagged
 *    in [freshIds] (the UI shows a "NEW" badge on them).
 *  - "Unwatched" is worked out from the watch history ([unwatched]), so a video disappears the moment you open it.
 */
object QuickPicks {
    /** How many cards the carousel shows at most. */
    const val MAX_CARDS = 16
    private const val POOL = 48

    /** Raw pool, newest first (watched videos are still in it; see [unwatched]). */
    var pool by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    /** Ids of videos that showed up since the previous refresh (a subscribed channel uploaded them). */
    var freshIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** True in a freshly started process (app opened cleanly): the first [refresh] goes to the network. */
    @Volatile
    private var forceNext = true

    private var loadedFromDisk = false
    private var lastKey = ""

    /** Fills the pool from the phone cache once (instant first paint). */
    fun restore() {
        if (loadedFromDisk) return
        loadedFromDisk = true
        if (pool.isEmpty()) pool = Graph.store.loadQuickPicks()
    }

    /** The cards to show: not a Short, not live-only noise, not in the watch history, at most [MAX_CARDS]. */
    fun unwatched(pool: List<VideoItem>, history: List<VideoItem>): List<VideoItem> {
        val seen = history.mapTo(HashSet()) { it.id }
        return pool.asSequence()
            .filter { !it.short && it.id !in seen }
            .distinctBy { it.id }
            .take(MAX_CARDS)
            .toList()
    }

    private fun keyOf(loggedIn: Boolean, handle: String, subs: List<ChannelItem>): String =
        if (loggedIn) "acct:$handle" else "local:" + subs.joinToString(",") { it.id }

    /**
     * Fetches the newest videos again, but only for a clean app start, [force] (pull-to-refresh) or when the
     * account / subscriptions changed; any other call does nothing.
     */
    suspend fun refresh(force: Boolean = false) {
        restore()
        val store = Graph.store
        val loggedIn = Session.loggedIn
        val subs = store.subscriptions
        val key = keyOf(loggedIn, Session.handle, subs)
        val changed = key != lastKey
        if (loading) return
        if (!force && !forceNext && !changed) return
        if (!loggedIn && subs.isEmpty()) {
            lastKey = key
            pool = emptyList()
            freshIds = emptySet()
            store.saveQuickPicks(emptyList())
            return
        }

        forceNext = false
        loading = true
        try {
            val fetched: List<VideoItem> =
                if (loggedIn) {
                    val first = Api.accountFeed(null)
                    val more = first.next?.let { runCatching { Api.accountFeed(it).items }.getOrDefault(emptyList()) }.orEmpty()
                    (first.items + more).filter { !it.short }.sortedBy { Api.ageMinutes(it.ageText) }
                } else {
                    Api.localFeed(subs)
                }.distinctBy { it.id }.take(POOL)

            if (fetched.isEmpty()) {
                // an empty answer is most likely a hiccup: keep what we had
                forceNext = true
                lastKey = key
                return
            }
            val before = pool.mapTo(HashSet()) { it.id }
            // after an account / subscription switch every video is "new" to this pool, so no badges then
            freshIds = if (before.isEmpty() || changed && lastKey.isNotEmpty()) emptySet()
            else fetched.filter { it.id !in before }.mapTo(LinkedHashSet()) { it.id }
            pool = fetched
            store.saveQuickPicks(fetched)
            lastKey = key
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // keep the old pool and try again the next time Home asks
            forceNext = true
        } finally {
            loading = false
        }
    }
}
