package com.cleantube.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Shorts tab: only Shorts of the channels you are subscribed to (the account's subscriptions when signed in, plus the
 * ones saved on this phone), and only the ones you have not watched yet. Nothing from outside the subscriptions is mixed
 * in. An empty list means "No new videos".
 *
 * A channel's Shorts tab has no upload time per Short, so the order is: every channel's newest unwatched Short first, then
 * every channel's second newest, and so on (a channel's own Shorts keep YouTube's newest-first order).
 */
object SubscribedShorts {
    private const val MAX_CHANNELS = 60
    private const val PER_CHANNEL = 6
    private const val HISTORY_PAGES = 3
    private const val HISTORY_TTL_MS = 10 * 60_000L

    /** Ids from the newest pages of the YouTube account's watch history (watched in the official app too). */
    private var accountWatched: Set<String> = emptySet()
    private var accountWatchedAt = 0L

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** The subscribed channels: the account's list (signed in) and the ones saved on this phone, without repeats. */
    private suspend fun channels(): List<ChannelItem> {
        val local = runCatching { Graph.store.subscriptions }.getOrDefault(emptyList())
        val remote = if (Session.loggedIn && !Playback.isBlocked()) attempt { Api.accountChannels() }.orEmpty() else emptyList()
        return (remote + local).filter { it.id.isNotEmpty() }.distinctBy { it.id }
    }

    private suspend fun watchedInAccount(): Set<String> {
        if (!Session.loggedIn || Playback.isBlocked()) return accountWatched
        val now = System.currentTimeMillis()
        if (now - accountWatchedAt < HISTORY_TTL_MS) return accountWatched
        val ids = HashSet<String>()
        var token: String? = null
        var pages = 0
        try {
            do {
                val page = Api.accountHistory(token)
                page.items.forEach { ids += it.id }
                token = page.next
                pages++
            } while (token != null && pages < HISTORY_PAGES)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // keep what was read; the app's own seen list still applies
        }
        if (ids.isNotEmpty()) {
            accountWatched = ids
            accountWatchedAt = now
        }
        return accountWatched
    }

    /** The unwatched Shorts of the subscribed channels, newest of every channel first. Empty = nothing new. */
    suspend fun load(): List<VideoItem> {
        val subs = channels()
        if (subs.isEmpty()) return emptyList()
        val store = Graph.store
        val watchedElsewhere = watchedInAccount()
        val answered = AtomicInteger(0)
        val gate = Semaphore(4)
        val perChannel = coroutineScope {
            subs.take(MAX_CHANNELS).map { c ->
                async(Dispatchers.Default) {
                    gate.withPermit {
                        val items = attempt { withTimeout(10_000) { Api.channelVideosTab(c.id, "shorts", null).items } }
                        if (items != null) answered.incrementAndGet()
                        (items ?: emptyList()).filter { it.id.isNotEmpty() }.map {
                            it.copy(
                                channelId = c.id,
                                channelTitle = it.channelTitle.ifEmpty { c.title },
                                channelThumb = it.channelThumb.ifEmpty { c.thumb },
                                short = true
                            )
                        }
                    }
                }
            }.awaitAll()
        }
        // not one channel answered: that is a connection problem, not "no new videos"
        if (answered.get() == 0) {
            throw ApiException(ApiException.Kind.NETWORK, "Couldn't load Shorts from your subscriptions. Check your connection and try again.")
        }
        val queues = perChannel.map { list ->
            list.filter {
                !store.isShortSeen(it.id) && !store.isInHistory(it.id) && it.id !in watchedElsewhere && !store.isHidden(it)
            }.take(PER_CHANNEL)
        }
        val out = ArrayList<VideoItem>()
        val added = HashSet<String>()
        var round = 0
        while (true) {
            var any = false
            for (queue in queues) {
                val v = queue.getOrNull(round) ?: continue
                any = true
                if (added.add(v.id)) out += v
            }
            if (!any) break
            round++
        }
        return out
    }
}
