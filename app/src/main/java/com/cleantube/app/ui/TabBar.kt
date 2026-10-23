package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Height of the floating tab bar + the gap under it (the mini player sits above this). */
val TabBarSpace = 88.dp

/**
 * ArchiveTune-style floating bottom bar: one rounded surface with an icon + label per tab; the open tab gets a
 * pill behind its icon and a bold label. Slides away while a video is open full size, in fullscreen, or under a sheet.
 */
@Composable
fun TabBar(nav: Nav, tabs: List<TabSpec>, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(140))
    ) {
        Box(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val shape = RoundedCornerShape(30.dp)
            Row(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                    .clip(shape)
                    .background(SurfaceHighest)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (nav.current != Route.Search()) nav.push(Route.Search())
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // the little red YouTube-style logo
                Box(
                    Modifier.size(width = 38.dp, height = 28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE53935)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text(
                    "Search", color = TextMuted, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 16.dp)
                )
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = TextPrimary, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun TabBarItem(t: TabSpec, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pillW by animateDpAsState(if (selected) 64.dp else 28.dp, spring(dampingRatio = 0.8f, stiffness = 500f), label = "pillW")
    val pillA by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "pillA")
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement_Center
    ) {
        Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(pillW, 32.dp)
                    .graphicsLayer { alpha = pillA }
                    .clip(RoundedCornerShape(16.dp))
                    .background(SecondaryContainer)
            )
            Icon(
                t.icon, contentDescription = t.label,
                tint = if (selected) OnSecondaryContainer else TextMuted,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            t.label,
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) TextPrimary else TextMuted
        )
    }
}

private val Arrangement_Center = androidx.compose.foundation.layout.Arrangement.Center
