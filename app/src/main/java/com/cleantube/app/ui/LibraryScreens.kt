package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.ApiException
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Paged
import com.cleantube.app.data.Session
import com.cleantube.app.data.VideoItem
import com.cleantube.app.data.Api
import kotlinx.coroutines.launch

private val listPadding = PaddingValues(bottom = 120.dp)

// -------------------------------------------------------------------- Feed

/** Remembers the last feed so switching tabs doesn't spend API quota again. */
private object FeedCache {
    private var signature = ""
    private var time = 0L
    var items: List<VideoItem> = emptyList()
        private set

    fun valid(subs: List<ChannelItem>): Boolean =
        signature == sig(subs) && System.currentTimeMillis() - time < 15 * 60_000L

    fun put(subs: List<ChannelItem>, list: List<VideoItem>) {
        signature = sig(subs)
        time = System.currentTimeMillis()
        items = list
    }

    private fun sig(subs: List<ChannelItem>) = subs.joinToString(",") { it.id }
}

@Composable
fun FeedScreen() {
    if (Session.loggedIn) AccountFeedScreen() else LocalFeedScreen()
}

/** Your real YouTube subscriptions feed (needs the account login). */
@Composable
private fun AccountFeedScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val paged = remember { Paged(scope) { token -> Api.accountFeed(token) } }
    Column(Modifier.fillMaxSize()) {
        TabTopBar("Feed", actions = {
            IconButton(onClick = { paged.refresh() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = TextPrimary)
            }
        })
        PagedColumn(paged, emptyText = "Subscribe to channels and their newest videos show up here.") { v ->
            VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
        }
    }
}

@Composable
private fun LocalFeedScreen() {
    val nav = LocalNav.current
    val store = Graph.store
    val scope = rememberCoroutineScope()
    val subs = store.subscriptions

    var items by remember { mutableStateOf<List<VideoItem>?>(if (FeedCache.valid(subs)) FeedCache.items else null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiException?>(null) }

    fun load() {
        if (subs.isEmpty() || loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val result = Api.localFeed(subs)
                FeedCache.put(subs, result)
                items = result
            } catch (e: ApiException) {
                error = e
            } catch (e: Exception) {
                error = ApiException(ApiException.Kind.OTHER, e.message ?: "Something went wrong.")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(subs.map { it.id }) {
        if (subs.isNotEmpty() && !FeedCache.valid(subs)) load()
    }

    Column(Modifier.fillMaxSize()) {
        TabTopBar("Feed", actions = {
            IconButton(onClick = { load() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = TextPrimary)
            }
        })
        val list = items
        val err = error
        when {
            subs.isEmpty() -> EmptyState("Subscribe to channels and their newest videos show up here.\nSign in from Settings to use your real YouTube feed.")
            err != null -> ErrorBox(err, onRetry = { load() })
            list == null || (loading && list.isEmpty()) -> Loading()
            list.isEmpty() -> EmptyState("No recent videos.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                items(list.size) { i ->
                    val v = list[i]
                    VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
                }
            }
        }
    }
}

// ----------------------------------------------------------------- Library

@Composable
fun LibraryScreen() {
    val nav = LocalNav.current
    val store = Graph.store
    var tab by remember { mutableStateOf(0) }
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TabTopBar("Library")
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pill("Subscriptions", onClick = { tab = 0 }, active = tab == 0)
            Pill("Playlists", onClick = { tab = 1 }, active = tab == 1)
            Pill("Downloads", onClick = { nav.push(Route.Downloads) })
        }

        if (tab == 0) {
            val subs = store.subscriptions.sortedBy { it.title.lowercase() }
            if (subs.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("No subscriptions yet. Open a channel and tap Subscribe, or import your NewPipe subscriptions.")
                    Pill("Import / backup", onClick = { nav.push(Route.Settings) }, active = true)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                    items(subs.size) { i ->
                        val c = subs[i]
                        ChannelRow(c, onClick = { nav.push(Route.Channel(c.id, c)) })
                    }
                }
            }
        } else {
            val lists = store.playlists
            LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PillTextField(
                            newName,
                            { newName = it },
                            "New playlist",
                            Modifier.weight(1f),
                            keyboardActions = KeyboardActions(onDone = {})
                        )
                        Pill("Create", onClick = {
                            if (newName.isNotBlank()) {
                                store.createPlaylist(newName)
                                newName = ""
                            }
                        }, active = true)
                    }
                }
                items(lists.size) { i ->
                    val p = lists[i]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Card)
                            .clickable { nav.push(Route.LocalList(p.id)) }
                            .padding(16.dp)
                    ) {
                        Text(
                            p.name,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("${p.videos.size} videos", color = TextMuted, fontSize = 12.sp)
                    }
                }
                if (lists.isEmpty()) {
                    item { EmptyState("No playlists yet.") }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- History

@Composable
fun HistoryScreen() {
    val nav = LocalNav.current
    val store = Graph.store
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(false) }
    val list = store.history
    val remote = remember(Session.loggedIn) { Paged(scope) { token -> Api.accountHistory(token) } }

    Column(Modifier.fillMaxSize()) {
        TabTopBar("History", actions = {
            if (list.isNotEmpty()) {
                IconButton(onClick = { confirm = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Clear history", tint = TextPrimary)
                }
            }
        })
        if (Session.loggedIn) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("This app", onClick = { account = false }, active = !account)
                Pill("YouTube account", onClick = { account = true }, active = account)
            }
        }
        if (account && Session.loggedIn) {
            PagedColumn(remote, emptyText = "Your YouTube watch history is empty.") { v ->
                VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
            }
        } else if (list.isEmpty()) {
            EmptyState("Videos you open show up here.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                items(list.size) { i ->
                    val v = list[i]
                    VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Card,
            title = { Text("Clear watch history?") },
            text = { Text("This only removes it from this phone.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = { store.clearHistory(); confirm = false }) { Text("Clear", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel", color = TextPrimary) }
            }
        )
    }
}

// -------------------------------------------------------- Local playlists

@Composable
fun LocalListScreen(route: Route.LocalList) {
    val nav = LocalNav.current
    val store = Graph.store
    val playlist = store.playlist(route.id)
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(playlist?.name ?: "Playlist", onBack = { nav.back() }, actions = {
            if (playlist != null) {
                IconButton(onClick = { confirm = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist", tint = TextPrimary)
                }
            }
        })
        if (playlist == null || playlist.videos.isEmpty()) {
            EmptyState("This playlist is empty. Use \"Save to playlist\" on any video.")
        } else {
            val vids = playlist.videos
            LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                items(vids.size) { i ->
                    val v = vids[i]
                    VideoRow(
                        v,
                        onClick = { nav.push(Route.Video(v.id, v)) },
                        onRemove = { store.toggleInPlaylist(playlist.id, v) }
                    )
                }
            }
        }
    }

    if (confirm && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Card,
            title = { Text("Delete \"${playlist.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    store.deletePlaylist(playlist.id)
                    nav.back()
                }) { Text("Delete", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel", color = TextPrimary) }
            }
        )
    }
}
