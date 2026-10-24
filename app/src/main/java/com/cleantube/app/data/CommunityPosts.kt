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
    private const val CHANNELS = 10
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
        loading = true
        try {
            val channels = (if (loggedIn) runCatching { Api.accountChannels() }.getOrDefault(emptyList()).ifEmpty { subs } else subs)
            if (channels.isEmpty()) {
                pool = emptyList()
                lastKey = key
                return
            }
            val gate = Semaphore(4)
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
