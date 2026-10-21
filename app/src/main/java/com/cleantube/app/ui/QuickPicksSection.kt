package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.cleantube.app.data.Graph
import com.cleantube.app.data.QuickPicks
import com.cleantube.app.data.Session
import com.cleantube.app.data.formatDuration

/**
 * Fetches Quick picks once per clean app start (the first time Home shows), and again when the account or the
 * subscriptions change. No timer. Pull-to-refresh on Home calls [QuickPicks.refresh] with force = true.
 */
@Composable
private fun QuickPicksAutoRefresh() {
    val subs = Graph.store.subscriptions
    LaunchedEffect(Session.loggedIn, Session.handle, subs) { QuickPicks.refresh() }
}

/**
 * "Quick picks" carousel at the top of Home: the newest videos of your subscribed channels that you haven't
 * watched yet, as big cards with the neighbours peeking in (Material 3 multi-browse carousel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPicksSection() {
    val nav = LocalNav.current
    QuickPicksAutoRefresh()
    val cards by remember { derivedStateOf { QuickPicks.unwatched(QuickPicks.pool, Graph.store.history) } }
    val loading = QuickPicks.loading
    if (cards.isEmpty() && !loading) return

    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(
            "Quick picks",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardW = (maxWidth * 0.8f).coerceIn(232.dp, 340.dp)
            val cardH = 250.dp
            if (cards.isEmpty()) {
                // first load: grey placeholders in the same shape
                Row(Modifier.padding(horizontal = 16.dp)) {
                    Box(Modifier.width(cardW).height(cardH).clip(RoundedCornerShape(28.dp)).background(Hairline).shimmer())
                }
            } else {
                HorizontalMultiBrowseCarousel(
                    state = rememberCarouselState { cards.size },
                    preferredItemWidth = cardW,
                    itemSpacing = 8.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth().height(cardH)
                ) { index ->
                    val v = cards[index]
                    val fresh = v.id in QuickPicks.freshIds
                    Box(
                        Modifier
                            .fillMaxSize()
                            .maskClip(RoundedCornerShape(28.dp))
                            .background(Hairline)
                            .clickable { nav.push(Route.Video(v.id, v)) }
                    ) {
                        VideoThumb(v.id, Modifier.fillMaxSize())
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        0.5f to Color(0x14000000),
                                        1f to Color(0xD6000000)
                                    )
                                )
                        )
                        if (fresh) {
                            Badge("NEW", Color(0xFFE53935), Modifier.align(Alignment.TopStart).padding(14.dp))
                        }
                        if (v.live) {
                            Badge("LIVE", Color.Black, Modifier.align(Alignment.TopEnd).padding(14.dp))
                        } else if (v.durationSec > 0) {
                            Badge(formatDuration(v.durationSec), Color(0xB3000000), Modifier.align(Alignment.TopEnd).padding(14.dp))
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 16.dp)) {
                            Text(
                                v.title,
                                color = Color.White,
                                fontSize = 18.sp,
                                lineHeight = 23.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            val meta = listOf(v.channelTitle, v.ageText.ifEmpty { com.cleantube.app.data.timeAgo(v.publishedAt) })
                                .filter { it.isNotEmpty() }.joinToString(" • ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta,
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
