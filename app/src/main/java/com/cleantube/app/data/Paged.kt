package com.cleantube.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.launch

/** App-wide singletons (kept tiny on purpose: this app has no DI framework). */
object Graph {
    lateinit var store: Store
        private set

    fun init(context: Context) {
        if (!::store.isInitialized) store = Store(context.applicationContext)
    }
}

/**
 * Observable, page-by-page list. Compose reads [items], [loading], [error] and [finished];
 * call [loadMore] when the user scrolls near the end and [refresh] to start over.
 */
class Paged<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (String?) -> Page<T>
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<ApiException?>(null)
        private set
    var finished by mutableStateOf(false)
        private set
    /** True while a pull-to-refresh is running (the old list stays visible until the new one arrives). */
    var refreshing by mutableStateOf(false)
        private set

    /** Kept here so scroll position and "already animated" rows survive leaving and re-entering the screen. */
    val listState = LazyListState()
    val seen = HashSet<Any>()

    private var next: String? = null
    private var generation = 0
    private var job: Job? = null

    fun loadMore() {
        if (loading || finished) return
        loading = true
        error = null
        val gen = generation
        job = scope.launch {
            try {
                val page = fetch(next)
                if (gen == generation) {
                    items = dedupe(items + page.items)
                    next = page.next
                    if (page.next == null) finished = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (gen == generation) error = e
            } catch (e: Exception) {
                if (gen == generation) error = ApiException(ApiException.Kind.OTHER, e.message ?: "Something went wrong.")
            } finally {
                if (gen == generation) loading = false
            }
        }
    }

    /** Same video twice in one list (YouTube repeats videos between pages): keep the first one. */
    private fun dedupe(list: List<T>): List<T> =
        list.distinctBy { if (it is VideoItem) it.id else it }

    /** Takes items out of the list right away (e.g. "Not interested"). */
    fun removeWhere(pred: (T) -> Boolean) {
        items = items.filterNot(pred)
    }

    /** Puts one item at the top of the list (e.g. a comment that was just posted). */
    fun prepend(item: T) {
        items = listOf(item) + items
    }

    /** Pull-to-refresh: fetch the first page and swap it in; on failure the old list stays. */
    fun pullRefresh() {
        if (refreshing) return
        generation++
        job?.cancel()
        loading = false
        refreshing = true
        val gen = generation
        job = scope.launch {
            try {
                val page = fetch(null)
                if (gen == generation) {
                    items = dedupe(page.items)
                    next = page.next
                    finished = page.next == null
                    error = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation && items.isEmpty()) {
                    error = (e as? ApiException) ?: ApiException(ApiException.Kind.OTHER, e.message ?: "Something went wrong.")
                }
            } finally {
                if (gen == generation) refreshing = false
            }
        }
    }

    fun refresh() {
        refreshing = false
        generation++
        job?.cancel()
        items = emptyList()
        next = null
        finished = false
        loading = false
        error = null
        loadMore()
    }
}

/**
 * Lists of the tab screens live here (not inside the screens), so switching tabs or coming back from a video
 * shows what was already loaded instead of fetching it again. Pull down to refresh.
 */
object TabCache {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val map = HashMap<String, Any>()
    val shortsPage = HashMap<String, Int>()

    @Suppress("UNCHECKED_CAST")
    fun <T> paged(key: String, fetch: suspend (String?) -> Page<T>): Paged<T> =
        map.getOrPut(key) { Paged(appScope, fetch) } as Paged<T>

    fun clear() {
        map.clear()
        shortsPage.clear()
    }
}
