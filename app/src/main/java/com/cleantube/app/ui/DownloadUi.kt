package com.cleantube.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
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
                            if (where == "app") "Private to CleanTube, shown in its Downloads list. Removed if you uninstall the app."
                            else "Saved in Movies/CleanTube (or Music/CleanTube for audio), visible to your gallery and file manager.",
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

@Composable
fun DownloadsScreen() {
    val ctx = LocalContext.current
    val list = Downloads.items
    val active = list.filter { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED }
    val finished = list.filter { it.status != DlStatus.RUNNING && it.status != DlStatus.QUEUED }
    var pasted by remember { mutableStateOf<VideoItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        TabTopBar("Downloads")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Pill("Paste YouTube link", icon = Icons.Rounded.ContentPaste, onClick = {
                        val text = readClipboard(ctx)
                        val uri = Regex("https?://\\S+").find(text)?.value?.let { Uri.parse(it) }
                        val r = uri?.let { routeFromUri(it) }
                        if (r is Route.Video) pasted = VideoItem(id = r.id, title = "")
                        else android.widget.Toast.makeText(ctx, "Copy a YouTube video link first.", android.widget.Toast.LENGTH_SHORT).show()
                    })
                }
            }
            if (list.isEmpty()) {
                item { EmptyState("Nothing downloaded yet. Open a video or a Short and tap Download, or paste a link above.") }
            }
            if (active.isNotEmpty()) {
                item { SectionLabel("DOWNLOADING (${active.size})") }
                items(active, key = { it.id }) { d -> DownloadRow(d) }
            }
            if (finished.isNotEmpty()) {
                item { SectionLabel("DOWNLOADED") }
                items(finished, key = { it.id }) { d -> DownloadRow(d) }
            }
        }
    }

    pasted?.let { v -> DownloadDialog(v) { pasted = null } }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun DownloadRow(d: DownloadItem) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var confirm by remember(d.id) { mutableStateOf(false) }
    val running = d.status == DlStatus.RUNNING
    val live = running || d.status == DlStatus.QUEUED

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .clickable(enabled = d.status == DlStatus.DONE) {
                if (Downloads.exists(ctx, d)) nav.push(Route.LocalPlay(Downloads.uriOf(d).toString(), d.title))
                else android.widget.Toast.makeText(ctx, "The file is gone (deleted or moved).", android.widget.Toast.LENGTH_LONG).show()
            }
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        AppImage("https://i.ytimg.com/vi/${d.videoId}/mqdefault.jpg", Modifier.width(120.dp).aspectRatio(16f / 9f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(d.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(d.label, if (d.where == "phone") "Phone storage" else "In app").joinToString(" • "),
                color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )

            when (d.status) {
                DlStatus.QUEUED -> Text("Waiting in queue…", color = TextMuted, fontSize = 12.sp)
                DlStatus.RUNNING -> {
                    if (d.phase.isNotEmpty()) {
                        Text(d.phase, color = TextPrimary, fontSize = 12.sp)
                    } else {
                        val pct = (d.progress * 100).toInt()
                        val sizes = if (d.total > 0) "${sizeText(d.bytes)} of ${sizeText(d.total)}" else sizeText(d.bytes)
                        Text("$pct%  •  $sizes", color = TextPrimary, fontSize = 12.sp)
                        val speed = formatSpeed(d.speed)
                        Text(
                            listOf(speed, if (d.etaSec >= 0) "${formatEta(d.etaSec)} left" else "calculating time left…")
                                .filter { it.isNotEmpty() }.joinToString("  •  "),
                            color = TextMuted, fontSize = 12.sp
                        )
                    }
                }
                DlStatus.DONE -> Text(sizeText(d.bytes), color = TextMuted, fontSize = 12.sp)
                DlStatus.FAILED -> Text("Failed: ${d.error}", color = Danger, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                DlStatus.CANCELLED -> Text("Cancelled", color = TextMuted, fontSize = 12.sp)
            }

            if (live) {
                if (running && d.phase.isEmpty() && d.progress > 0f) {
                    LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Hairline)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Hairline)
                }
            }

            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    running -> Pill("Cancel", onClick = { Downloads.cancel(d) })
                    d.status == DlStatus.QUEUED -> Pill("Remove", onClick = { Downloads.remove(ctx, d, deleteFile = false) })
                    else -> {
                        if (d.status == DlStatus.FAILED || d.status == DlStatus.CANCELLED) {
                            Pill("Retry", onClick = { Downloads.retry(ctx, d) }, active = true)
                        }
                        Pill("Delete", onClick = { confirm = true })
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
