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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

// Collapsing top header (Material You style): a flat full-width bar, no card.
// One card for the whole app, drawn above the tab pager (see App.kt): on Home it collapses with the feed's scroll,
// on Shorts it floats over the video already collapsed, and swiping / tapping between the two shrinks or grows it
// in step with the page movement. On the other tabs it slides away.
//
//  expanded:  ( card ................................... Hi, Davesh!  (photo, big) )
//             (   ( search pill, full width )                                        )
//  collapsed: ( card: ( search pill, up to the photo )                (photo, small) )
//
// The card colour is the theme's soft accent, which already follows the playing video (see Theme.kt), so the
// header changes colour together with the rest of the app. Everything is driven by ONE number (0 = expanded,
// 1 = collapsed) that follows the list's scroll through a spring, and the size / position of every part is
// worked out from it in the layout phase: no recomposition per frame, no jumps.

private val CardMargin = 0.dp      // space between the card and the screen's left / right edge
private val CardTop = 8.dp          // space between the status bar and the card
private val InnerPad = 16.dp        // space between the card's edge and what is inside
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

/** Scroll of the Home feed as 0 (top) .. 1 (fully scrolled past the card's growth); written by [TrackHeaderScroll]. */
object HeaderScroll {
    var raw by androidx.compose.runtime.mutableFloatStateOf(0f)
}

/** Reports a list's scroll to the header. Call it from the screen whose feed the header should follow. */
@Composable
fun TrackHeaderScroll(listState: LazyListState) {
    val density = LocalDensity.current
    val rangePx = with(density) { (CardBig - CardSmall).toPx() }
    androidx.compose.runtime.LaunchedEffect(listState, rangePx) {
        androidx.compose.runtime.snapshotFlow {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / rangePx).coerceIn(0f, 1f)
        }.collect { HeaderScroll.raw = it }
    }
}

/** How visible the header is for a pager position: 1 on the Home / Shorts pages and in between, fading away beyond. */
fun headerVisibility(pos: Float, homeIndex: Int, shortsIndex: Int): Float {
    val idx = listOf(homeIndex, shortsIndex).filter { it >= 0 }
    if (idx.isEmpty()) return 0f
    val lo = idx.min().toFloat()
    val hi = idx.max().toFloat()
    val dist = if (pos < lo) lo - pos else if (pos > hi) pos - hi else 0f
    return (1f - dist).coerceIn(0f, 1f)
}

/**
 * The header card. [pagerPosition] is the tab pager's position (page + offset); with it the card knows how far
 * it is from Shorts (where it is always collapsed) and from the other tabs (where it is hidden).
 */
@Composable
fun CollapsingHeader(
    pagerPosition: () -> Float,
    homeIndex: Int,
    shortsIndex: Int,
    modifier: Modifier = Modifier
) {
    val nav = LocalNav.current
    val density = LocalDensity.current
    val sbPx = WindowInsets.statusBars.getTop(density)

    // spring smoothing: the card glides to where the Home scroll wants it instead of snapping with every pixel
    val homeF = animateFloatAsState(HeaderScroll.raw, spring(dampingRatio = 1f, stiffness = 320f), label = "headerCollapse")
    // 1 on the Shorts page, 0 one page away: the card shrinks / grows while the pages slide
    val shortsW: () -> Float = {
        if (shortsIndex < 0) 0f else (1f - kotlin.math.abs(pagerPosition() - shortsIndex)).coerceIn(0f, 1f)
    }
    // 0 = expanded .. 1 = collapsed
    val fOf: () -> Float = {
        val h = homeF.value
        (h + (1f - h) * shortsW()).coerceIn(0f, 1f)
    }

    val firstName = Session.name.trim().substringBefore(' ').ifEmpty { "Davesh" }
    val thumb = Session.thumb

    Layout(
        content = {
            // 0: greeting (fades out and slides toward the photo while collapsing)
            Text(
                "Hi, $firstName!",
                color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.graphicsLayer {
                    val p = fOf()
                    alpha = (1f - p * 2.6f).coerceIn(0f, 1f)
                    translationX = 32.dp.toPx() * p
                    val sc = 1f - 0.12f * p
                    scaleX = sc
                    scaleY = sc
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                }
            )
            // 1: profile photo (opens Settings)
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(SecondaryContainer)
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
                    .background(Card)
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
            .graphicsLayer {
                // slides up a little and fades while the pager moves away from Home / Shorts
                val v = headerVisibility(pagerPosition(), homeIndex, shortsIndex)
                alpha = v
                translationY = -(1f - v) * 36.dp.toPx()
            }
            .drawBehind {
                val w = shortsW()
                // flat bar over the full screen width (status bar included), in the screen colour: part of the page, not a card.
                // The feed slides under it with a soft fade. Over a Short the bar goes away and only a dark scrim stays,
                // so the search pill and photo float on the video.
                val bgA = 1f - w
                val barH = size.height
                if (bgA > 0f) {
                    drawRect(Bg, size = Size(size.width, barH), alpha = bgA)
                    drawRect(
                        Brush.verticalGradient(listOf(Bg, Color.Transparent), startY = barH, endY = barH + FadeBelow.toPx()),
                        topLeft = Offset(0f, barH), size = Size(size.width, FadeBelow.toPx()), alpha = bgA
                    )
                }
                if (w > 0f) {
                    drawRect(
                        Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent), startY = 0f, endY = barH),
                        size = Size(size.width, barH), alpha = w
                    )
                }
            }
    ) { measurables, c ->
        val p = fOf()
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
