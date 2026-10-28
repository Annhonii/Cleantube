package com.cleantube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.PagerState
import kotlin.math.floor

data class MorphingTab(
    val label: String,
    val icon: ImageVector
)

private val MorphSpring = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * Shared-coordinate top tab bar with a morphing circular/pill indicator.
 *
 * The first tab is a standalone circle; remaining tabs sit inside a shared
 * background. Supply [pagerState] to interpolate the indicator during swipes.
 */
@Composable
fun MorphingTopTabBar(
    tabs: List<MorphingTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState? = null,
    activeColor: Color = Color(0xFF171717),
    inactiveContainer: Color = Color(0xFF252525),
    activeContentColor: Color = Color.White,
    inactiveContentColor: Color = Color(0xFFBDBDBD),
    height: Dp = 48.dp,
    gap: Dp = 8.dp
) {
    if (tabs.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.CenterStart
    ) {
        val circleWidth = height
        val mainWidth = (maxWidth - circleWidth - gap).coerceAtLeast(0.dp)
        val mainTabs = (tabs.size - 1).coerceAtLeast(1)
        val slotWidth = mainWidth / mainTabs

        val pagerPosition = pagerState?.let {
            (it.currentPage + it.currentPageOffsetFraction)
                .coerceIn(0f, (tabs.size - 1).toFloat())
        }
        val position = pagerPosition ?: selectedIndex
            .coerceIn(0, tabs.lastIndex).toFloat()

        val lowerIndex = floor(position).toInt().coerceIn(0, tabs.lastIndex)
        val upperIndex = (lowerIndex + 1).coerceAtMost(tabs.lastIndex)
        val fraction = position - lowerIndex

        fun tabCenter(index: Int): Dp =
            if (index == 0) circleWidth / 2
            else circleWidth + gap + slotWidth * (index - 1) + slotWidth / 2

        fun tabWidth(index: Int): Dp {
            if (index == 0) return circleWidth
            val available = (slotWidth - 4.dp).coerceAtLeast(0.dp)
            val desired = 56.dp + 7.2.dp * tabs[index].label.length
            return minOf(desired, available)
        }

        val safeSelected = selectedIndex.coerceIn(0, tabs.lastIndex)
        val targetWidth = if (pagerState == null) tabWidth(safeSelected)
        else androidx.compose.ui.unit.lerp(
            tabWidth(lowerIndex), tabWidth(upperIndex), fraction
        )

        val targetCenter = if (pagerState == null) tabCenter(safeSelected)
        else androidx.compose.ui.unit.lerp(
            tabCenter(lowerIndex), tabCenter(upperIndex), fraction
        )

        val targetOffset = (targetCenter - targetWidth / 2)
            .coerceIn(0.dp, (maxWidth - targetWidth).coerceAtLeast(0.dp))

        val indicatorX by animateDpAsState(targetOffset, MorphSpring, label = "indicatorOffset")
        val indicatorWidth by animateDpAsState(targetWidth, MorphSpring, label = "indicatorWidth")

        Box(Modifier.fillMaxWidth().height(height)) {
            if (tabs.size > 1) {
                Box(
                    Modifier.offset(x = circleWidth + gap)
                        .width(mainWidth).height(height)
                        .clip(CircleShape).background(inactiveContainer)
                )
            }

            Box(
                Modifier.offset(x = indicatorX)
                    .width(indicatorWidth).height(height)
                    .clip(CircleShape).background(activeColor)
            )

            Box(
                Modifier.size(circleWidth).clip(CircleShape)
                    .clickable { onSelect(0) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tabs[0].icon,
                    contentDescription = tabs[0].label,
                    tint = if (safeSelected == 0) activeContentColor else inactiveContentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Row(
                Modifier.offset(x = circleWidth + gap).width(mainWidth).height(height),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.drop(1).forEachIndexed { index, tab ->
                    val tabIndex = index + 1
                    val selected = safeSelected == tabIndex
                    Box(
                        Modifier.weight(1f).height(height).clip(CircleShape)
                            .clickable { onSelect(tabIndex) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) activeContentColor else inactiveContentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        text = tab.label,
                                        color = activeContentColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
