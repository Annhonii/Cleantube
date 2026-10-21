package com.cleantube.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.luminance
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
//  expanded:  ( photo, big )
//             ( Hi, Davesh )
//             ( search bar, full width            (round button) )
//  collapsed: ( photo, small ) ( search bar, up to the right edge )
//
// A red glow (same red in light and dark mode) sits behind the expanded header and fades away while it collapses. Everything is driven by ONE number (0 = expanded,
// 1 = collapsed) that follows the list's scroll through a spring, and the size / position of every part is
// worked out from it in the layout phase: no recomposition per frame, no jumps.

private val CardMargin = 0.dp      // space between the card and the screen's left / right edge
private val CardTop = 0.dp          // space between the status bar and the card
private val InnerPad = 20.dp        // space between the screen edge and what is inside
private val Gap = 10.dp
private val AvatarBig = 60.dp
private val AvatarSmall = 44.dp
private val SearchBig = 54.dp
private val SearchSmall = 46.dp
private val TopPadBig = 12.dp       // status bar -> photo
private val GreetGapTop = 8.dp      // photo -> greeting
private val GreetH = 40.dp          // greeting line
private val GreetGapBottom = 10.dp  // greeting -> search bar
private val BottomPadBig = 14.dp
private val CardBig = TopPadBig + AvatarBig + GreetGapTop + GreetH + GreetGapBottom + SearchBig + BottomPadBig   // 198dp
private val CardSmall = 64.dp
private val BelowCard = 4.dp

// The red of the header glow. One fixed colour, the same in light and dark mode (taken from the dark design).
private val HeaderRed = Color(0xFFC62A3F)

/** Full (expanded) height of [CollapsingHeader]: use it as the top padding of the list underneath. */
@Composable
fun collapsingHeaderHeight(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + CardTop + CardBig + BelowCard

private fun li(a: Int, b: Int, t: Float) = (a + (b - a) * t).roundToInt()
private fun lf(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Scroll of the Home feed as 0 (top) .. 1 (fully scrolled past the card's growth); written by [TrackHeaderScroll]. */
object HeaderScroll {
    private val home = androidx.compose.runtime.mutableFloatStateOf(0f)
    private val feed = androidx.compose.runtime.mutableFloatStateOf(0f)
    private val library = androidx.compose.runtime.mutableFloatStateOf(0f)

    /** Scroll (0..1) of the feed that belongs to [route]; tabs without a scroll-following feed are always 1 (collapsed). */
    fun get(route: Route): Float = when (route) {
        Route.Home -> home.floatValue
        Route.Feed -> feed.floatValue
        Route.Library -> library.floatValue
        else -> 1f
    }

    fun set(route: Route, v: Float) {
        when (route) {
            Route.Home -> home.floatValue = v
            Route.Feed -> feed.floatValue = v
            Route.Library -> library.floatValue = v
            else -> Unit
        }
    }
}

/** Reports a list's scroll to the header. Call it from the screen whose feed the header should follow. */
@Composable
fun TrackHeaderScroll(listState: LazyListState, route: Route = Route.Home) {
    val density = LocalDensity.current
    // The header never rests half way: as soon as the feed is scrolled a little it glides to the collapsed state,
    // and it only grows back when the feed is (almost) back at the top. The gap between the two numbers stops flicker.
    val collapseAtPx = with(density) { 16.dp.toPx() }
    val expandAtPx = with(density) { 4.dp.toPx() }
    androidx.compose.runtime.LaunchedEffect(listState, collapseAtPx, expandAtPx, route) {
        var collapsed = HeaderScroll.get(route) >= 0.5f
        androidx.compose.runtime.snapshotFlow {
            if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
            else listState.firstVisibleItemScrollOffset.toFloat()
        }.collect { offset ->
            if (!collapsed && offset > collapseAtPx) collapsed = true
            else if (collapsed && offset < expandAtPx) collapsed = false
            HeaderScroll.set(route, if (collapsed) 1f else 0f)
        }
    }
}

/** How visible the header is for a pager position: it is on every tab page, so always 1 (kept so callers can still fade it). */
fun headerVisibility(pos: Float, homeIndex: Int, shortsIndex: Int): Float = 1f

/**
 * The header card. [pagerPosition] is the tab pager's position (page + offset); with it the card knows how far
 * it is from Shorts (where it is always collapsed) and from the other tabs (where it is hidden).
 */
@Composable
fun CollapsingHeader(
    pagerPosition: () -> Float,
    homeIndex: Int,
    shortsIndex: Int,
    routes: List<Route>,
    modifier: Modifier = Modifier
) {
    val nav = LocalNav.current
    val density = LocalDensity.current
    val sbPx = WindowInsets.statusBars.getTop(density)

    // spring smoothing: the card glides to where each feed's scroll wants it instead of snapping with every pixel
    val spec = spring<Float>(dampingRatio = 0.9f, stiffness = 380f)
    val homeF = animateFloatAsState(HeaderScroll.get(Route.Home), spec, label = "headerHome")
    val feedF = animateFloatAsState(HeaderScroll.get(Route.Feed), spec, label = "headerFeed")
    val libF = animateFloatAsState(HeaderScroll.get(Route.Library), spec, label = "headerLibrary")
    // 1 on the Shorts page, 0 one page away: the card shrinks / grows while the pages slide
    val shortsW: () -> Float = {
        if (shortsIndex < 0) 0f else (1f - kotlin.math.abs(pagerPosition() - shortsIndex)).coerceIn(0f, 1f)
    }
    // 0 = expanded .. 1 = collapsed. Home, Subscription and Library follow their own feed's scroll; Shorts (and any
    // other page) shows the collapsed bar. While swiping between two pages the value glides between theirs.
    val pageValue: (Int) -> Float = { i ->
        when (routes.getOrNull(i)) {
            Route.Home -> homeF.value
            Route.Feed -> feedF.value
            Route.Library -> libF.value
            else -> 1f
        }
    }
    val fOf: () -> Float = {
        val pos = pagerPosition().coerceAtLeast(0f)
        val lo = kotlin.math.floor(pos).toInt()
        lf(pageValue(lo), pageValue(lo + 1), pos - lo).coerceIn(0f, 1f)
    }

    val firstName = Session.name.trim().substringBefore(' ').ifEmpty { "Davesh" }
    val thumb = Session.thumb

    Layout(
        content = {
            // 0: greeting (big, under the photo; fades out completely while the header collapses)
            Text(
                "Hi, $firstName",
                color = TextPrimary, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.6).sp,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    val p = fOf()
                    alpha = (1f - p * 3f).coerceIn(0f, 1f)
                }
            )
            // 1: profile photo (opens Settings), at the left edge
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(SecondaryContainer)
                    .bouncyClickable(pressedScale = 0.9f, onClick = { if (nav.current != Route.Settings) nav.push(Route.Settings) }),
                contentAlignment = Alignment.Center
            ) {
                if (thumb.isNotEmpty()) AppImage(thumb, Modifier.fillMaxSize(), CircleShape)
                else Icon(Icons.Rounded.PersonOutline, contentDescription = "Account", tint = TextPrimary, modifier = Modifier.size(28.dp))
            }
            // 2: search bar: "search" on the left, round search button on the right (opens the search page)
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Card)
                    .bouncyClickable(pressedScale = 0.98f, onClick = { nav.push(Route.Search()) })
                    .padding(start = 22.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "search", color = TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Normal,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(Secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search", tint = if (Secondary.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(22.dp))
                }
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
                val p = fOf()
                // flat bar over the full screen width (status bar included), in the screen colour: part of the page, not a card.
                // The feed slides under it with a clean edge. Over a Short the bar goes away and only a dark scrim stays,
                // so the search bar and photo float on the video.
                val bgA = 1f - w
                val barH = size.height
                if (bgA > 0f) {
                    drawRect(Bg, size = Size(size.width, barH), alpha = bgA)
                    // red glow from the top left, the same red in light and dark mode; it melts into the screen colour
                    // towards the bottom and goes away while the header collapses
                    val glowA = (1f - p) * bgA
                    if (glowA > 0.01f) {
                        drawRect(
                            Brush.radialGradient(
                                0f to HeaderRed,
                                0.45f to HeaderRed.copy(alpha = 0.55f),
                                1f to Color.Transparent,
                                center = Offset(size.width * 0.24f, 0f),
                                radius = size.width * 1.0f
                            ),
                            size = Size(size.width, barH), alpha = glowA
                        )
                        drawRect(
                            Brush.verticalGradient(
                                0.35f to Color.Transparent, 1f to Bg,
                                startY = 0f, endY = barH
                            ),
                            size = Size(size.width, barH), alpha = glowA
                        )
                    }
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

        // photo: big -> small, always at the left edge
        val aBig = AvatarBig.roundToPx()
        val aSm = AvatarSmall.roundToPx()
        val a = li(aBig, aSm, p)
        val avatar = measurables[1].measure(Constraints.fixed(a, a))
        val avatarX = innerX
        val centerBig = cardTop + TopPadBig.roundToPx() + aBig / 2
        val centerSm = cardTop + cardSmall / 2
        val cy = li(centerBig, centerSm, p)

        // search bar: full width under the greeting -> from just right of the (small) photo to the right edge
        val sBig = SearchBig.roundToPx()
        val sSm = SearchSmall.roundToPx()
        val searchH = li(sBig, sSm, p)
        val greetTopBig = cardTop + TopPadBig.roundToPx() + aBig + GreetGapTop.roundToPx()
        val greetH = GreetH.roundToPx()
        val topBig = greetTopBig + greetH + GreetGapBottom.roundToPx()
        val topSm = centerSm - sSm / 2
        val searchTop = li(topBig, topSm, p)
        val leftBig = innerX
        val leftSm = innerX + aSm + gap
        val searchX = li(leftBig, leftSm, p)
        val searchW = w - innerX - searchX
        val search = measurables[2].measure(Constraints.fixed(searchW, searchH))

        // greeting: under the photo, at the left edge
        val greeting = measurables[0].measure(Constraints(maxWidth = (w - 2 * innerX).coerceAtLeast(0)))

        val h = cardTop + li(cardBig, cardSmall, p) + BelowCard.roundToPx()
        layout(w, h) {
            greeting.place(innerX, greetTopBig + (greetH - greeting.height) / 2)
            avatar.place(avatarX, cy - a / 2)
            search.place(searchX, searchTop)
        }
    }
}
