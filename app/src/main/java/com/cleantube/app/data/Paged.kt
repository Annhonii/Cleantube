package com.cleantube.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
                    items = items + page.items
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

    fun refresh() {
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
