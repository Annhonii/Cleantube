package com.cleantube.app.ui

import com.cleantube.app.data.Downloads
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
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

    // The header floats over the list (see CollapsingHeader): the feed starts below it and collapses it while scrolling.
    val headerH = collapsingHeaderHeight()
    Box(Modifier.fillMaxSize()) {
        PagedColumn(
            paged,
            emptyText = "Subscribe to channels and their newest videos show up here.",
            contentPadding = PaddingValues(top = headerH + 8.dp, bottom = 120.dp),
            refreshIndicatorTop = headerH,
            header = { item { SubscriptionStrip(stripChannels, paged.items) } },
            indexRow = { i -> BentoFeedItem(paged.items, i) { v -> nav.push(Route.Video(v.id, v)) } }
        )
        TrackHeaderScroll(paged.listState, Route.Feed)
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

    val headerH = collapsingHeaderHeight()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        val list = items
        val err = error
        when {
            subs.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = headerH)) {
                EmptyState("Subscribe to channels and their newest videos show up here.\nSign in from Settings to use your real YouTube feed.")
            }
            err != null -> Box(Modifier.fillMaxSize().padding(top = headerH)) { ErrorBox(err, onRetry = { load() }) }
            list == null || (loading && list.isEmpty()) -> Box(Modifier.fillMaxSize().padding(top = headerH)) { Loading() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = headerH)) { EmptyState("No recent videos.") }
            else -> PullRefresh(refreshing = loading, onRefresh = { load() }, modifier = Modifier.fillMaxSize(), indicatorTop = headerH) {
                LazyColumn(
                    Modifier.fillMaxSize(), state = listState,
                    contentPadding = PaddingValues(top = headerH + 8.dp, bottom = 120.dp)
                ) {
                    item { SubscriptionStrip(subs, list) }
                    items(list.size) { i -> BentoFeedItem(list, i) { v -> nav.push(Route.Video(v.id, v)) } }
                }
            }
        }
        TrackHeaderScroll(listState, Route.Feed)
    }
}

// ----------------------------------------------------------------- Library

@Composable
private fun LibEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(SecondaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = OnSecondaryContainer, modifier = Modifier.size(24.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
    }
}

@Composable
private fun LibSection(text: String) {
    Text(
        text, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp)
    )
}

/** Big square tile of the Library dashboard: icon, a large number and a label. [hero] = filled mint. */
@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    count: String,
    hero: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(30.dp)
    val fg = if (hero) OnAccent else TextPrimary
    val sub = if (hero) OnAccent.copy(alpha = 0.8f) else TextMuted
    Column(
        modifier
            .aspectRatio(1.25f)
            .clip(shape)
            .background(if (hero) Accent else Card)
            .bouncyClickable(pressedScale = 0.95f, onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(if (hero) OnAccent.copy(alpha = 0.2f) else SecondaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp)) }
        Column {
            Text(label, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (count.isNotEmpty()) Text(count, color = sub, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun LibraryScreen() {
    val nav = LocalNav.current
    val store = Graph.store

    val headerH = collapsingHeaderHeight()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(top = headerH + 8.dp, bottom = 120.dp)
        ) {
            // 2 x 2 bento dashboard
            item {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            Icons.Rounded.History, "History",
                            "",
                            hero = true, onClick = { nav.push(Route.History) }, modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            Icons.Rounded.Download, "Downloads", "",
                            hero = false, onClick = { nav.push(Route.Downloads) }, modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            Icons.AutoMirrored.Rounded.PlaylistPlay, "Playlists",
                            "",
                            hero = false, onClick = { nav.push(Route.Playlists) }, modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            Icons.Rounded.ThumbUp, if (Session.loggedIn) "Liked" else "Subscribed",
                            "",
                            hero = false,
                            onClick = {
                                if (Session.loggedIn) nav.push(Route.Playlist("LL", "Liked videos")) else nav.push(Route.Settings)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        TrackHeaderScroll(listState, Route.Library)
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
