package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.cleantube.app.data.Api
import com.cleantube.app.data.ApiException
import com.cleantube.app.data.PlaylistItem
import com.cleantube.app.data.Session
import com.cleantube.app.data.TabCache
import kotlinx.coroutines.CancellationException
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
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Paged
import com.cleantube.app.data.VideoItem
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
    val paged = TabCache.paged("feed:${Session.handle}") { token -> Api.accountFeed(token) }

    // channel logos on top (YouTube account's subscriptions; falls back to the locally saved ones)
    var accountChannels by remember { mutableStateOf(AccountChannelsCache.list) }
    LaunchedEffect(Unit) {
        if (!AccountChannelsCache.valid()) {
            try {
                val list = Api.accountChannels()
                if (list.isNotEmpty()) {
                    AccountChannelsCache.put(list)
                    accountChannels = list
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // no logos then; the feed itself still works
            }
        }
    }
    val stripChannels = accountChannels.ifEmpty { Graph.store.subscriptions }

    Column(Modifier.fillMaxSize()) {
        TabTopBar("Feed", actions = {
            IconButton(onClick = { paged.refresh() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = TextPrimary)
            }
        })
        PagedColumn(
            paged,
            emptyText = "Subscribe to channels and their newest videos show up here.",
            header = { item { SubscriptionStrip(stripChannels, paged.items) } }
        ) { v ->
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
            else -> PullRefresh(refreshing = loading, onRefresh = { load() }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                    item { SubscriptionStrip(subs, list) }
                    items(list.size) { i ->
                        val v = list[i]
                        VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- Library

@Composable
private fun LibEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF4A5D8E)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(subtitle, color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
    }
}

@Composable
private fun LibSection(text: String) {
    Text(
        text, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun LibraryScreen() {
    val nav = LocalNav.current
    val store = Graph.store

    Column(Modifier.fillMaxSize()) {
        TabTopBar("Library")
        val subs = store.subscriptions.sortedBy { it.title.lowercase() }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
            item {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibEntry(Icons.Rounded.Download, "Downloads", "Videos saved on this phone") { nav.push(Route.Downloads) }
                    LibEntry(
                        Icons.Rounded.History, "History",
                        if (Session.loggedIn) "Your YouTube history: videos and Shorts" else "Videos and Shorts you watched here"
                    ) { nav.push(Route.History) }
                    LibEntry(
                        Icons.AutoMirrored.Rounded.PlaylistPlay, "Playlists",
                        if (Session.loggedIn) "Your playlists and liked videos" else "Playlists on this phone"
                    ) { nav.push(Route.Playlists) }
                }
            }
            item { LibSection("SUBSCRIPTIONS") }
            if (subs.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState("No subscriptions yet. Open a channel and tap Subscribe, or import your NewPipe subscriptions.")
                        Pill("Import / backup", onClick = { nav.push(Route.Settings) }, active = true)
                    }
                }
            } else {
                items(subs.size) { i ->
                    val c = subs[i]
                    ChannelRow(c, onClick = { nav.push(Route.Channel(c.id, c)) })
                }
            }
        }
    }
}

// ----------------------------------------------------------------- History

/** Watch history, split into Videos and Shorts. Signed in = your YouTube account's history, otherwise this app's own. */
@Composable
fun HistoryScreen() {
    val nav = LocalNav.current
    val store = Graph.store
    val loggedIn = Session.loggedIn
    var tab by remember { mutableIntStateOf(0) }   // 0 = Videos, 1 = Shorts
    var confirm by remember { mutableStateOf(false) }
    val paged = TabCache.paged("history:${Session.handle}") { token -> Api.accountHistory(token) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("History", onBack = { nav.back() }, actions = {
            if (loggedIn) {
                IconButton(onClick = { paged.refresh() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                }
            } else if (store.history.isNotEmpty()) {
                IconButton(onClick = { confirm = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Clear history", tint = TextPrimary)
                }
            }
        })
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pill("Videos", onClick = { tab = 0 }, active = tab == 0)
            Pill("Shorts", onClick = { tab = 1 }, active = tab == 1)
        }
        val wantShort = tab == 1
        val emptyText = if (wantShort) "No Shorts in your history yet." else "No videos in your history yet."

        if (loggedIn) {
            val state = paged.listState
            LaunchedEffect(paged) { if (paged.items.isEmpty() && !paged.loading) paged.loadMore() }
            LoadMoreEffect(state, paged)
            val list = paged.items.filter { it.short == wantShort }
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = listPadding) {
                items(list.size) { i ->
                    val v = list[i]
                    VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
                }
                item {
                    val err = paged.error
                    when {
                        err != null -> ErrorBox(err, onRetry = { paged.loadMore() })
                        paged.loading -> Loading()
                        list.isEmpty() && paged.finished -> EmptyState(emptyText)
                    }
                }
            }
        } else {
            val list = store.history.filter { it.short == wantShort }
            if (list.isEmpty()) {
                EmptyState(emptyText)
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                    items(list.size) { i ->
                        val v = list[i]
                        VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) }, onRemove = { store.removeHistory(v.id) })
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Card,
            title = { Text("Clear watch history?") },
            confirmButton = {
                TextButton(onClick = { confirm = false; store.clearHistory() }) { Text("Clear", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel", color = TextPrimary) }
            }
        )
    }
}

// --------------------------------------------------------------- Playlists

/** Signed in: Liked videos + the playlists of your YouTube account. Always: the playlists kept on this phone. */
@Composable
fun PlaylistsScreen() {
    val nav = LocalNav.current
    val store = Graph.store
    val loggedIn = Session.loggedIn
    var account by remember { mutableStateOf<List<PlaylistItem>?>(null) }
    var error by remember { mutableStateOf<ApiException?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(loggedIn, reload) {
        if (!loggedIn) return@LaunchedEffect
        error = null
        try {
            account = Api.accountPlaylists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            error = e
        } catch (e: Exception) {
            error = ApiException(ApiException.Kind.OTHER, e.message ?: "Something went wrong.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Playlists", onBack = { nav.back() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
            if (loggedIn) {
                item {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        LibEntry(Icons.Rounded.ThumbUp, "Liked videos", "Videos you liked") {
                            nav.push(Route.Playlist("LL", "Liked videos"))
                        }
                    }
                }
                val acc = account?.filter { it.id != "LL" }
                val err = error
                when {
                    acc != null -> {
                        item { LibSection("YOUR PLAYLISTS") }
                        if (acc.isEmpty()) item { EmptyState("No playlists on your account.") }
                        items(acc.size) { i ->
                            val p = acc[i]
                            PlaylistRow(p, onClick = { nav.push(Route.Playlist(p.id, p.title)) })
                        }
                    }
                    err != null -> item { ErrorBox(err, onRetry = { reload++ }) }
                    else -> item { Loading() }
                }
            } else {
                item {
                    EmptyState("Sign in from Settings to see your YouTube playlists and liked videos.")
                }
            }

            item { LibSection("ON THIS PHONE") }
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
            val lists = store.playlists
            items(lists.size) { i ->
                val p = lists[i]
                Column(
                    Modifier
                        .bouncyClickable(pressedScale = 0.975f) { nav.push(Route.LocalList(p.id)) }
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Card)
                        .padding(16.dp)
                ) {
                    Text(p.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.videos.size} videos", color = TextMuted, fontSize = 12.sp)
                }
            }
            if (lists.isEmpty()) item { EmptyState("No playlists yet.") }
        }
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
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel", color = TextPrimary) }
            }
        )
    }
}
