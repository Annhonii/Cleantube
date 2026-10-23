package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.VideoItem
import com.cleantube.app.data.formatDuration

/** The three-dot menu of the compact rows (save, share, go to channel, remove). */
@Composable
fun VideoMenuButton(v: VideoItem, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(Modifier.size(34.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextMuted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Save to playlist") }, onClick = { menu = false; saveDialog = true })
            DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; shareVideo(ctx, v.id) })
            if (v.channelId.isNotEmpty()) {
                DropdownMenuItem(text = { Text("Go to channel") }, onClick = { menu = false; nav.push(Route.Channel(v.channelId)) })
            }
            if (onRemove != null) {
                DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; onRemove() })
            }
        }
    }
    if (saveDialog) SaveToPlaylistDialog(v) { saveDialog = false }
}

/** Small thumbnail left, text right. Flat: no card behind it. */
@Composable
fun CompactRow(v: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    val thumbC = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    Row(
        modifier
            .fillMaxWidth()
            .prefetchOnPress(v.id)
            .plainClickable { thumbC[0]?.takeIf { it.isAttached }?.let { VideoHost.rememberOrigin(it.boundsInWindow()) }; onClick() }
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).onGloballyPositioned { thumbC[0] = it }.clip(RoundedCornerShape(12.dp)).background(Hairline)) {
            VideoThumb(v.id, Modifier.matchParentSize())
            if (v.live) {
                Badge("LIVE", Color.Black, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            } else if (v.durationSec > 0) {
                Badge(formatDuration(v.durationSec), Color(0xB3000000), Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 2.dp)) {
            Text(v.title, color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val meta = listOf(v.channelTitle, videoMeta(v)).filter { it.isNotEmpty() }.joinToString(" • ")
            Text(meta, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        VideoMenuButton(v, onRemove = onRemove)
    }
}

/** Feed item: one clean row per video (the same layout everywhere). */
@Composable
fun BentoFeedItem(all: List<VideoItem>, index: Int, onOpen: (VideoItem) -> Unit) {
    val v = all.getOrNull(index) ?: return
    VideoRow(v, onClick = { onOpen(v) })
}

/** Trending: rank number next to a compact row. */
@Composable
fun CompactRankRow(all: List<VideoItem>, index: Int, onOpen: (VideoItem) -> Unit) {
    val v = all.getOrNull(index) ?: return
    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${index + 1}", color = Tertiary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.width(30.dp)
        )
        CompactRow(v, onClick = { onOpen(v) }, modifier = Modifier.weight(1f))
    }
}

/** Round icon-only button of the video page. */
@Composable
fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Box(
        modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(if (active) Accent else SecondaryContainer)
            .bouncyClickable(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = if (active) OnAccent else OnSecondaryContainer, modifier = Modifier.size(19.dp))
    }
}
