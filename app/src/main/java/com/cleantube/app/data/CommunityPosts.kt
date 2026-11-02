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
