package com.cleantube.app.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.Api
import com.cleantube.app.data.Paged
import com.cleantube.app.data.PlaylistItem
import com.cleantube.app.data.VideoItem
import com.cleantube.app.data.formatDuration

private val HeaderBase = Color(0xFF30343C)
private val OnHeader = Color.White
private val OnHeaderMuted = Color(0xCCFFFFFF)

/**
 * A playlist: a big cover with the title, owner and facts on a blurred backdrop, a Play all button with Share and a
 * three-dot menu (Download all, Open in YouTube), and the videos as compact rows below.
 */
@Composable
fun PlaylistScreen(route: Route.Playlist) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember(route.id) { mutableStateOf<PlaylistItem?>(null) }
    var downloadAll by remember(route.id) { mutableStateOf(false) }

    LaunchedEffect(route.id) {
        try {
            info = Api.playlist(route.id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // the title and the videos below are enough
        }
    }

    val paged = remember(route.id) {
        Paged<VideoItem>(scope) { token -> Api.playlistVideos(route.id, token) }
    }

    val title = info?.title?.takeIf { it.isNotEmpty() } ?: route.title.ifEmpty { "Playlist" }
    val videos = paged.items
    val count = info?.countText?.takeIf { it.isNotEmpty() }
        ?: if (paged.finished && videos.isNotEmpty()) "${videos.size} video${if (videos.size == 1) "" else "s"}" else ""
    val meta = listOf("Playlist", count, info?.viewsText.orEmpty(), info?.updatedText.orEmpty())
        .filter { it.isNotEmpty() }.joinToString(" • ")
    val link = "https://www.youtube.com/playlist?list=${route.id}"
    // the account's own lists (liked videos, watch later) have no public page to share
    val shareable = route.id != "LL" && route.id != "WL"

    Box(Modifier.fillMaxSize()) {
        PagedColumn(
            paged,
            emptyText = "This playlist is empty or private.",
            contentPadding = PaddingValues(bottom = listBottomPad()),
            refreshIndicatorTop = 72.dp,
            header = {
                item(key = "playlist-header") {
                    PlaylistHeader(
                        title = title,
                        coverUrl = info?.thumb.orEmpty(),
                        firstVideoId = videos.firstOrNull()?.id.orEmpty(),
                        owner = info?.channelTitle.orEmpty(),
                        meta = meta,
                        shareable = shareable,
                        onPlayAll = {
                            if (videos.isEmpty()) Toast.makeText(ctx, "The playlist is still loading.", Toast.LENGTH_SHORT).show()
                            else VideoHost.playQueue(videos, 0)
                        },
                        onShare = { shareText(ctx, link) },
                        onDownloadAll = { downloadAll = true },
                        onOpenYouTube = { openUrl(ctx, link) }
                    )
                }
            }
        ) { v ->
            PlaylistVideoRow(v, onClick = {
                // tapping a row plays the playlist from that video on
                VideoHost.playQueue(paged.items, paged.items.indexOfFirst { it.id == v.id }.coerceAtLeast(0))
            })
        }

        // stays on top while the list scrolls
        Box(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 14.dp, top = 12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x80000000))
                .bouncyClickable(pressedScale = 0.88f, onClick = { nav.back() }),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }

    if (downloadAll) PlaylistDownloadDialog(route.id, title) { downloadAll = false }
}

@Composable
private fun PlaylistHeader(
    title: String,
    coverUrl: String,
    firstVideoId: String,
    owner: String,
    meta: String,
    shareable: Boolean,
    onPlayAll: () -> Unit,
    onShare: () -> Unit,
    onDownloadAll: () -> Unit,
    onOpenYouTube: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    // the playlist's own cover when YouTube gave one, else the first video's picture
    val backdrop = coverUrl.ifEmpty { if (firstVideoId.isNotEmpty()) "https://i.ytimg.com/vi/$firstVideoId/mqdefault.jpg" else "" }

    Box(Modifier.fillMaxWidth().background(HeaderBase)) {
        // blur needs Android 12; below that the header is just the dark tint
        if (backdrop.isNotEmpty() && Build.VERSION.SDK_INT >= 31) {
            AppImage(backdrop, Modifier.matchParentSize().blur(40.dp), RectangleShape)
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color(0x80000000), Color(0x99000000), Bg)))
        )

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 68.dp, start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val heroShape = RoundedCornerShape(16.dp)
            if (coverUrl.isNotEmpty()) {
                AppImage(coverUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f), heroShape)
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(heroShape).background(Hairline)) {
                    if (firstVideoId.isNotEmpty()) VideoThumb(firstVideoId, Modifier.fillMaxSize())
                }
            }

            Text(
                title,
                color = OnHeader,
                fontSize = 28.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (owner.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                        Text(owner.first().uppercase(), color = OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(owner, color = OnHeader, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (meta.isNotEmpty()) {
                Text(meta, color = OnHeaderMuted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .bouncyClickable(pressedScale = 0.96f, onClick = onPlayAll),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play all", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                if (shareable) HeaderButton(Icons.Rounded.Share, "Share", onShare)
                Box {
                    HeaderButton(Icons.Rounded.MoreVert, "More") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Download all") },
                            leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                            onClick = { menu = false; onDownloadAll() }
                        )
                        if (shareable) {
                            DropdownMenuItem(
                                text = { Text("Open in YouTube") },
                                onClick = { menu = false; onOpenYouTube() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color(0x33FFFFFF))
            .bouncyClickable(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = OnHeader, modifier = Modifier.size(24.dp))
    }
}

/** Compact row: thumbnail with the length on the left, title / channel / views on the right, menu at the end. */
@Composable
private fun PlaylistVideoRow(v: VideoItem, onClick: () -> Unit) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var save by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(false) }
    val thumbC = remember { arrayOfNulls<LayoutCoordinates>(1) }

    val vt = rememberVideoTint(v.id)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(vt.container)
            .prefetchOnPress(v.id)
            .plainClickable {
                thumbC[0]?.takeIf { it.isAttached }?.let { VideoHost.rememberOrigin(it.boundsInWindow()) }
                onClick()
            }
            .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .onGloballyPositioned { thumbC[0] = it }
                .clip(RoundedCornerShape(12.dp))
                .background(Hairline)
        ) {
            VideoThumb(v.id, Modifier.fillMaxSize())
            if (v.live) {
                Badge("LIVE", Color(0xFFE53935), Modifier.align(Alignment.BottomEnd).padding(6.dp))
            } else if (v.durationSec > 0) {
                Text(
                    formatDuration(v.durationSec),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                v.title,
                color = vt.onContainer,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (v.channelTitle.isNotEmpty()) {
                Text(
                    v.channelTitle,
                    color = vt.muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            val meta = videoMeta(v)
            if (meta.isNotEmpty()) {
                Text(meta, color = vt.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = vt.muted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; download = true })
                DropdownMenuItem(text = { Text("Save to playlist") }, onClick = { menu = false; save = true })
                DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; shareVideo(ctx, v.id) })
                if (v.channelId.isNotEmpty()) {
                    DropdownMenuItem(text = { Text("Go to channel") }, onClick = { menu = false; nav.push(Route.Channel(v.channelId)) })
                }
            }
        }
    }
    if (save) SaveToPlaylistDialog(v) { save = false }
    if (download) DownloadDialog(v) { download = false }
}
