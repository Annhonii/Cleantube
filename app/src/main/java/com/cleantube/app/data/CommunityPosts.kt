package com.cleantube.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/**
 * Community posts (text, picture and poll posts) of the channels you are subscribed to, for the Home feed.
 * The pool is filled in the background from the channels' Posts tabs (first page of up to [CHANNELS] channels, a couple
 * of posts each, mixed channel by channel) and Home slips them in between the videos. Fetched once per app start and
 * again when the account / subscriptions change, or on pull-to-refresh; the Home feed never waits for it.
 */
object CommunityPosts {
    private const val CHANNELS = 5
    private const val PER_CHANNEL = 3

    var pool by mutableStateOf<List<Post>>(emptyList())
        private set

    private var lastKey = ""
    private var loading = false

    fun request(force: Boolean = false) {
        TabCache.appScope.launch { refresh(force) }
    }

    suspend fun refresh(force: Boolean = false) {
        val store = Graph.store
        val loggedIn = Session.loggedIn
        val subs = store.subscriptions
        val key = if (loggedIn) "acct:${Session.handle}" else "local:" + subs.joinToString(",") { it.id }
        if (!force && key == lastKey) return
        if (loading) return
        if (Playback.isBlocked()) return   // YouTube is refusing this network: posts are a bonus, stay quiet
        loading = true
        try {
            val channels = (if (loggedIn) runCatching { Api.accountChannels() }.getOrDefault(emptyList()).ifEmpty { subs } else subs)
            if (channels.isEmpty()) {
                pool = emptyList()
                lastKey = key
                return
            }
            val gate = Semaphore(2)
            val perChannel = coroutineScope {
                channels.shuffled().take(CHANNELS).map { ch ->
                    async {
                        gate.withPermit {
                            runCatching { withTimeout(9000) { Api.channelPosts(ch.id, null).items.take(PER_CHANNEL) } }
                                .getOrDefault(emptyList())
                        }
                    }
                }.awaitAll()
            }
            // round robin over the channels, so one chatty channel doesn't fill the feed
            val mixed = ArrayList<Post>()
            for (round in 0 until PER_CHANNEL) perChannel.forEach { l -> l.getOrNull(round)?.let { mixed += it } }
            val usable = mixed.filter { it.id.isNotEmpty() && (it.text.isNotBlank() || it.image.isNotEmpty() || it.poll.isNotEmpty()) }
                .distinctBy { it.id }
            if (usable.isNotEmpty() || force) pool = usable
            lastKey = key
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // posts are a bonus: Home just stays videos only
        } finally {
            loading = false
        }
    }
}

/**
 * Posts for the New tab: the community posts of the subscribed channels, newest first by their "x days ago" age, so the
 * tab can slot them in between the videos by time. Up to [CHANNELS] channels (the ones that just uploaded first), a few
 * posts each, loaded in the background; cached for a few minutes.
 */
object FeedPosts {
    private const val CHANNELS = 30
    private const val PER_CHANNEL = 4
    private const val FRESH_MS = 10 * 60_000L

    private var key = ""
    private var time = 0L
    private var cached: List<Post> = emptyList()

    fun cached(k: String): List<Post> = if (k == key) cached else emptyList()

    fun invalidate() { time = 0L }

    suspend fun load(k: String, channels: List<ChannelItem>, active: List<String>): List<Post> {
        if (k == key && System.currentTimeMillis() - time < FRESH_MS) return cached
        if (Playback.isBlocked()) return cached(k)
        val order = (active + channels.map { it.id }).distinct().filter { id -> channels.any { it.id == id } }.take(CHANNELS)
        val gate = Semaphore(4)
        val lists = coroutineScope {
            order.map { id ->
                async {
                    gate.withPermit {
                        runCatching { withTimeout(8000) { Api.channelPosts(id, null).items.take(PER_CHANNEL) } }
                            .getOrDefault(emptyList())
                    }
                }
            }.awaitAll()
        }
        val posts = lists.flatten()
            .filter { it.id.isNotEmpty() && (it.text.isNotBlank() || it.image.isNotEmpty() || it.poll.isNotEmpty()) }
            .distinctBy { it.id }
            .filter { Api.ageMinutes(it.age) != Long.MAX_VALUE }
            .sortedBy { Api.ageMinutes(it.age) }
        key = k
        time = System.currentTimeMillis()
        cached = posts
        return posts
    }
}
