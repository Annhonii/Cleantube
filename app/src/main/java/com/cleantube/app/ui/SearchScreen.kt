package com.cleantube.app.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Paged
import com.cleantube.app.data.PlaylistItem
import com.cleantube.app.data.VideoItem
import com.cleantube.app.data.Api

@Composable
fun SearchScreen(route: Route.Search) {
    val nav = LocalNav.current
    val store = Graph.store
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    var text by remember { mutableStateOf(route.query) }
    var submitted by remember { mutableStateOf(route.query) }
    var type by remember { mutableStateOf("video") }

    LaunchedEffect(Unit) {
        if (route.query.isEmpty()) focus.requestFocus()
    }

    fun submit(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        store.addSearch(t)
        text = t
        submitted = t
        keyboard?.hide()
        focusManager.clearFocus()
    }

    // While the text differs from the last submitted query we show suggestions (= your search history).
    val showSuggestions = submitted.isEmpty() || text != submitted

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Card).border(1.dp, Hairline, CircleShape)
                    .bouncyClickable(pressedScale = 0.88f, onClick = { nav.back() }),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            PillTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "Search YouTube",
                modifier = Modifier.weight(1f),
                trailing = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = "" }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(text) }),
                focusRequester = focus
            )
            Spacer(Modifier.width(10.dp))
        }

        if (showSuggestions) {
            val q = text.trim()
            val matches = if (q.isEmpty()) store.searches
            else store.searches.filter { it.contains(q, ignoreCase = true) }

            LazyColumn(Modifier.fillMaxSize()) {
                items(matches.size) { i ->
                    val s = matches[i]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { submit(s) }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            s,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { store.removeSearch(s) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (text.isNotBlank()) {
                    item {
                        Text(
                            "Search for \"${text.trim()}\"",
                            color = Accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { submit(text) }
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        )
                    }
                }
                if (text.isEmpty() && store.searches.isNotEmpty()) {
                    item {
                        Text(
                            "Clear search history",
                            color = TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { store.clearSearches() }
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Pill("Videos", onClick = { type = "video" }, active = type == "video")
                Pill("Channels", onClick = { type = "channel" }, active = type == "channel")
                Pill("Playlists", onClick = { type = "playlist" }, active = type == "playlist")
            }

            val paged = remember(submitted, type) {
                Paged<Any>(scope) { token ->
                    when (type) {
                        "channel" -> Api.searchChannels(submitted, token)
                        "playlist" -> Api.searchPlaylists(submitted, token)
                        else -> Api.searchVideos(submitted, token)
                    }
                }
            }
            PagedColumn(paged, emptyText = "No results for \"$submitted\"") { item ->
                when (item) {
                    is VideoItem -> VideoRow(item, onClick = { if (item.short) openInShorts(nav, item) else nav.push(Route.Video(item.id, item)) })
                    is ChannelItem -> ChannelRow(item, onClick = { nav.push(Route.Channel(item.id, item)) })
                    is PlaylistItem -> PlaylistRow(item, onClick = { nav.push(Route.Playlist(item.id, item.title)) })
                    else -> {}
                }
            }
        }
    }
}
