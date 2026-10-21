package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cleantube.app.data.VideoItem
import com.cleantube.app.data.formatDuration

// New card family used by the redesign:
//  - HeroCard:   big full-bleed card, title on the picture, mint play button
//  - HalfTile:   two of these sit side by side (bento grid)
//  - CompactRow: small thumbnail on the left, text on the right (Up next, lists)
//  - BentoFeedItem: puts them in a repeating rhythm: hero, 2 tiles, 2 tiles, hero ...

/** The three-dot menu shared by every card (save, share, go to channel, remove). */
@Composable
fun VideoMenuButton(v: VideoItem, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null, onDark: Boolean = false) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (onDark) Color(0x66000000) else Color.Transparent)
                .clickable { menu = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = if (onDark) Color.White else TextMuted, modifier = Modifier.size(20.dp))
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

@Composable
private fun rememberAvatar(v: VideoItem): String {
    val avatar by produceState(ChannelAvatars.known(v.channelId, v.channelThumb), v.channelId, v.channelThumb) {
        if (value.isEmpty() && v.channelId.isNotEmpty()) value = ChannelAvatars.load(v.channelId)
    }
    return avatar
}

@Composable
private fun Avatar(name: String, url: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(Accent),
        contentAlignment = Alignment.Center
    ) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "•", color = OnAccent, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold)
        if (url.isNotEmpty()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
        }
    }
}

/** Big card: the title sits on the picture, a mint play button on the corner. */
@Composable
fun HeroCard(v: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val avatar = rememberAvatar(v)
    val shape = RoundedCornerShape(34.dp)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .aspectRatio(4f / 4.6f)
            .clip(shape)
            .background(Hairline)
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick)
    ) {
        VideoThumb(v.id, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0x66000000),
                        0.35f to Color.Transparent,
                        0.62f to Color(0x33000000),
                        1.0f to Color(0xEE000000)
                    )
                )
        )
        // top: channel chip + menu
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickable(enabled = v.channelId.isNotEmpty()) { nav.push(Route.Channel(v.channelId)) }
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(v.channelTitle, avatar, 26.dp)
                Spacer(Modifier.width(8.dp))
                Text(v.channelTitle, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            }
            Spacer(Modifier.weight(1f))
            VideoMenuButton(v, onDark = true)
        }
        // bottom: title, meta, play
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 18.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                val badge = if (v.live) "LIVE" else if (v.durationSec > 0) formatDuration(v.durationSec) else ""
                if (badge.isNotEmpty()) {
                    Text(
                        badge,
                        color = if (v.live) Color.White else OnAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (v.live) Color(0xFFFF4B4B) else Accent)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    v.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = videoMeta(v)
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(meta, color = Color(0xCCFFFFFF), fontSize = 12.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(Accent),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = OnAccent, modifier = Modifier.size(30.dp)) }
        }
    }
}

/** Half-width tile for the two-column part of the feed. */
@Composable
fun HalfTile(v: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .clip(shape)
            .background(Card)
            .border(1.dp, Hairline, shape)
            .bouncyClickable(pressedScale = 0.96f, onClick = onClick)
            .padding(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.18f).clip(RoundedCornerShape(21.dp)).background(Hairline)) {
            VideoThumb(v.id, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x80000000))))
            )
            if (v.live) {
                Badge("LIVE", Color(0xFFFF4B4B), Modifier.align(Alignment.BottomStart).padding(8.dp))
            } else if (v.durationSec > 0) {
                Badge(formatDuration(v.durationSec), Color(0xB3000000), Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    v.title, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(36.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(v.channelTitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VideoMenuButton(v, Modifier.padding(start = 2.dp))
        }
    }
}

/** Small thumbnail left, text right: dense list row. */
@Composable
fun CompactRow(v: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(shape)
            .background(Card)
            .border(1.dp, Hairline, shape)
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(138.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(18.dp)).background(Hairline)) {
            VideoThumb(v.id, Modifier.fillMaxSize())
            if (v.live) {
                Badge("LIVE", Color(0xFFFF4B4B), Modifier.align(Alignment.BottomEnd).padding(6.dp))
            } else if (v.durationSec > 0) {
                Badge(formatDuration(v.durationSec), Color(0xB3000000), Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.title, color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(v.channelTitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = videoMeta(v)
            if (meta.isNotEmpty()) Text(meta, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        VideoMenuButton(v, onRemove = onRemove)
    }
}

/**
 * One item of the feed in a repeating rhythm of five:
 * 0 = hero, 1 = two tiles (this one and the next), 2 = (shown by 1), 3 = two tiles, 4 = (shown by 3).
 */
@Composable
fun BentoFeedItem(all: List<VideoItem>, index: Int, onOpen: (VideoItem) -> Unit) {
    val v = all.getOrNull(index) ?: return
    when (index % 5) {
        0 -> HeroCard(v, onClick = { onOpen(v) })
        1, 3 -> {
            val next = all.getOrNull(index + 1)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HalfTile(v, onClick = { onOpen(v) }, modifier = Modifier.weight(1f))
                if (next != null) HalfTile(next, onClick = { onOpen(next) }, modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
            }
        }
        else -> Spacer(Modifier.height(0.dp))
    }
}

/** Round icon, label under it: used for the action row of the video page and the Library. */
@Composable
fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Column(
        modifier.width(68.dp).bouncyClickable(pressedScale = 0.9f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(if (active) Accent else Card)
                .border(1.dp, if (active) Accent else Hairline, RoundedCornerShape(21.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (active) OnAccent else TextPrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextMuted, fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Medium)
    }
}

/** Horizontal strip of topic chips; tapping one searches for it. */
@Composable
fun TopicChips(modifier: Modifier = Modifier, onPick: ((String) -> Unit)? = null) {
    val nav = LocalNav.current
    val topics = listOf("Music", "Gaming", "Live", "News", "Sports", "Podcasts", "Comedy", "Tech", "Learning", "Movies")
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onPick == null) Pill("All", onClick = {}, active = true)
        topics.forEach { t -> Pill(t, onClick = { if (onPick != null) onPick(t) else nav.push(Route.Search(t)) }) }
    }
}

/** Trending: big mint rank number next to a compact row. */
@Composable
fun CompactRankRow(all: List<VideoItem>, index: Int, onOpen: (VideoItem) -> Unit) {
    val v = all.getOrNull(index) ?: return
    Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${index + 1}", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(34.dp)
        )
        CompactRow(v, onClick = { onOpen(v) }, modifier = Modifier.weight(1f))
    }
}
