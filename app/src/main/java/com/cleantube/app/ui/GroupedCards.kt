package com.cleantube.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Position of a card inside its current visual group. Recomputed from the displayed list. */
internal enum class GroupedCardPosition { FIRST, MIDDLE, LAST, SINGLE }

internal fun groupedCardPosition(index: Int, count: Int): GroupedCardPosition = when {
    count <= 1 -> GroupedCardPosition.SINGLE
    index <= 0 -> GroupedCardPosition.FIRST
    index >= count - 1 -> GroupedCardPosition.LAST
    else -> GroupedCardPosition.MIDDLE
}

/** AOSP-style joined cards: only the group's outside corners are rounded. */
internal fun groupedCardShape(
    position: GroupedCardPosition,
    outerRadius: Dp = 24.dp
): RoundedCornerShape = when (position) {
    GroupedCardPosition.FIRST -> RoundedCornerShape(
        topStart = outerRadius, topEnd = outerRadius, bottomStart = 0.dp, bottomEnd = 0.dp
    )
    GroupedCardPosition.MIDDLE -> RoundedCornerShape(0.dp)
    GroupedCardPosition.LAST -> RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp, bottomStart = outerRadius, bottomEnd = outerRadius
    )
    GroupedCardPosition.SINGLE -> RoundedCornerShape(outerRadius)
}

internal fun groupedCardShape(index: Int, count: Int, outerRadius: Dp = 24.dp): RoundedCornerShape =
    groupedCardShape(groupedCardPosition(index, count), outerRadius)
