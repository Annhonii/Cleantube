package com.cleantube.app.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.cleantube.app.data.formatBytes
import com.cleantube.app.data.formatEta
import com.cleantube.app.data.formatSpeed
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cleantube.app.data.Api
import com.cleantube.app.data.ApiException
import com.cleantube.app.data.DlStatus
import com.cleantube.app.data.DownloadItem
import com.cleantube.app.data.Downloads
import com.cleantube.app.data.Graph
import com.cleantube.app.data.StreamInfo
import com.cleantube.app.data.StreamOption
import com.cleantube.app.data.Streams
import com.cleantube.app.data.VideoItem

private fun sizeText(b: Long): String = formatBytes(b)

/** Pick a file and a place (inside the app, or the phone's Movies / Music folder), then download. */
@Composable
fun DownloadDialog(video: VideoItem, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val store = Graph.store
    var info by remember(video.id) { mutableStateOf<StreamInfo?>(null) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    var picked by remember(video.id) { mutableStateOf<StreamOption?>(null) }
    var where by remember { mutableStateOf(store.downloadWhere) }
    var threads by remember { mutableStateOf(store.downloadThreads) }

    LaunchedEffect(video.id) {
        try {
            val i = Streams.fetch(video.id)
            info = i
            picked = i.options.firstOrNull()
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Exception) {
            error = e.message ?: "Couldn't get the download links."
        }
    }

    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Card,
        title = { Text("Download") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val i = info
                val err = error
                when {
                    err != null -> Text(err, color = Danger, fontSize = 13.sp)
                    i == null -> Text("Getting download links…", color = TextMuted, fontSize = 13.sp)
                    else -> {
                        i.options.forEach { o ->
                            val sel = picked == o
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) Hairline else Color.Transparent)
                                    .clickable { picked = o }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    (if (sel) "● " else "○ ") + o.label,
                                    color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f)
                                )
                                val sz = sizeText(o.size)
                                if (sz.isNotEmpty()) Text(sz, color = TextMuted, fontSize = 12.sp)
                            }
                        }
                        Text("Save to", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("In app", onClick = { where = "app" }, active = where == "app")
                            Pill("Phone storage", onClick = { where = "phone" }, active = where == "phone")
                        }
                        Text("Connections (threads)", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 4, 8, 16, 32).forEach { n ->
                                Pill("$n", onClick = { threads = n }, active = threads == n)
                            }
                        }
                        Text(
                            if (where == "app") "Private to Voidstream, shown in its Downloads list. Removed if you uninstall the app."
                            else "Saved in Movies/Voidstream (or Music/Voidstream for audio), visible to your gallery and file manager.",
                            color = TextMuted, fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            val o = picked
            if (o != null) {
                TextButton(onClick = {
                    store.downloadWhere = where
                    store.downloadThreads = threads
                    Downloads.enqueue(ctx, video.id, info?.title?.ifEmpty { video.title } ?: video.title, o, where, threads)
                    android.widget.Toast.makeText(ctx, "Download started", android.widget.Toast.LENGTH_SHORT).show()
                    onDone()
                }) { Text("Download", color = Accent) }
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel", color = TextPrimary) } }
    )
}

/**
 * "Download all" of a playlist: loads every video of it, asks for one quality for all of them and where to save,
 * then queues them. They end up together in a folder named after the playlist.
 */
@Composable
fun PlaylistDownloadDialog(playlistId: String, title: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val store = Graph.store
    var videos by remember(playlistId) { mutableStateOf<List<VideoItem>?>(null) }
    var found by remember(playlistId) { mutableStateOf(0) }
    var error by remember(playlistId) { mutableStateOf<String?>(null) }
    var attempt by remember(playlistId) { mutableStateOf(0) }
    // 0 = best, a height = at most that tall, -1 = audio only
    var quality by remember { mutableStateOf(0) }
    // this choice is not saved: the setting for single downloads stays as it is
    var where by remember { mutableStateOf("phone") }
    var threads by remember { mutableStateOf(store.downloadThreads) }
    val folder = remember(title) { Downloads.folderName(title) }

    LaunchedEffect(playlistId, attempt) {
        error = null
        videos = null
        found = 0
        try {
            videos = Api.allPlaylistVideos(playlistId) { found = it }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load the playlist."
        }
    }

    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Card,
        title = { Text("Download all") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = videos
                val err = error
                when {
                    err != null -> {
                        Text(err, color = Danger, fontSize = 13.sp)
                        Row(Modifier.padding(top = 4.dp)) { Pill("Retry", onClick = { attempt++ }, active = true) }
                    }
                    list == null -> {
                        Text(
                            if (found > 0) "Loading the playlist… $found videos found" else "Loading the playlist…",
                            color = TextMuted, fontSize = 13.sp
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = SecondaryContainer)
                    }
                    list.isEmpty() -> Text("This playlist has no videos to download.", color = TextMuted, fontSize = 13.sp)
                    else -> {
                        Text(
                            "\"$folder\" · ${list.size} video${if (list.size == 1) "" else "s"}",
                            color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                        )
                        Text("Quality (the same for every video)", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Best", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p", -1 to "Audio only").forEach { (q, label) ->
                                Pill(label, onClick = { quality = q }, active = quality == q)
                            }
                        }
                        Text("Save to", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("In app", onClick = { where = "app" }, active = where == "app")
                            Pill("Phone storage", onClick = { where = "phone" }, active = where == "phone")
                        }
                        Text("Connections (threads)", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 4, 8, 16, 32).forEach { n ->
                                Pill("$n", onClick = { threads = n }, active = threads == n)
                            }
                        }
                        Text(
                            if (where == "phone") {
                                "Saved in ${if (quality < 0) "Music" else "Movies"}/Voidstream/$folder, visible to your gallery and file manager."
                            } else {
                                "Saved in a \"$folder\" folder inside the app. Removed if you uninstall the app."
                            },
                            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            "Videos download one after another. Each video's link is fetched when its turn comes.",
                            color = TextMuted, fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            val list = videos
            if (list != null && list.isNotEmpty()) {
                TextButton(onClick = {
                    Downloads.enqueuePlaylist(ctx, title, list, audioOnly = quality < 0, maxHeight = quality.coerceAtLeast(0), where = where, threads = threads)
                    android.widget.Toast.makeText(
                        ctx, "${list.size} video${if (list.size == 1) "" else "s"} added to Downloads", android.widget.Toast.LENGTH_SHORT
                    ).show()
                    onDone()
                }) { Text("Download ${list.size}", color = Accent) }
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel", color = TextPrimary) } }
    )
}

// ------------------------------------------------------------------ Downloads screen
//
// Flat Material 3: no shadows, no blur, no gradients. Surfaces, spacing and type do all the work.
// Every card is tinted with the colours of ITS video (the thumbnail's main colour, same pipeline as the app theme),
// so the list changes colour from one video to the next.

private val ThumbW = 120.dp
private val ThumbH = ThumbW * 9f / 16f

/** Survives the screen: saving a file to storage must finish (and say so) even when the card scrolls away. */
private val dlScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private fun toast(ctx: android.content.Context, text: String) =
    android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()

@Composable
fun DownloadsScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val list = Downloads.items
    var pasted by remember { mutableStateOf<VideoItem?>(null) }
    var link by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var overflow by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(searching) {
        if (searching) runCatching { searchFocus.requestFocus() }
    }

    val q = query.trim()
    val shown = if (searching && q.isNotEmpty()) list.filter { it.title.contains(q, ignoreCase = true) } else list.toList()
    val active = shown.filter { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }
    val finished = shown.filter { it.status != DlStatus.RUNNING && it.status != DlStatus.QUEUED }
    val waiting = list.count { it.status == DlStatus.QUEUED }
    val inApp = list.filter { it.status == DlStatus.DONE && it.where == "app" }

    fun openLink(text: String) {
        val uri = Regex("https?://\\S+").find(text)?.value?.let { Uri.parse(it) }
        val r = uri?.let { routeFromUri(it) }
        if (r is Route.Video) {
            pasted = VideoItem(id = r.id, title = "")
            link = ""
        } else {
            toast(ctx, "Only YouTube video links can be downloaded.")
        }
    }

    fun saveAll() {
        val todo = inApp.toList()
        dlScope.launch {
            var ok = 0
            var failed = 0
            todo.forEach { d ->
                try {
                    Downloads.saveToStorage(ctx, d)
                    ok++
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                }
            }
            toast(
                ctx,
                if (failed == 0) "Saved $ok video${if (ok == 1) "" else "s"} to storage"
                else "Saved $ok, couldn't save $failed"
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        // top bar: back | search, more
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DlIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", size = 44.dp, iconSize = 22.dp, bg = Card) { nav.back() }
            Spacer(Modifier.weight(1f))
            DlIconButton(Icons.Rounded.Search, "Search downloads", size = 44.dp, iconSize = 24.dp) {
                searching = !searching
                if (!searching) query = ""
            }
            Box {
                DlIconButton(Icons.Rounded.MoreVert, "More options", size = 44.dp, iconSize = 24.dp) { overflow = true }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    DropdownMenuItem(
                        text = { Text("Save all in-app videos to storage") },
                        leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                        enabled = inApp.isNotEmpty(),
                        onClick = { overflow = false; saveAll() }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove all waiting ($waiting)") },
                        leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                        enabled = waiting > 0,
                        onClick = { overflow = false; Downloads.removeWaiting() }
                    )
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = listBottomPad())) {
            // large title + count badge
            item {
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Downloads", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp, maxLines = 1
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "(${list.size})", color = OnSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SecondaryContainer)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            if (searching) {
                item {
                    PillTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search downloads",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        leading = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(22.dp)) },
                        trailing = {
                            if (query.isNotEmpty()) {
                                DlIconButton(Icons.Rounded.Close, "Clear", size = 36.dp, iconSize = 20.dp, tint = TextMuted) { query = "" }
                            }
                        },
                        focusRequester = searchFocus
                    )
                }
            }
            // URL paste bar
            item {
                LinkBar(
                    value = link,
                    onValueChange = { link = it },
                    onPaste = {
                        val t = readClipboard(ctx).trim()
                        if (t.isEmpty()) {
                            toast(ctx, "Clipboard is empty. Copy a link first.")
                        } else {
                            link = t
                            openLink(t)
                        }
                    },
                    onGo = { openLink(link) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }

            if (list.isEmpty()) {
                item { EmptyState("Nothing downloaded yet. Open a video or a Short and tap Download, or paste a link above.") }
            } else if (shown.isEmpty()) {
                item { EmptyState("No downloads match \"$q\".") }
            }
            if (active.isNotEmpty()) {
                item { SectionLabel("Downloading (${active.size})") }
                items(active, key = { it.id }) { d -> DownloadRow(d) }
            }
            if (finished.isNotEmpty()) {
                item { SectionLabel("Downloaded") }
                items(finished, key = { it.id }) { d -> DownloadRow(d) }
            }
        }
    }

    pasted?.let { v -> DownloadDialog(v) { pasted = null } }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
    )
}

/** Round icon-only button: [bg] is transparent unless given. */
@Composable
private fun DlIconButton(
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    tint: Color = TextPrimary,
    bg: Color = Color.Transparent,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .bouncyClickable(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Full-width pill with a thin border: link icon, the field, and a tonal "Paste" button inside the right edge. */
@Composable
private fun LinkBar(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(1.dp, Hairline, CircleShape)
            .clip(CircleShape)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Link, contentDescription = null, tint = TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("Paste YouTube or web link...", color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontFamily = AppFont),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .height(44.dp)
                .clip(CircleShape)
                .background(SecondaryContainer)
                .bouncyClickable(pressedScale = 0.94f, onClick = onPaste)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Paste", color = OnSecondaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun rememberCardPalette(videoId: String): VideoTint = rememberVideoTint(videoId)

/** "720p" out of the label ("720p video + sound", "Up to 1080p"); empty when there is no picture size (audio, "Best"). */
private fun resolutionOf(d: DownloadItem): String =
    if (d.audioOnly) "" else Regex("\\d{3,4}p").find(d.label)?.value.orEmpty()

@Composable
private fun DownloadRow(d: DownloadItem) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var confirm by remember(d.id) { mutableStateOf(false) }
    var menu by remember(d.id) { mutableStateOf(false) }
    val running = d.status == DlStatus.RUNNING
    val queued = d.status == DlStatus.QUEUED
    val live = running || queued
    val done = d.status == DlStatus.DONE
    val failed = d.status == DlStatus.FAILED
    val cancelled = d.status == DlStatus.CANCELLED
    val c = rememberCardPalette(d.videoId)
    val res = resolutionOf(d)

    fun play() {
        if (Downloads.exists(ctx, d)) nav.push(Route.LocalPlay(Downloads.uriOf(d).toString(), d.title))
        else android.widget.Toast.makeText(ctx, "The file is gone (deleted or moved).", android.widget.Toast.LENGTH_LONG).show()
    }

    fun saveToStorage() {
        dlScope.launch {
            try {
                val place = Downloads.saveToStorage(ctx, d)
                toast(ctx, "Saved to $place")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(ctx, e.message ?: "Couldn't save the file.")
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .bouncyClickable(enabled = done, pressedScale = 0.98f) { play() }
            .clip(RoundedCornerShape(20.dp))
            .background(c.container)
            .padding(10.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        // 16:9 thumbnail with the resolution in its bottom-right corner
        Box(Modifier.width(ThumbW).height(ThumbH)) {
            AppImage("https://i.ytimg.com/vi/${d.videoId}/mqdefault.jpg", Modifier.fillMaxSize(), RoundedCornerShape(12.dp))
            val badgeText = if (d.audioOnly) "Audio" else res
            if (badgeText.isNotEmpty()) {
                Text(
                    badgeText, color = c.onBadge, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.badge)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            // title (and, while it is going on, the progress)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    d.title, color = c.onContainer, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (running && d.phase.isEmpty()) {
                    val speed = formatSpeed(d.speed)
                    Text(
                        listOf(speed, if (d.etaSec >= 0) "${formatEta(d.etaSec)} left" else "calculating time left…")
                            .filter { it.isNotEmpty() }.joinToString("  •  "),
                        color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (failed) {
                    Text("Failed: ${d.error}", color = Danger, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (live) {
                    // the bar glides to each new value instead of jumping
                    val shownProgress by animateFloatAsState(d.progress, tween(500, easing = LinearOutSlowInEasing), label = "dlProgress")
                    if (running && d.phase.isEmpty() && d.progress > 0f) {
                        LinearProgressIndicator(progress = { shownProgress }, modifier = Modifier.fillMaxWidth(), color = c.primary, trackColor = c.track)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = c.primary, trackColor = c.track)
                    }
                }
            }
            // metadata on the left, compact icon buttons on the right
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val meta = when {
                    queued -> "Waiting in queue…"
                    running && d.phase.isNotEmpty() -> d.phase
                    running -> {
                        val pct = (d.progress * 100).toInt()
                        val sizes = if (d.total > 0) "${sizeText(d.bytes)} of ${sizeText(d.total)}" else sizeText(d.bytes)
                        "$pct% • $sizes"
                    }
                    failed -> "Failed"
                    cancelled -> "Cancelled"
                    else -> listOf(res, if (d.audioOnly) "Audio" else "Video", sizeText(d.bytes)).filter { it.isNotEmpty() }.joinToString(" • ")
                }
                Text(
                    meta, color = if (failed) Danger else c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                when {
                    running -> DlIconButton(Icons.Rounded.Close, "Cancel", size = 32.dp, tint = c.muted) { Downloads.cancel(d) }
                    queued -> DlIconButton(Icons.Rounded.Close, "Remove", size = 32.dp, tint = c.muted) {
                        Downloads.remove(ctx, d, deleteFile = false)
                    }
                    else -> {
                        if (failed || cancelled) {
                            DlIconButton(Icons.Rounded.Refresh, "Retry", size = 32.dp, tint = c.muted) { Downloads.retry(ctx, d) }
                        }
                        DlIconButton(Icons.Outlined.DeleteOutline, "Delete", size = 32.dp, tint = c.muted) { confirm = true }
                        Box {
                            DlIconButton(Icons.Rounded.MoreVert, "More options", size = 32.dp, tint = c.muted) { menu = true }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (done) {
                                    DropdownMenuItem(
                                        text = { Text("Play") },
                                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                                        onClick = { menu = false; play() }
                                    )
                                    if (d.where == "app") {
                                        DropdownMenuItem(
                                            text = { Text("Save to storage") },
                                            leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                                            onClick = { menu = false; saveToStorage() }
                                        )
                                    } else {
                                        DropdownMenuItem(text = { Text("Saved in phone storage") }, enabled = false, onClick = {})
                                    }
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Retry") },
                                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                        onClick = { menu = false; Downloads.retry(ctx, d) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Card,
            title = { Text("Delete download?") },
            text = { Text(if (d.status == DlStatus.DONE) "The file will be removed from your phone." else "Remove it from the list.", color = TextMuted) },
            confirmButton = { TextButton(onClick = { Downloads.remove(ctx, d, deleteFile = true); confirm = false }) { Text("Delete", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = TextPrimary) } }
        )
    }
}

/** Plays a downloaded file with the system VideoView (no network needed). */
@Composable
fun LocalPlayScreen(route: Route.LocalPlay) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(route.title, onBack = { nav.back() })
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    val mc = MediaController(ctx)
                    mc.setAnchorView(this)
                    setMediaController(mc)
                    setVideoURI(Uri.parse(route.uri))
                    setOnPreparedListener { start() }
                }
            },
            onRelease = { it.stopPlayback() }
        )
    }
}
