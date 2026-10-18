package com.cleantube.app.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

private fun sizeText(b: Long): String = when {
    b <= 0 -> ""
    b < 1_000_000 -> "${b / 1000} KB"
    b < 1_000_000_000 -> String.format(java.util.Locale.US, "%.1f MB", b / 1e6)
    else -> String.format(java.util.Locale.US, "%.2f GB", b / 1e9)
}

/** Pick a file and a place (inside the app, or the phone's Movies / Music folder), then download. */
@Composable
fun DownloadDialog(video: VideoItem, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val store = Graph.store
    var info by remember(video.id) { mutableStateOf<StreamInfo?>(null) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    var picked by remember(video.id) { mutableStateOf<StreamOption?>(null) }
    var where by remember { mutableStateOf(store.downloadWhere) }

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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val i = info
                val err = error
                when {
                    err != null -> Text(err, color = Accent, fontSize = 13.sp)
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
                    Downloads.enqueue(ctx, video.id, info?.title?.ifEmpty { video.title } ?: video.title, o, where)
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
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val list = Downloads.items

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle("Downloads", onBack = { nav.back() })
        if (list.isEmpty()) {
            EmptyState("Nothing downloaded yet. Open a video and tap Download.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list.toList(), key = { it.id }) { d -> DownloadRow(d) }
            }
        }
    }
}

@Composable
private fun DownloadRow(d: DownloadItem) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var confirm by remember(d.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .clickable(enabled = d.status == DlStatus.DONE) {
                if (Downloads.exists(ctx, d)) nav.push(Route.LocalPlay(Downloads.uriOf(d).toString(), d.title))
                else android.widget.Toast.makeText(ctx, "The file is gone (deleted or moved).", android.widget.Toast.LENGTH_LONG).show()
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(d.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val state = when (d.status) {
            DlStatus.QUEUED -> "Waiting…"
            DlStatus.RUNNING -> "${(d.progress * 100).toInt()}%  ${sizeText(d.bytes)}"
            DlStatus.DONE -> sizeText(d.bytes)
            DlStatus.FAILED -> "Failed: ${d.error}"
            DlStatus.CANCELLED -> "Cancelled"
        }
        Text(
            listOf(d.label, if (d.where == "phone") "Phone storage" else "In app", state).filter { it.isNotEmpty() }.joinToString(" • "),
            color = if (d.status == DlStatus.FAILED) Accent else TextMuted, fontSize = 12.sp
        )
        if (d.status == DlStatus.RUNNING || d.status == DlStatus.QUEUED) {
            LinearProgressIndicator(
                progress = { d.progress },
                modifier = Modifier.fillMaxWidth(),
                color = Accent,
                trackColor = Hairline
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.status == DlStatus.RUNNING || d.status == DlStatus.QUEUED) {
                Pill("Cancel", onClick = { Downloads.remove(ctx, d, deleteFile = true) })
            } else {
                Pill("Delete", onClick = { confirm = true })
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Card,
            title = { Text("Delete download?") },
            text = { Text(if (d.status == DlStatus.DONE) "The file will be removed from your phone." else "Remove it from the list.", color = TextMuted) },
            confirmButton = { TextButton(onClick = { Downloads.remove(ctx, d, deleteFile = true); confirm = false }) { Text("Delete", color = Accent) } },
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
