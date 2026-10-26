package com.cleantube.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.VideoItem

/**
 * The page under the player for a DOWNLOADED video (the counterpart of [VideoScreen]). Everything here comes from the
 * download itself, so it opens instantly and never waits for, or fails because of, the network: no video details, no
 * comments, no "up next", no history / recommendation calls.
 */
@Composable
fun LocalVideoPage(id: String, lv: LocalVideo) {
    // the player's settings card is the only panel that can be open on top of this page
    BackHandler(enabled = PlayerSettingsHost.open) { PlayerSettingsHost.open = false }
    LaunchedEffect(id) { PlayerSettingsHost.open = false }
    LaunchedEffect(PlayerSettingsHost.open) { VideoHost.commentsOpen = PlayerSettingsHost.open }
    DisposableEffect(Unit) { onDispose { VideoHost.commentsOpen = false } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp)
    ) {
        Text(
            lv.title.ifEmpty { "Downloaded video" },
            color = TextPrimary,
            fontSize = 19.sp,
            lineHeight = 25.sp,
            letterSpacing = (-0.2).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        if (lv.details.isNotEmpty()) {
            Text(lv.details, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Row(
            Modifier
                .padding(top = 16.dp)
                .clip(CircleShape)
                .background(SecondaryContainer)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = OnSecondaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Saved on this device, plays without internet", color = OnSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // the same video as a stream (comments, up next, other qualities); the downloaded file stays as it is
            Pill(
                "Watch online", icon = Icons.Rounded.Public,
                onClick = { VideoHost.open(id, VideoItem(id = id, title = lv.title)) }
            )
        }
    }
}
