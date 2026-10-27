package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.VideoItem

/**
 * Home's Shorts shelf, shown after the first few videos: the Shorts logo and title, then the Shorts as tall cards,
 * two per row. Tapping a card goes straight to the Shorts tab, on that Short.
 */
@Composable
fun HomeShortsShelf(shorts: List<VideoItem>) {
    val nav = LocalNav.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(ShortsLogoVector, contentDescription = null, tint = Color(0xFFFF0033), modifier = Modifier.size(30.dp))
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
        shorts.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { v ->
                    ShortCard(v, Modifier.weight(1f)) {
                        // the Shorts tab scrolls to this Short as soon as it is on screen
                        ShortsUi.openId = v.id
                        nav.selectTab(Route.Shorts)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ShortCard(v: VideoItem, modifier: Modifier, onClick: () -> Unit) {
    val title = cleanShortTitle(v.title).ifEmpty { v.channelTitle }
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(22.dp))
            .background(Hairline)
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
    ) {
        VideoThumb(v.id, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color(0x14000000),
                        1f to Color(0xCC000000)
                    )
                )
        )
        if (title.isNotEmpty()) {
            Text(
                title,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 14.dp)
            )
        }
    }
}
