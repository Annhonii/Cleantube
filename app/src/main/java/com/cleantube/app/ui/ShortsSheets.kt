@file:OptIn(ExperimentalMaterial3Api::class)

package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.cleantube.app.data.Api
import com.cleantube.app.data.Comment
import com.cleantube.app.data.Paged
import com.cleantube.app.data.VideoItem
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants

private val speeds = listOf(
    "0.25x" to PlayerConstants.PlaybackRate.RATE_0_25,
    "0.5x" to PlayerConstants.PlaybackRate.RATE_0_5,
    "1x" to PlayerConstants.PlaybackRate.RATE_1,
    "1.5x" to PlayerConstants.PlaybackRate.RATE_1_5,
    "2x" to PlayerConstants.PlaybackRate.RATE_2
)

@Composable
private fun MenuLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String = "", onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The Shorts menu. Long press opens the quick version (speed, captions, report);
 * the three-dot button opens everything (also description, audio source, download, save, link, open in YouTube).
 */
@Composable
internal fun ShortsMenuSheet(
    req: MenuRequest,
    meta: ShortMeta?,
    player: ShortsPlayer,
    captionsOn: Boolean,
    onCaptions: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onSave: () -> Unit,
    onOpenChannel: () -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val v = req.video
    val description = meta?.details?.description.orEmpty()
    var showDescription by remember { mutableStateOf(false) }
    val channelName = meta?.channel?.title.orEmpty().ifEmpty { v.channelTitle }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Card, contentColor = TextPrimary) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(v.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))

            MenuLabel("Playback speed")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                speeds.forEach { (label, rate) ->
                    Pill(label, onClick = { player.setSpeedTo(rate) }, active = player.speed == rate)
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.ClosedCaption, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Captions", color = TextPrimary, fontSize = 15.sp)
                    Text("Show YouTube's captions when a Short has them", color = TextMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = captionsOn,
                    onCheckedChange = onCaptions,
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White)
                )
            }

            if (req.full) {
                MenuRow(
                    Icons.Rounded.Info,
                    "Description",
                    if (showDescription) "" else "Tap to read"
                ) { showDescription = !showDescription }
                if (showDescription) {
                    Text(
                        description.ifEmpty { "No description." },
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 44.dp, bottom = 8.dp)
                    )
                }
                MenuRow(Icons.Rounded.GraphicEq, "Audio source", "Original audio - $channelName") {
                    onDismiss()
                    onOpenChannel()
                }
                MenuRow(Icons.Rounded.Download, "Download") {
                    onDismiss()
                    onDownload()
                }
                MenuRow(Icons.Rounded.Add, "Save to playlist") {
                    onDismiss()
                    onSave()
                }
                MenuRow(Icons.Rounded.ContentCopy, "Copy link") {
                    copyToClipboard(ctx, "Short", "https://youtube.com/shorts/${v.id}")
                    Toast.makeText(ctx, "Link copied", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
                MenuRow(Icons.Rounded.SmartDisplay, "Open in YouTube") {
                    onDismiss()
                    openShortInYouTube(ctx, v.id)
                }
            }

            MenuRow(Icons.Rounded.Flag, "Report", "Opens the Short on YouTube, where reporting lives") {
                onDismiss()
                openShortInYouTube(ctx, v.id)
            }
        }
    }
}

@Composable
private fun CommentRow(c: Comment) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppImage(url = c.avatar, modifier = Modifier.size(34.dp), shape = CircleShape)
        Column(Modifier.weight(1f)) {
            Text(
                listOf(c.author, c.publishedAt).filter { it.isNotEmpty() }.joinToString("  "),
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(c.text, color = TextPrimary, fontSize = 14.sp)
            val extra = listOfNotNull(
                c.likesText.takeIf { it.isNotEmpty() }?.let { "$it likes" },
                c.replies.takeIf { it > 0 }?.let { "$it replies" }
            ).joinToString("   ")
            if (extra.isNotEmpty()) Text(extra, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Comment section as a bottom sheet (the video keeps playing behind it). */
@Composable
internal fun CommentsSheet(
    video: VideoItem,
    hasDetails: Boolean,
    countText: String,
    onOpenInYouTube: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val paged = remember(video.id) { Paged(scope) { token -> Api.comments(video.id, token) } }
    // The comment token comes with the video details, so make sure those were fetched first.
    var ready by remember(video.id) { mutableStateOf(hasDetails) }
    LaunchedEffect(video.id) {
        if (!ready) {
            tryOrNull { Api.videoDetails(video.id) }
            ready = true
        }
    }
    val height = (LocalConfiguration.current.screenHeightDp * 0.7f).dp

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Card, contentColor = TextPrimary) {
        Column(Modifier.fillMaxWidth().height(height).navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (countText.isEmpty()) "Comments" else "Comments  $countText",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }
            Box(Modifier.weight(1f)) {
                if (ready) {
                    PagedColumn(
                        paged,
                        emptyText = "No comments yet.",
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) { c -> CommentRow(c) }
                } else {
                    Loading()
                }
            }
            // Writing comments needs a signed-in YouTube session, so the bar hands over to YouTube.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(CircleShape)
                    .background(Hairline)
                    .clickable(onClick = onOpenInYouTube)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Add a comment on YouTube...", color = TextMuted, fontSize = 14.sp)
            }
        }
    }
}
