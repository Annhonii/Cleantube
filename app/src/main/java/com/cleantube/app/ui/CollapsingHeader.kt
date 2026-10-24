package com.cleantube.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.Session
import kotlin.math.roundToInt

// Collapsing top header, one for the whole app (drawn above the tab pager, see App.kt).
//
//  expanded (Home, feed at the top):        collapsed (Home scrolled, and every other tab):
//    ( photo, big )      (search) (bell)       ( photo, small ) ( search bar, wide ) (bell)
//    Welcome back,                             ( shorts ) ( Home | Subscription | Library )
//    <First name>!
//    ( shorts ) ( Home | Subscription | Library )
//
// Everything is driven by ONE number (0 = expanded, 1 = collapsed) that follows the feed's scroll through a spring.
// The header never rests half way: the feed's scroll only decides between the two states (see TrackHeaderScroll),
// and the size / position of every part is worked out from that number in the layout phase (no recomposition per frame).

private val PadH = 20.dp            // screen edge -> content
private val Gap = 10.dp
private val TopPad = 10.dp          // status bar -> photo
private val AvatarBig = 62.dp
private val AvatarSmall = 42.dp
private val BtnBig = 46.dp          // round search / bell buttons
private val BtnSmall = 42.dp
private val GreetGapTop = 14.dp     // photo -> greeting
private val GreetH = 88.dp          // two lines of the greeting
private val GreetGapBottom = 18.dp  // greeting -> navigation row
private val NavH = 44.dp
private val NavGapSmall = 10.dp     // photo row -> navigation row when collapsed
private val BottomBig = 10.dp
private val BottomSmall = 8.dp

private val NavTopBig = TopPad + AvatarBig + GreetGapTop + GreetH + GreetGapBottom
private val NavTopSmall = TopPad + AvatarSmall + NavGapSmall
private val CardBig = NavTopBig + NavH + BottomBig        // below the status bar
private val CardSmall = NavTopSmall + NavH + BottomSmall

private val SearchIconSize = 24.dp
private val SearchIconLeft = 16.dp    // icon's place inside the wide search bar
private val SearchTextLeft = SearchIconLeft + SearchIconSize + 10.dp

// One spring for the header and for the list padding under it, so the feed follows the header exactly.
private val HeaderSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 380f)

/**
 * Current height of the header for the feed of [route]: use it as the top padding of that feed. On Home it follows
 * the header (big -> small with the same spring), so the feed moves up together with the header and no empty gap is
 * left under the collapsed bar. Every other tab always sits under the collapsed header.
 */
@Composable
fun collapsingHeaderHeight(route: Route = Route.Home): Dp {
    val sb = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val target = if (route == Route.Home) HeaderScroll.get(Route.Home) else 1f
    val p by animateFloatAsState(target, HeaderSpring, label = "headerHeight")
    return sb + androidx.compose.ui.unit.lerp(CardBig, CardSmall, p)
}

private fun li(a: Int, b: Int, t: Float) = (a + (b - a) * t).roundToInt()
private fun lf(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Scroll state of the Home feed as 0 (header expanded) or 1 (collapsed); written by [TrackHeaderScroll]. */
object HeaderScroll {
    private val home = androidx.compose.runtime.mutableFloatStateOf(0f)

    /** Collapse value (0 / 1) of the feed that belongs to [route]; tabs without a scroll-following feed are always 1 (collapsed). */
    fun get(route: Route): Float = when (route) {
        Route.Home -> home.floatValue
        else -> 1f
    }

    fun set(route: Route, v: Float) {
        when (route) {
            Route.Home -> home.floatValue = v
            else -> Unit
        }
    }
}

/** Reports a list's scroll to the header. Call it from the screen whose feed the header should follow. */
@Composable
fun TrackHeaderScroll(listState: LazyListState, route: Route = Route.Home) {
    val density = LocalDensity.current
    // Only two states, never in between: as soon as the feed is scrolled a little the header glides to the collapsed
    // state, and it only grows back when the feed is (almost) back at the top. The gap between the two numbers stops flicker.
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

// The YouTube Shorts logo; if the path can't be parsed for any reason the tab's own icon is used instead.
private val ShortsIcon: ImageVector by lazy {
    runCatching {
        ImageVector.Builder(
            name = "Shorts", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(
                "M17.77,10.32l-1.2,-0.5L18,9.06c1.84,-0.96 2.53,-3.23 1.56,-5.06s-3.24,-2.53 -5.07,-1.56L6,6.94" +
                    "c-1.29,0.68 -2.07,2.04 -2,3.49 0.07,1.42 0.93,2.67 2.22,3.25 0.03,0.01 1.2,0.5 1.2,0.5L6,14.93" +
                    "c-1.83,0.97 -2.53,3.24 -1.56,5.07 0.97,1.83 3.24,2.53 5.07,1.56l8.5,-4.5c1.29,-0.68 2.06,-2.04 1.99,-3.49" +
                    "-0.07,-1.42 -0.94,-2.68 -2.23,-3.25zM10,14.65v-5.3L15,12l-5,2.65z"
            ),
            fill = SolidColor(Color.Black)
        ).build()
    }.getOrElse { Icons.Rounded.LocalFireDepartment }
}

/**
 * The header. [pagerPosition] is the tab pager's position (page + offset): while swiping between Home and another
 * tab the header glides between its expanded and collapsed look in step with the pages.
 */
@Composable
fun CollapsingHeader(
    pagerPosition: () -> Float,
    homeIndex: Int,
    shortsIndex: Int,
    routes: List<Route>,
    tabs: List<TabSpec>,
    modifier: Modifier = Modifier
) {
    val nav = LocalNav.current
    val density = LocalDensity.current
    val sbPx = WindowInsets.statusBars.getTop(density)
    val onShorts = nav.current == Route.Shorts

    // spring smoothing: the header glides to the state the feed's scroll wants instead of snapping
    val homeF = animateFloatAsState(HeaderScroll.get(Route.Home), HeaderSpring, label = "headerHome")
    // 0 = expanded .. 1 = collapsed. Home follows its feed's scroll; every other page always shows the collapsed header.
    // While swiping between two pages the value glides between theirs.
    val pageValue: (Int) -> Float = { i ->
        when (routes.getOrNull(i)) {
            Route.Home -> homeF.value
            else -> 1f
        }
    }
    val fOf: () -> Float = {
        val pos = pagerPosition().coerceAtLeast(0f)
        val lo = kotlin.math.floor(pos).toInt()
        lf(pageValue(lo), pageValue(lo + 1), pos - lo).coerceIn(0f, 1f)
    }

    // The account's first name comes from the saved copy of the Google account (see Session), so it is there at once
    val firstName = if (Session.loggedIn) Session.name.trim().substringBefore(' ') else ""
    val thumb = Session.thumb

    // Over a Short the header is see-through with white icons
    val surface = if (onShorts) Color(0x33FFFFFF) else Card
    val fg = if (onShorts) Color.White else TextPrimary
    val muted = if (onShorts) Color(0xB3FFFFFF) else TextMuted
    val selBg = if (onShorts) Color.White else Accent
    val onSel = if (onShorts) Color.Black else OnAccent
    val dark = Bg.luminance() < 0.5f
    val greetingStyle = androidx.compose.ui.text.TextStyle(
        fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp
    )
    val shortsTab = tabs.firstOrNull { it.route == Route.Shorts }
    val pillTabs = tabs.filter { it.route != Route.Shorts }

    Layout(
        content = {
            // 0: greeting (big, under the photo; fades out completely while the header collapses)
            Text(
                buildAnnotatedString {
                    if (firstName.isEmpty()) {
                        withStyle(SpanStyle(color = fg)) { append("Welcome!") }
                    } else {
                        withStyle(SpanStyle(color = if (dark) fg.copy(alpha = 0.6f) else fg)) { append("Welcome back,\n") }
                        withStyle(SpanStyle(color = fg)) { append("$firstName!") }
                    }
                },
                style = greetingStyle,
                maxLines = 2, softWrap = false, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    val p = fOf()
                    alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                    translationY = -p * 16.dp.toPx()
                }
            )
            // 1: profile photo (the Google account's), opens Settings
            Box(
                Modifier
                    .bouncyClickable(pressedScale = 0.92f, onClick = { if (nav.current != Route.Settings) nav.push(Route.Settings) })
                    .clip(CircleShape)
                    .background(surface),
                contentAlignment = Alignment.Center
            ) {
                if (thumb.isNotEmpty()) AppImage(thumb, Modifier.fillMaxSize(), CircleShape)
                else Icon(Icons.Rounded.PersonOutline, contentDescription = "Account", tint = fg, modifier = Modifier.size(28.dp))
            }
            // 2: search bar (round at first, grows to the left into a wide bar); tap opens the search page
            Box(
                Modifier
                    .bouncyClickable(pressedScale = 0.97f, onClick = { if (nav.current != Route.Search()) nav.push(Route.Search()) })
                    .clip(CircleShape)
                    .background(surface)
            )
            // 3: search icon (centred in the round button, slides to the left edge of the wide bar)
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = fg, modifier = Modifier.size(SearchIconSize))
            // 4: "Search..." (appears while the bar is wide)
            Text(
                "Search...", color = muted, fontSize = 17.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.graphicsLayer { alpha = ((fOf() - 0.55f) / 0.35f).coerceIn(0f, 1f) }
            )
            // 5: notifications
            Box(
                Modifier
                    .bouncyClickable(pressedScale = 0.9f, onClick = { if (nav.current != Route.Notifications) nav.push(Route.Notifications) })
                    .clip(CircleShape)
                    .background(surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.NotificationsNone, contentDescription = "Notifications", tint = fg, modifier = Modifier.size(24.dp))
            }
            // 6: Shorts button
            Box(
                Modifier
                    .bouncyClickable(pressedScale = 0.9f, onClick = { if (shortsTab != null && !onShorts) nav.selectTab(Route.Shorts) })
                    .clip(CircleShape)
                    .background(if (onShorts) selBg else surface),
                contentAlignment = Alignment.Center
            ) {
                if (shortsTab != null) {
                    Icon(ShortsIcon, contentDescription = "Shorts", tint = if (onShorts) onSel else fg, modifier = Modifier.size(24.dp))
                }
            }
            // 7: navigation pill: Home | Subscription | Library
            NavPill(pillTabs, nav, surface, selBg, onSel, muted)
        },
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // flat bar over the full screen width (status bar included), in the screen colour; the feed slides under
                // it with a clean edge. Over a Short it is see-through, so the buttons float on the video.
                if (!onShorts) drawRect(Bg)
            }
    ) { measurables, c ->
        val p = fOf()
        val w = c.maxWidth
        val pad = PadH.roundToPx()
        val gap = Gap.roundToPx()
        val top = sbPx + TopPad.roundToPx()

        // photo + round buttons: big -> small
        val avBig = AvatarBig.roundToPx()
        val avSm = AvatarSmall.roundToPx()
        val av = li(avBig, avSm, p)
        val btn = li(BtnBig.roundToPx(), BtnSmall.roundToPx(), p)
        val cy = top + av / 2   // the photo is the tallest part of the top row; the buttons share its centre line

        val avatar = measurables[1].measure(Constraints.fixed(av, av))

        // bell: always at the right edge
        val bellX = w - pad - btn
        val bell = measurables[5].measure(Constraints.fixed(btn, btn))

        // search: round button left of the bell -> wide bar from just right of the (small) photo to the bell.
        // Its right edge stays put, its left edge slides to the left.
        val searchRight = bellX - gap
        val searchLeftBig = searchRight - BtnBig.roundToPx()
        val searchLeftSmall = pad + avSm + gap
        val searchLeft = li(searchLeftBig, searchLeftSmall, p)
        val searchW = (searchRight - searchLeft).coerceAtLeast(0)
        val searchBg = measurables[2].measure(Constraints.fixed(searchW, btn))
        val iconSz = SearchIconSize.roundToPx()
        val searchIcon = measurables[3].measure(Constraints.fixed(iconSz, iconSz))
        val iconX = searchLeft + li((searchW - iconSz) / 2, SearchIconLeft.roundToPx(), p)
        val textLeft = SearchTextLeft.roundToPx()
        val searchText = measurables[4].measure(
            Constraints(maxWidth = (searchW - textLeft - 12.dp.roundToPx()).coerceAtLeast(0))
        )

        // greeting: under the photo, at the left edge
        val greetTop = top + avBig + GreetGapTop.roundToPx()
        val greeting = measurables[0].measure(Constraints(maxWidth = (w - 2 * pad).coerceAtLeast(0)))

        // navigation row: Shorts button + pill; moves up to sit under the small photo row
        val navH = NavH.roundToPx()
        val navTopBig = top + avBig + GreetGapTop.roundToPx() + GreetH.roundToPx() + GreetGapBottom.roundToPx()
        val navTopSmall = top + avSm + NavGapSmall.roundToPx()
        val navTop = li(navTopBig, navTopSmall, p)
        val shortsBtn = measurables[6].measure(Constraints.fixed(navH, navH))
        // without a Shorts tab (hidden in Settings) the pill takes the whole row
        val pillX = if (shortsTab != null) pad + navH + gap else pad
        val pill = measurables[7].measure(Constraints.fixed((w - pad - pillX).coerceAtLeast(0), navH))

        val h = navTop + navH + li(BottomBig.roundToPx(), BottomSmall.roundToPx(), p)
        layout(w, h) {
            if (p < 0.999f) greeting.place(pad, greetTop)
            avatar.place(pad, cy - av / 2)
            searchBg.place(searchLeft, cy - btn / 2)
            searchIcon.place(iconX, cy - iconSz / 2)
            if (searchText.width > 0) searchText.place(searchLeft + textLeft, cy - searchText.height / 2)
            bell.place(bellX, cy - btn / 2)
            if (shortsTab != null) shortsBtn.place(pad, navTop)
            pill.place(pillX, navTop)
        }
    }
}

/** Home | Subscription | Library: the open tab is a filled pill with its name, the others are icon + name. */
@Composable
private fun NavPill(
    tabs: List<TabSpec>,
    nav: Nav,
    surface: Color,
    selBg: Color,
    onSel: Color,
    idle: Color
) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(surface)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tabs.forEach { t ->
            val selected = t.route == nav.current
            val bg by animateColorAsState(if (selected) selBg else Color.Transparent, label = "pillItemBg")
            Row(
                Modifier
                    .fillMaxHeight()
                    .bouncyClickable(pressedScale = 0.94f, onClick = { if (!selected) nav.selectTab(t.route) })
                    .clip(CircleShape)
                    .background(bg)
                    .padding(horizontal = if (selected) 14.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(t.icon, contentDescription = t.label, tint = if (selected) onSel else idle, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    t.label,
                    color = if (selected) onSel else idle,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1, softWrap = false
                )
            }
        }
    }
}
