package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cleantube.app.data.Playback
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.net.Uri
import android.content.Intent
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.itemsIndexed
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

// ------------------------------------------------------------------ Download option dialog
//
// Opens the moment it is asked for: the stream links the player already looked up are reused ([Playback.cached]),
// otherwise the one-request fast path is used ([Playback.resolve]) and only the quality list shows a skeleton while
// it waits. The colours come from the video's own thumbnail, the same pipeline the app theme and the video page use.

/** Colours of the dialog: the video's own once its thumbnail is read (or when "colours from the video" is off, the normal ones). */
private class DlColors(
    val container: Color, val onSurface: Color, val muted: Color, val row: Color,
    val selected: Color, val onSelected: Color, val chip: Color, val onChip: Color,
    val primary: Color, val onPrimary: Color, val outline: Color
)

@Composable
private fun dlAnim(c: Color): Color = animateColorAsState(c, tween(500), label = "dlColour").value

@Composable
private fun rememberDlColors(videoId: String): DlColors {
    val s = rememberThumbScheme(videoId)
    return DlColors(
        container = dlAnim(s?.surfaceContainerHigh ?: Card),
        onSurface = dlAnim(s?.onSurface ?: TextPrimary),
        muted = dlAnim(s?.onSurfaceVariant ?: TextMuted),
        row = dlAnim(s?.surfaceContainerHighest ?: SurfaceHighest),
        selected = dlAnim(s?.primaryContainer ?: AccentSoft),
        onSelected = dlAnim(s?.onPrimaryContainer ?: OnAccentSoft),
        chip = dlAnim(s?.secondaryContainer ?: SecondaryContainer),
        onChip = dlAnim(s?.onSecondaryContainer ?: OnSecondaryContainer),
        primary = dlAnim(s?.primary ?: Accent),
        onPrimary = dlAnim(s?.onPrimary ?: OnAccent),
        outline = dlAnim(s?.outline ?: Hairline)
    )
}

private fun heightBadge(h: Int): String = when {
    h >= 2160 -> "4K"
    h >= 1440 -> "2K"
    h >= 1080 -> "FHD"
    h >= 720 -> "HD"
    h > 0 -> "$h"
    else -> "VID"
}

private fun heightName(h: Int): String = when {
    h >= 2160 -> "2160p (4K)"
    h >= 1440 -> "1440p (2K)"
    h == 1080 -> "1080p (FHD)"
    h == 720 -> "720p (HD)"
    h > 0 -> "${h}p"
    else -> "Video"
}

@Composable
private fun DlSectionTitle(text: String, c: DlColors, modifier: Modifier = Modifier) {
    Text(text, color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}

/** Round option with an optional icon: filled with a tick when on, thin outline when off. */
@Composable
private fun DlChip(label: String, icon: ImageVector?, on: Boolean, c: DlColors, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) c.chip else Color.Transparent, tween(220), label = "dlChipBg")
    val fg by animateColorAsState(if (on) c.onChip else c.muted, tween(220), label = "dlChipFg")
    val edge by animateColorAsState(if (on) c.chip else c.outline, tween(220), label = "dlChipEdge")
    Row(
        modifier
            .height(56.dp)
            .clip(CircleShape)
            .border(1.dp, edge, CircleShape)
            .background(bg)
            .bouncyClickable(pressedScale = 0.96f, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (on) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = fg, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** One quality: radio, a small badge (FHD / HD / 480 ...), "1080p (FHD) • MP4", and the estimated size. */
@Composable
private fun DlQualityRow(on: Boolean, badge: String, label: String, size: String, shape: RoundedCornerShape, c: DlColors, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) c.selected else c.row, tween(200), label = "dlRowBg")
    val fg by animateColorAsState(if (on) c.onSelected else c.onSurface, tween(200), label = "dlRowFg")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 18.dp)
            .height(54.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = on, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = fg, unselectedColor = c.muted),
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(fg.copy(alpha = 0.22f)).padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(badge, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = fg, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(size, color = fg, fontSize = 17.sp, maxLines = 1)
    }
}

/** Placeholder rows while the links are being looked up: the dialog is already usable, only this part waits. */
@Composable
private fun DlQualitySkeleton(c: DlColors) {
    val t = rememberInfiniteTransition(label = "dlSkeleton")
    val a by t.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "dlSkeletonAlpha")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { i ->
            Box(
                Modifier.fillMaxWidth().height(54.dp)
                    .clip(groupedCardShape(i, 5, 22.dp))
                    .background(c.row.copy(alpha = a))
            )
        }
    }
}

/** Pick a file and a place (inside the app, the phone's folders, or both), then download. */
@Composable
fun DownloadDialog(video: VideoItem, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val store = Graph.store
    val c = rememberDlColors(video.id)

    // links the player already looked up are used straight away: nothing to wait for
    var info by remember(video.id) { mutableStateOf(Playback.cached(video.id)) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    var attempt by remember(video.id) { mutableStateOf(0) }
    var audio by remember(video.id) { mutableStateOf(false) }
    var pickedVideo by remember(video.id) { mutableStateOf<StreamOption?>(null) }
    var pickedAudio by remember(video.id) { mutableStateOf<StreamOption?>(null) }
    // "In-app" and "Phone storage" can both be on: the file stays in the app and a copy goes to the phone
    var toApp by remember { mutableStateOf(store.downloadWhere != "phone") }
    var toPhone by remember { mutableStateOf(store.downloadWhere != "app") }
    var threads by remember { mutableStateOf(store.downloadThreads) }

    LaunchedEffect(video.id, attempt) {
        if (info != null && attempt == 0) return@LaunchedEffect
        error = null
        try {
            info = Playback.resolve(video.id, force = attempt > 0)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't get the download links."
        }
    }

    val videoOpts = remember(info) { info?.let { Playback.ladder(it) }.orEmpty() }
    val audioOpts = remember(info) { info?.options?.filter { it.audioOnly }.orEmpty() }
    // a video that has no picture file (music, for example) opens on "Audio"
    LaunchedEffect(info) { if (info != null && videoOpts.isEmpty() && audioOpts.isNotEmpty()) audio = true }
    val list = if (audio) audioOpts else videoOpts
    val selected = (if (audio) pickedAudio else pickedVideo)?.takeIf { it in list } ?: list.firstOrNull()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            store.downloadTree = uri.toString()
        }
    }

    fun start(o: StreamOption) {
        store.downloadWhere = when {
            toApp && toPhone -> "both"
            toPhone -> "phone"
            else -> "app"
        }
        store.downloadThreads = threads
        Downloads.enqueue(
            ctx, video.id, info?.title?.ifEmpty { video.title } ?: video.title, o,
            where = if (toPhone && !toApp) "phone" else "app", threads = threads,
            alsoPhone = toApp && toPhone
        )
        android.widget.Toast.makeText(ctx, "Download started", android.widget.Toast.LENGTH_SHORT).show()
        onDone()
    }

    val maxH = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    val threadLabels = remember { (1..32).map { "$it" } }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 14.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .heightIn(max = maxH)
                .clip(RoundedCornerShape(36.dp))
                .background(c.container)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Download Option", color = c.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, modifier = Modifier.weight(1f)
                )
                DlIconButton(Icons.Rounded.Close, "Close", size = 44.dp, iconSize = 26.dp, tint = c.onSurface) { onDone() }
            }

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DlSectionTitle("Media Type Selector", c, Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DlChip("Video", Icons.Rounded.Videocam, on = !audio, c = c, modifier = Modifier.weight(1f)) { audio = false }
                    DlChip("Audio", Icons.Rounded.MusicNote, on = audio, c = c, modifier = Modifier.weight(1f)) { audio = true }
                }

                DlSectionTitle("Quality", c, Modifier.padding(top = 10.dp))
                Row(Modifier.fillMaxWidth().padding(start = 64.dp, end = 18.dp)) {
                    Text("Quality", color = c.muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("Est. Size", color = c.muted, fontSize = 15.sp)
                }
                val err = error
                when {
                    err != null -> {
                        Text(err, color = Danger, fontSize = 13.sp)
                        Row { Pill("Retry", onClick = { attempt++ }, active = true) }
                    }
                    info == null -> DlQualitySkeleton(c)
                    list.isEmpty() -> Text(
                        if (audio) "YouTube offers no audio file for this video." else "YouTube offers no video file for this video.",
                        color = c.muted, fontSize = 14.sp
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        list.forEachIndexed { i, o ->
                            val on = o == selected
                            val h = Playback.heightOf(o)
                            val kbps = Regex("(\\d+) kbps").find(o.label)?.groupValues?.get(1)
                            DlQualityRow(
                                on = on,
                                badge = if (audio) o.ext.uppercase() else heightBadge(h),
                                label = if (audio) (kbps?.let { "$it kbps" } ?: "Audio") + " • " + o.ext.uppercase()
                                else heightName(h) + " • " + o.ext.uppercase(),
                                size = sizeText(o.size),
                                shape = if (on) RoundedCornerShape(27.dp) else groupedCardShape(i, list.size, 22.dp),
                                c = c
                            ) { if (audio) pickedAudio = o else pickedVideo = o }
                        }
                    }
                }

                DlSectionTitle("Save Location", c, Modifier.padding(top = 14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DlChip("In-app", null, on = toApp, c = c, modifier = Modifier.weight(1f)) {
                        if (!toApp) toApp = true else if (toPhone) toApp = false
                    }
                    DlChip("Phone storage", null, on = toPhone, c = c, modifier = Modifier.weight(1.25f)) {
                        if (!toPhone) toPhone = true else if (toApp) toPhone = false
                    }
                }
                if (toPhone) {
                    val tree = store.downloadTree
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.row).padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = c.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Download Path:", color = c.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (tree.isNotEmpty()) Downloads.treeLabel(tree) else Downloads.defaultFolderLabel(audio),
                                color = c.muted, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            if (tree.isNotEmpty()) {
                                Text(
                                    "Use default folder", color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 4.dp).clickable { store.downloadTree = "" }
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(c.chip)
                                .bouncyClickable(pressedScale = 0.94f, onClick = { picker.launch(null) })
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = c.onChip, modifier = Modifier.size(26.dp))
                            Text("Change", color = c.onChip, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Text(
                    when {
                        toApp && toPhone -> "Kept in Voidstream's Downloads list, and a copy is saved to your phone."
                        toApp -> "Private to Voidstream, shown in its Downloads list. Removed if you uninstall the app."
                        else -> "Saved to your phone, visible to your gallery and file manager."
                    },
                    color = c.muted, fontSize = 13.sp
                )

                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = c.primary, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Connections (threads)", color = c.onSurface, fontSize = 19.sp)
                }
                TickSlider(
                    labels = threadLabels,
                    index = (threads - 1).coerceIn(0, threadLabels.lastIndex),
                    onPick = { threads = it + 1 },
                    resetIndex = 7,
                    accentColor = c.primary, trackColor = c.row, idleColor = c.muted, contentColor = c.onSurface
                )
                Spacer(Modifier.height(4.dp))
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDone) { Text("Cancel", color = c.onSurface, fontSize = 18.sp) }
                Spacer(Modifier.width(14.dp))
                val go = selected
                Box(
                    Modifier
                        .height(56.dp)
                        .then(if (go != null) Modifier.bouncyClickable(pressedScale = 0.95f, onClick = { start(go) }) else Modifier)
                        .clip(CircleShape)
                        .background(c.primary.copy(alpha = if (go != null) 1f else 0.35f))
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Download", color = c.onPrimary, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
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
        title = { Text("Download playlist") },
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
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    // Downloads is a tab: the shared header card sits on top, so the page starts under it. If the tab was turned off
    // in Settings the page is pushed instead: it then has its own status bar padding and a back button.
    val isTab = visibleTabs(com.cleantube.app.data.Graph.store).any { it.route == Route.Downloads }
    val headerH = collapsingHeaderHeight(Route.Downloads)

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
                .then(if (isTab) Modifier.padding(top = headerH) else Modifier.statusBarsPadding())
                .height(if (isTab) 56.dp else 64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedIds.isNotEmpty()) {
                DlIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Exit selection", size = 44.dp, iconSize = 22.dp, bg = Card) { selectedIds = emptySet() }
                Text("${selectedIds.size} selected", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
            } else {
                if (!isTab) DlIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", size = 44.dp, iconSize = 22.dp, bg = Card) { nav.back() }
                Spacer(Modifier.weight(1f))
            }
            DlIconButton(Icons.Rounded.Search, "Search downloads", size = 44.dp, iconSize = 24.dp) {
                searching = !searching
                if (!searching) query = ""
            }
            Box {
                DlIconButton(Icons.Rounded.MoreVert, "More options", size = 44.dp, iconSize = 24.dp) { overflow = true }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    if (selectedIds.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Delete selected (${selectedIds.size})", color = Danger) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Danger) },
                            onClick = { overflow = false; confirmDeleteSelected = true }
                        )
                    }
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
                        "Downloads", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.4).sp, maxLines = 1
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
                itemsIndexed(active, key = { _, item -> item.id }) { i, d ->
                    DownloadRow(d, groupedCardShape(i, active.size, 20.dp), selected = d.id in selectedIds,
                        selectionMode = selectedIds.isNotEmpty(),
                        onLongPress = { selectedIds = selectedIds + d.id },
                        onToggleSelection = { selectedIds = if (d.id in selectedIds) selectedIds - d.id else selectedIds + d.id })
                }
            }
            if (finished.isNotEmpty()) {
                item { SectionLabel("Downloaded") }
                itemsIndexed(finished, key = { _, item -> item.id }) { i, d ->
                    DownloadRow(d, groupedCardShape(i, finished.size, 20.dp), selected = d.id in selectedIds,
                        selectionMode = selectedIds.isNotEmpty(),
                        onLongPress = { selectedIds = selectedIds + d.id },
                        onToggleSelection = { selectedIds = if (d.id in selectedIds) selectedIds - d.id else selectedIds + d.id })
                }
            }
        }
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            containerColor = Card,
            title = { Text("Delete ${selectedIds.size} downloads?") },
            text = { Text("The selected files will be removed from your device.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    list.filter { it.id in selectedIds }.toList().forEach { Downloads.remove(ctx, it, deleteFile = true) }
                    selectedIds = emptySet()
                    confirmDeleteSelected = false
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteSelected = false }) { Text("Cancel", color = TextPrimary) } }
        )
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

/** Colours of one card: the video's own colours once its thumbnail is read, the normal app colours until then. */
private class CardPalette(
    val container: Color, val onContainer: Color, val muted: Color,
    val primary: Color, val track: Color, val badge: Color, val onBadge: Color
)

@Composable
private fun rememberCardPalette(videoId: String): CardPalette {
    // Keep download rows neutral; thumbnail-derived colours made each row look inconsistent.
    return CardPalette(
        container = Card,
        onContainer = TextPrimary,
        muted = TextMuted,
        primary = Accent,
        track = SecondaryContainer,
        badge = SecondaryContainer,
        onBadge = OnSecondaryContainer
    )
}

/** "720p" out of the label ("720p video + sound", "Up to 1080p"); empty when there is no picture size (audio, "Best"). */
private fun resolutionOf(d: DownloadItem): String =
    if (d.audioOnly) "" else Regex("\\d{3,4}p").find(d.label)?.value.orEmpty()

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DownloadRow(
    d: DownloadItem,
    groupShape: RoundedCornerShape,
    selected: Boolean,
    selectionMode: Boolean,
    onLongPress: () -> Unit,
    onToggleSelection: () -> Unit
) {
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

    // opens in the app's main video player (the one that plays streams), straight from the file: no network is needed
    fun play() {
        if (Downloads.exists(ctx, d)) {
            val details = listOf(
                res, if (d.audioOnly) "Audio" else "Video", sizeText(d.bytes),
                if (d.where == "phone") "Phone storage" else "In app"
            ).filter { it.isNotEmpty() }.joinToString(" • ")
            VideoHost.openLocal(d.videoId, LocalVideo(Downloads.uriOf(d).toString(), d.title, details))
        } else {
            android.widget.Toast.makeText(ctx, "The file is gone (deleted or moved).", android.widget.Toast.LENGTH_LONG).show()
        }
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
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .animateContentSize(tween(280, easing = LinearOutSlowInEasing))
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelection() else if (done) play() },
                onLongClick = onLongPress
            )
            .clip(groupShape)
            .background(if (selected) SecondaryContainer else c.container)
            .padding(10.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn(tween(180)) + slideInHorizontally(
                animationSpec = tween(280, easing = LinearOutSlowInEasing),
                initialOffsetX = { -it / 2 }
            ),
            exit = fadeOut(tween(120)) + slideOutHorizontally(
                animationSpec = tween(200, easing = LinearOutSlowInEasing),
                targetOffsetX = { -it / 2 }
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelection() }, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(4.dp))
            }
        }
        // 16:9 thumbnail with the resolution in its bottom-right corner
        Box(Modifier.width(ThumbW).height(ThumbH)) {
            AppImage("https://i.ytimg.com/vi/${d.videoId}/mqdefault.jpg", Modifier.fillMaxSize(), RoundedCornerShape(12.dp))
            val badgeText = if (d.audioOnly) "Audio" else res
            if (badgeText.isNotEmpty()) {
                Text(
                    badgeText, color = c.onBadge, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1,
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
                    d.title, color = c.onContainer, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (running && d.phase.isEmpty()) {
                    val speed = formatSpeed(d.speed)
                    Text(
                        if (d.paused) "Paused"
                        else listOf(speed, if (d.etaSec >= 0) "${formatEta(d.etaSec)} left" else "calculating time left…")
                            .filter { it.isNotEmpty() }.joinToString("  •  "),
                        color = c.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (failed) {
                    Text("Failed: ${d.error}", color = Danger, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // finished, but the extra copy to phone storage didn't work
                if (done && d.error.isNotEmpty()) {
                    Text(d.error, color = Danger, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (live) {
                    // the bar glides to each new value instead of jumping
                    val shownProgress by animateFloatAsState(d.progress, tween(500, easing = LinearOutSlowInEasing), label = "dlProgress")
                    if (running && d.phase.isEmpty() && (d.progress > 0f || d.paused)) {
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
                        (if (d.paused) "Paused • " else "") + "$pct% • $sizes"
                    }
                    failed -> "Failed"
                    cancelled -> "Cancelled"
                    else -> listOf(res, if (d.audioOnly) "Audio" else "Video", sizeText(d.bytes)).filter { it.isNotEmpty() }.joinToString(" • ")
                }
                Text(
                    meta, color = if (failed) Danger else c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                when {
                    running -> {
                        // no pause while the file is being joined / saved: that part is quick and can't be stopped halfway
                        if (d.phase.isEmpty()) {
                            DlIconButton(
                                if (d.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                if (d.paused) "Resume" else "Pause", size = 32.dp, tint = c.primary
                            ) { if (d.paused) Downloads.resume(d) else Downloads.pause(d) }
                        }
                        DlIconButton(Icons.Rounded.Close, "Cancel", size = 32.dp, tint = c.muted) { Downloads.cancel(d) }
                    }
                    queued -> DlIconButton(Icons.Rounded.Close, "Remove", size = 32.dp, tint = c.muted) {
                        Downloads.remove(ctx, d, deleteFile = false)
                    }
                    else -> {
                        if (failed || cancelled) {
                            DlIconButton(Icons.Rounded.Refresh, "Retry", size = 32.dp, tint = c.muted) { Downloads.retry(ctx, d) }
                        }
                        Box {
                            DlIconButton(Icons.Rounded.MoreVert, "More options", size = 32.dp, tint = c.muted) { menu = true }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (done) {
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                        onClick = {
                                            menu = false
                                            try {
                                                val share = Intent(Intent.ACTION_SEND).apply {
                                                    type = if (d.audioOnly) "audio/*" else "video/*"
                                                    putExtra(Intent.EXTRA_STREAM, Downloads.uriOf(d))
                                                    putExtra(Intent.EXTRA_TITLE, d.title)
                                                    putExtra(Intent.EXTRA_SUBJECT, d.title)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                ctx.startActivity(Intent.createChooser(share, "Share video"))
                                            } catch (e: Exception) {
                                                toast(ctx, "Couldn't share this download.")
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Play") },
                                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                                        onClick = { menu = false; play() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = Danger) },
                                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Danger) },
                                        onClick = { menu = false; confirm = true }
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
