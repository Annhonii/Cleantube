package com.cleantube.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.Session
import kotlin.math.roundToInt

// Collapsing top header (Material You style): a floating rounded card below the status bar.
//
//  expanded:  ( card ................................... Hi, Davesh!  (photo, big) )
//             (   ( search pill, full width )                                        )
//  collapsed: ( card: ( search pill, up to the photo )                (photo, small) )
//
// The card colour is the theme's soft accent, which already follows the playing video (see Theme.kt), so the
// header changes colour together with the rest of the app. Everything is driven by ONE number (0 = expanded,
// 1 = collapsed) that follows the list's scroll through a spring, and the size / position of every part is
// worked out from it in the layout phase: no recomposition per frame, no jumps.

private val CardMargin = 12.dp      // space between the card and the screen's left / right edge
private val CardTop = 8.dp          // space between the status bar and the card
private val InnerPad = 14.dp        // space between the card's edge and what is inside
private val Gap = 10.dp
private val AvatarBig = 56.dp
private val AvatarSmall = 40.dp
private val SearchBig = 52.dp
private val SearchSmall = 44.dp
private val InnerVBig = 12.dp
private val CardBig = InnerVBig + AvatarBig + InnerVBig + SearchBig + InnerVBig   // 144dp
private val CardSmall = 56.dp
private val BelowCard = 4.dp
private val FadeBelow = 14.dp       // soft fade where the feed slides under the header

/** Full (expanded) height of [CollapsingHeader]: use it as the top padding of the list underneath. */
@Composable
fun collapsingHeaderHeight(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + CardTop + CardBig + BelowCard

private fun li(a: Int, b: Int, t: Float) = (a + (b - a) * t).roundToInt()
private fun lf(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Sticky header drawn over a list. Pass the list's [listState]; scrolling the content up collapses the header,
 * scrolling back to the top expands it again. The list needs [collapsingHeaderHeight] of top padding.
 */
@Composable
fun CollapsingHeader(listState: LazyListState, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val density = LocalDensity.current
    val rangePx = with(density) { (CardBig - CardSmall).toPx() }
    val sbPx = WindowInsets.statusBars.getTop(density)

    val raw = remember(listState, rangePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / rangePx).coerceIn(0f, 1f)
        }
    }
    // spring smoothing: the header glides to where the scroll wants it instead of snapping with every pixel
    val fState = animateFloatAsState(raw.value, spring(dampingRatio = 1f, stiffness = 380f), label = "headerCollapse")

    val firstName = Session.name.trim().substringBefore(' ').ifEmpty { "Davesh" }
    val thumb = Session.thumb

    Layout(
        content = {
            // 0: greeting (fades out and slides toward the photo while collapsing)
            Text(
                "Hi, $firstName!",
                color = OnAccentSoft, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.graphicsLayer {
                    val p = fState.value
                    alpha = (1f - p * 2.4f).coerceIn(0f, 1f)
                    translationX = 28.dp.toPx() * p
                }
            )
            // 1: profile photo (opens Settings)
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Card)
                    .bouncyClickable(pressedScale = 0.9f, onClick = { if (nav.current != Route.Settings) nav.push(Route.Settings) }),
                contentAlignment = Alignment.Center
            ) {
                if (thumb.isNotEmpty()) AppImage(thumb, Modifier.fillMaxSize(), CircleShape)
                else Icon(Icons.Rounded.PersonOutline, contentDescription = "Account", tint = TextPrimary, modifier = Modifier.size(26.dp))
            }
            // 2: search pill (opens the search page)
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Bg)
                    .bouncyClickable(pressedScale = 0.98f, onClick = { nav.push(Route.Search()) })
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = TextPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Search videos", color = TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val p = fState.value
                // screen colour behind the status bar and around the card, so the feed never shows through there
                drawRect(Bg)
                drawRect(
                    Brush.verticalGradient(listOf(Bg, Color.Transparent), startY = size.height, endY = size.height + FadeBelow.toPx()),
                    topLeft = Offset(0f, size.height), size = Size(size.width, FadeBelow.toPx())
                )
                // the card: all four corners rounded
                val cardH = lf(CardBig.toPx(), CardSmall.toPx(), p)
                val r = lf(32.dp.toPx(), 28.dp.toPx(), p)
                drawRoundRect(
                    AccentSoft,
                    Offset(CardMargin.toPx(), sbPx + CardTop.toPx()),
                    Size(size.width - 2 * CardMargin.toPx(), cardH),
                    CornerRadius(r, r)
                )
            }
    ) { measurables, c ->
        val p = fState.value.coerceIn(0f, 1f)
        val w = c.maxWidth
        val innerX = CardMargin.roundToPx() + InnerPad.roundToPx()   // left / right edge of what is inside the card
        val gap = Gap.roundToPx()
        val cardTop = sbPx + CardTop.roundToPx()
        val cardBig = CardBig.roundToPx()
        val cardSmall = CardSmall.roundToPx()

        // photo: big -> small, always at the card's right edge
        val aBig = AvatarBig.roundToPx()
        val aSm = AvatarSmall.roundToPx()
        val a = li(aBig, aSm, p)
        val avatar = measurables[1].measure(Constraints.fixed(a, a))
        val avatarX = w - innerX - a
        val centerBig = cardTop + InnerVBig.roundToPx() + aBig / 2
        val centerSm = cardTop + cardSmall / 2
        val cy = li(centerBig, centerSm, p)

        // search pill: full card width below the photo -> from the card's left edge up to the (small) photo
        val sBig = SearchBig.roundToPx()
        val sSm = SearchSmall.roundToPx()
        val searchH = li(sBig, sSm, p)
        val widthBig = w - 2 * innerX
        val widthSm = (w - innerX - aSm) - gap - innerX
        val searchW = li(widthBig, widthSm, p)
        val topBig = cardTop + InnerVBig.roundToPx() + aBig + InnerVBig.roundToPx()
        val topSm = centerSm - sSm / 2
        val searchTop = li(topBig, topSm, p)
        val search = measurables[2].measure(Constraints.fixed(searchW, searchH))

        // greeting: just left of the photo
        val greeting = measurables[0].measure(Constraints(maxWidth = (avatarX - gap - innerX).coerceAtLeast(0)))

        val h = cardTop + li(cardBig, cardSmall, p) + BelowCard.roundToPx()
        layout(w, h) {
            greeting.place(avatarX - gap - greeting.width, cy - greeting.height / 2)
            avatar.place(avatarX, cy - a / 2)
            search.place(innerX, searchTop)
        }
    }
}
