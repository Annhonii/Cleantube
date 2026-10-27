package com.cleantube.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.Api
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Session
import com.cleantube.app.data.TabCache
import com.cleantube.app.data.VideoItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ----------------------------------------------------------------- History

/** One line of the history list: a day heading, that day's Shorts shelf, or one long video. */
private sealed interface HistRow {
    val key: String

    data class Day(val label: String, val index: Int) : HistRow {
        override val key get() = "day:$index"
    }

    data class Shelf(val shorts: List<VideoItem>, val index: Int) : HistRow {
        override val key get() = "shelf:$index"
    }

    data class Video(val v: VideoItem) : HistRow {
        override val key get() = "v:${v.id}"
    }
}

private fun startOfDay(ms: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** "Today", "Yesterday", the weekday for the rest of this week, otherwise "20 Oct" (with the year when it is another one). */
private fun dayLabel(ms: Long): String {
    if (ms <= 0L) return "Earlier"
    val now = System.currentTimeMillis()
    val days = Math.round((startOfDay(now) - startOfDay(ms)) / 86_400_000.0).toInt()
    val date = Date(ms)
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Yesterday"
        days < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
        else -> {
            val sameYear = Calendar.getInstance().get(Calendar.YEAR) == Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
            SimpleDateFormat(if (sameYear) "d MMM" else "d MMM yyyy", Locale.getDefault()).format(date)
        }
    }
}

/**
 * All the Shorts of the history as ONE shelf at the very top (newest first), then the long videos day by day
 * (heading, then that day's videos). Neighbouring entries with the same heading are one day.
 */
private fun buildRows(entries: List<Pair<String, VideoItem>>): List<HistRow> {
    val out = ArrayList<HistRow>()
    val shorts = entries.map { it.second }.filter { it.short }
    if (shorts.isNotEmpty()) out += HistRow.Shelf(shorts, 0)
    val longs = entries.filter { !it.second.short }
    var i = 0
    var n = 0
    while (i < longs.size) {
        val label = longs[i].first
        var j = i
        while (j < longs.size && longs[j].first == label) j++
        out += HistRow.Day(label, n)
        longs.subList(i, j).forEach { out += HistRow.Video(it.second) }
        n++
        i = j
    }
    return out
}

/**
 * Watch history like YouTube's: the Shorts as one shelf on top, then the videos day by day (today first, then the
 * days before). Signed in = your YouTube account's history, otherwise this app's own.
 */
@Composable
fun HistoryScreen() {
    val nav = LocalNav.current
    val store = Graph.store
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val loggedIn = Session.loggedIn
    var confirm by remember { mutableStateOf(false) }
    var topMenu by remember { mutableStateOf(false) }
    val paged = TabCache.paged("history:${Session.handle}") { token -> Api.accountHistory(token) }

    fun remove(v: VideoItem, fromAccount: Boolean) {
        store.removeHistory(v.id)
        if (fromAccount) {
            paged.removeWhere { it.id == v.id }
            if (v.removeFromHistoryToken.isNotEmpty()) {
                scope.launch { Api.sendFeedback(v.removeFromHistoryToken) }
            } else {
                Toast.makeText(ctx, "Removed here. YouTube didn't allow removing it from your account history.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("History", onBack = { nav.back() }, actions = {
            if (loggedIn) {
                IconButton(onClick = { paged.refresh() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                }
            }
            Box {
                IconButton(onClick = { topMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextPrimary)
                }
                DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (store.historyPaused) "Resume watch history" else "Pause watch history") },
                        onClick = { topMenu = false; store.historyPaused = !store.historyPaused }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear all watch history", color = Danger) },
                        onClick = { topMenu = false; confirm = true }
                    )
                }
            }
        })

        if (store.historyPaused) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Card)
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Watch history is paused. New videos and Shorts aren't saved.",
                    color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { store.historyPaused = false }) { Text("Turn on", color = Accent) }
            }
        }

        val emptyText = "Nothing in your watch history yet."
        val padding = PaddingValues(bottom = 120.dp)

        if (loggedIn) {
            val state = paged.listState
            // the list is kept between visits: fetch it again on open so what was just watched shows up
            LaunchedEffect(paged) {
                if (paged.items.isEmpty()) { if (!paged.loading) paged.loadMore() } else paged.pullRefresh()
            }
            LoadMoreEffect(state, paged)
            val remote = paged.items
            val remoteIds = remote.map { it.id }.toHashSet()
            // what was watched in this app lately, but YouTube's list doesn't have (yet): shown on top
            val localOnly = store.history.filter { it.watchedAt > 0L && it.id !in remoteIds }.take(40)
            val localIds = localOnly.map { it.id }.toHashSet()
            val entries = ArrayList<Pair<String, VideoItem>>()
            localOnly.forEach { entries += dayLabel(it.watchedAt) to it }
            var last = ""
            remote.forEach { v ->
                if (v.historyDay.isNotEmpty()) last = v.historyDay
                entries += last.ifEmpty { "Earlier" } to v
            }
            val rows = buildRows(entries)
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = padding) {
                historyRows(rows, onOpen = { nav.push(Route.Video(it.id, it)) }, onRemove = { remove(it, it.id !in localIds) })
                item {
                    val err = paged.error
                    when {
                        err != null -> ErrorBox(err, onRetry = { paged.loadMore() })
                        paged.loading -> Loading()
                        rows.isEmpty() && paged.finished -> EmptyState(emptyText)
                    }
                }
            }
        } else {
            val entries = store.history.map { dayLabel(it.watchedAt) to it }
            if (entries.isEmpty()) {
                EmptyState(emptyText)
            } else {
                val rows = buildRows(entries)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                    historyRows(rows, onOpen = { nav.push(Route.Video(it.id, it)) }, onRemove = { remove(it, false) })
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Card,
            title = { Text("Clear watch history?") },
            text = {
                Text(
                    if (loggedIn) "This clears the watch history of this app and of your YouTube account." else "This clears the watch history of this app.",
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    store.clearHistory()
                    if (loggedIn) {
                        paged.removeWhere { true }
                        scope.launch {
                            if (Api.clearAccountHistory()) {
                                Toast.makeText(ctx, "Watch history cleared.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(ctx, "Couldn't clear your YouTube history. Try again.", Toast.LENGTH_LONG).show()
                                paged.pullRefresh()
                            }
                        }
                    }
                }) { Text("Clear", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel", color = TextPrimary) }
            }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historyRows(
    rows: List<HistRow>,
    onOpen: (VideoItem) -> Unit,
    onRemove: (VideoItem) -> Unit
) {
    items(rows, key = { it.key }) { row ->
        when (row) {
            is HistRow.Day -> Text(
                row.label,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 4.dp)
            )
            is HistRow.Shelf -> HistoryShortsShelf(row.shorts, onRemove)
            is HistRow.Video -> VideoRow(
                row.v,
                onClick = { onOpen(row.v) },
                onRemove = { onRemove(row.v) },
                removeLabel = "Remove from watch history"
            )
        }
    }
}

/** The Shorts of the history: logo + "Shorts" title, then tall cards that scroll sideways. */
@Composable
private fun HistoryShortsShelf(shorts: List<VideoItem>, onRemove: (VideoItem) -> Unit) {
    val nav = LocalNav.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(ShortsLogoVector, contentDescription = null, tint = Color(0xFFFF0033), modifier = Modifier.size(28.dp))
            Text(
                "Shorts",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextPrimary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Open Shorts") },
                        onClick = { menu = false; nav.selectTab(Route.Shorts) }
                    )
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(shorts, key = { it.id }) { v -> HistoryShortCard(v, { openInShorts(nav, v, shorts) }, { onRemove(v) }) }
        }
    }
}

@Composable
private fun HistoryShortCard(v: VideoItem, onClick: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val title = cleanShortTitle(v.title).ifEmpty { v.channelTitle }
    Box(
        Modifier
            .width(112.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Hairline)
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
    ) {
        VideoThumb(v.id, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color(0x14000000), 1f to Color(0xCC000000))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 10.dp)) {
            if (title.isNotEmpty()) {
                Text(
                    title, color = Color.White, fontSize = 14.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
            if (v.viewsText.isNotEmpty()) {
                Text(v.viewsText, color = Color(0xDDFFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Remove from watch history") },
                    onClick = { menu = false; onRemove() }
                )
            }
        }
    }
}
