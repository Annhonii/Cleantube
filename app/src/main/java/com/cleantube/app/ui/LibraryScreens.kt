package com.cleantube.app.ui

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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
import com.cleantube.app.data.FeedPosts
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
    // the subscribed channels' posts, slotted in between the videos by time
    var postsTick by remember { mutableIntStateOf(0) }
    val feedPosts = rememberFeedPosts(stripChannels, paged.items.map { it.channelId }, postsTick)
    val entries = remember(paged.items, feedPosts, paged.finished) { mixFeedByTime(paged.items, feedPosts, paged.finished) }

    // The header floats over the list (see CollapsingHeader): the feed starts below it and collapses it while scrolling.
    val headerH = collapsingHeaderHeight(Route.Feed)
    Box(Modifier.fillMaxSize()) {
        PagedColumn(
            paged,
            emptyText = "Subscribe to channels and their newest videos show up here.",
            contentPadding = PaddingValues(top = headerH + 12.dp, bottom = listBottomPad()),
            refreshIndicatorTop = headerH,
            header = { item { SubscriptionStrip(stripChannels, paged.items) } },
            onRefresh = { FeedPosts.invalidate(); postsTick++ },
            itemCount = entries.size,
            indexRow = { i -> FeedEntryRow(entries.getOrNull(i)) { v -> nav.push(Route.Video(v.id, v)) } }
        )
        PrefetchTop(paged.items)
    }
}

@Composable
private fun LocalFeedScreen() {
    val nav = LocalNav.current
    val store = Graph.store
    val scope = rememberCoroutineScope()
    val subs = store.subscriptions
    var postsTick by remember { mutableIntStateOf(0) }

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

    val headerH = collapsingHeaderHeight(Route.Feed)
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
            else -> PullRefresh(refreshing = loading, onRefresh = { FeedPosts.invalidate(); postsTick++; load() }, modifier = Modifier.fillMaxSize(), indicatorTop = headerH) {
                val feedPosts = rememberFeedPosts(subs, list.map { it.channelId }, postsTick)
                val entries = remember(list, feedPosts) { mixFeedByTime(list, feedPosts, true) }
                LazyColumn(
                    Modifier.fillMaxSize(), state = listState,
                    contentPadding = PaddingValues(top = headerH + 12.dp, bottom = listBottomPad())
                ) {
                    item { SubscriptionStrip(subs, list) }
                    items(entries.size) { i -> FeedEntryRow(entries[i]) { v -> nav.push(Route.Video(v.id, v)) } }
                }
            }
        }
    }
}

/** The posts of the subscribed channels for the New tab (loaded in the background, cached for a few minutes). */
@Composable
private fun rememberFeedPosts(channels: List<ChannelItem>, active: List<String>, tick: Int): List<com.cleantube.app.data.Post> {
    val key = channels.joinToString(",") { it.id }
    var posts by remember(key) { mutableStateOf(FeedPosts.cached(key)) }
    LaunchedEffect(key, tick) {
        if (channels.isNotEmpty()) {
            try {
                posts = FeedPosts.load(key, channels, active)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // posts are a bonus: the New tab stays videos only
            }
        }
    }
    return posts
}

/** One row of the New tab: a video, or a channel's post. */
@Composable
private fun FeedEntryRow(entry: HomeEntry?, onOpen: (VideoItem) -> Unit) {
    when (entry) {
        is HomeEntry.Video -> VideoRow(entry.v, onClick = { onOpen(entry.v) })
        is HomeEntry.PostEntry -> HomePostCard(entry.post)
        null -> {}
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

/** One plain row of the Library list: round icon, title, arrow. No card behind it. */
@Composable
private fun LibListRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(SecondaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = OnSecondaryContainer, modifier = Modifier.size(22.dp)) }
        Text(
            label, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        )
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
    }
}

@Composable
fun LibraryScreen() {
    val nav = LocalNav.current
    val store = Graph.store

    val headerH = collapsingHeaderHeight(Route.Library)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(top = headerH + 12.dp, bottom = listBottomPad())
        ) {
            // plain list: Playlists, Liked videos, History
            item {
                Column(Modifier.fillMaxWidth()) {
                    LibListRow(Icons.AutoMirrored.Rounded.PlaylistPlay, "Playlists") { nav.push(Route.Playlists) }
                    LibListRow(Icons.Rounded.ThumbUp, if (Session.loggedIn) "Liked videos" else "Subscribed") {
                        if (Session.loggedIn) nav.push(Route.Playlist("LL", "Liked videos")) else nav.push(Route.Settings)
                    }
                    LibListRow(Icons.Rounded.History, "History") { nav.push(Route.History) }
                }
            }
        }
    }
}

// --------------------------------------------------------------- Playlists

/** Signed in: Liked videos + the playlists of your YouTube account. Always: the playlists kept on this phone. */
@Composable
fun PlaylistsScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val loggedIn = Session.loggedIn
    var account by remember { mutableStateOf<List<PlaylistItem>?>(null) }
    var error by remember { mutableStateOf<ApiException?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var downloadFor by remember { mutableStateOf<PlaylistItem?>(null) }
    var removeFor by remember { mutableStateOf<PlaylistItem?>(null) }
    var removing by remember { mutableStateOf(false) }

    // plays the whole playlist in a random order
    fun shufflePlaylist(p: PlaylistItem) {
        Toast.makeText(ctx, "Loading playlist…", Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val vids = Api.allPlaylistVideos(p.id)
                if (vids.isEmpty()) Toast.makeText(ctx, "This playlist is empty.", Toast.LENGTH_SHORT).show()
                else VideoHost.playQueue(vids.shuffled(), 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(ctx, "Couldn't load the playlist: ${e.message ?: "try again"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(loggedIn, reload) {
        if (!loggedIn) {
            account = null
            return@LaunchedEffect
        }
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
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PillTextField(
                            newName,
                            { newName = it },
                            "New YouTube playlist",
                            Modifier.weight(1f),
                            keyboardActions = KeyboardActions(onDone = {})
                        )
                        Pill(
                            if (creating) "Creating…" else "Create",
                            onClick = {
                                val title = newName.trim()
                                if (title.isEmpty() || creating) {
                                    if (title.isEmpty()) Toast.makeText(ctx, "Enter a playlist name.", Toast.LENGTH_SHORT).show()
                                } else {
                                    creating = true
                                    scope.launch {
                                        try {
                                            Api.createYouTubePlaylist(title)
                                            newName = ""
                                            account = null
                                            reload++
                                            Toast.makeText(ctx, "Playlist created on YouTube.", Toast.LENGTH_SHORT).show()
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            Toast.makeText(ctx, "Couldn't create playlist: ${e.message ?: "try again"}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            creating = false
                                        }
                                    }
                                }
                            },
                            active = true
                        )
                    }
                }
                val acc = account?.filter { it.id != "LL" }
                val err = error
                when {
                    acc != null -> {
                        item { LibSection("YOUR PLAYLISTS") }
                        if (acc.isEmpty()) item { EmptyState("No playlists on your YouTube account yet.") }
                        items(acc.size) { i ->
                            val p = acc[i]
                            PlaylistRow(
                                p,
                                onClick = { nav.push(Route.Playlist(p.id, p.title)) },
                                shape = groupedCardShape(i, acc.size),
                                onShuffle = { shufflePlaylist(p) },
                                onDownload = { downloadFor = p },
                                onRemove = { removeFor = p }
                            )
                        }
                    }
                    err != null -> item { ErrorBox(err, onRetry = { reload++ }) }
                    else -> item { Loading() }
                }
            } else {
                item {
                    EmptyState("Sign in from Settings to create and see playlists on your YouTube account.")
                }
            }
        }
    }

    downloadFor?.let { p -> PlaylistDownloadDialog(p.id, p.title) { downloadFor = null } }

    removeFor?.let { p ->
        AlertDialog(
            onDismissRequest = { if (!removing) removeFor = null },
            containerColor = Card,
            title = { Text("Remove playlist?") },
            text = { Text("\"${p.title}\" will be removed from your YouTube account. The videos in it are not deleted.") },
            confirmButton = {
                TextButton(
                    enabled = !removing,
                    onClick = {
                        removing = true
                        scope.launch {
                            try {
                                Api.deleteYouTubePlaylist(p.id)
                                account = account?.filter { it.id != p.id }
                                Toast.makeText(ctx, "Playlist removed.", Toast.LENGTH_SHORT).show()
                                removeFor = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Couldn't remove the playlist: ${e.message ?: "try again"}", Toast.LENGTH_LONG).show()
                            } finally {
                                removing = false
                            }
                        }
                    }
                ) { Text(if (removing) "Removing…" else "Remove", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { removeFor = null }, enabled = !removing) { Text("Cancel") } }
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
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel", color = TextPrimary) }
            }
        )
    }
}
