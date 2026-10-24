package com.cleantube.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
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

private val NoGreetGap = 18.dp      // photo row -> navigation row when there is no greeting (signed out)

/** The greeting only exists for a signed-in account with a name; signed out, nothing is shown there. */
private fun hasGreeting() = Session.loggedIn && Session.name.isNotBlank()

private fun navTopBigDp(greeting: Boolean): Dp =
    if (greeting) TopPad + AvatarBig + GreetGapTop + GreetH + GreetGapBottom else TopPad + AvatarBig + NoGreetGap
private val NavTopSmall = TopPad + AvatarSmall + NavGapSmall
private fun cardBig(greeting: Boolean): Dp = navTopBigDp(greeting) + NavH + BottomBig   // below the status bar
private val CardSmall = NavTopSmall + NavH + BottomSmall

// Rounded top corners of the feed (every tab page): the page's content is cut with rounded top-left / top-right corners
// right under the header bar, so a video that scrolls up under the header looks rounded there. It is part of the page
// (see [feedTopCorners]), so it moves with the page when tabs are switched or swiped.
val FeedCorner = 24.dp   // radius of the rounded top corners of every tab page (0.dp = straight)
// The feed tabs (Home / Subscription / Library) have their cards 14dp in from the screen edge, so only a thin slice of a 24dp
// curve touches them and it looks much shallower than on Shorts (whose video is edge to edge). Their container curve is
// concentric with the 24dp one instead: 24dp + that 14dp margin, so the curve reaches the cards the same way.
val FeedCornerFeed = 38.dp

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
    return sb + androidx.compose.ui.unit.lerp(cardBig(hasGreeting()), CardSmall, p)
}

/**
 * Bottom edge of the header bar (px from the top of the screen) for the page of [route], read when drawing: it follows
 * the header's own spring, so the page's rounded corners stay glued to the bar while it collapses.
 */
@Composable
fun rememberHeaderBarBottom(route: Route): () -> Float {
    val density = LocalDensity.current
    val sbPx = WindowInsets.statusBars.getTop(density)
    val bigPx = with(density) { cardBig(hasGreeting()).toPx() }
    val smallPx = with(density) { CardSmall.toPx() }
    val target = if (route == Route.Home) HeaderScroll.get(Route.Home) else 1f
    val p = animateFloatAsState(target, HeaderSpring, label = "headerBarBottom")
    return { sbPx + bigPx + (smallPx - bigPx) * p.value }
}

/** Cuts the content with rounded top corners at [top] (px); everything above [top] is left to the header bar. */
fun Modifier.feedTopCorners(top: () -> Float, radius: Float): Modifier = drawWithContent {
    val y = top()
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                0f, y, size.width, size.height,
                topLeftCornerRadius = CornerRadius(radius, radius),
                topRightCornerRadius = CornerRadius(radius, radius),
                bottomRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius.Zero
            )
        )
    }
    clipPath(path) { this@drawWithContent.drawContent() }
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
    val firstName = if (hasGreeting()) Session.name.trim().substringBefore(' ') else ""
    val thumb = Session.thumb

    // Colours follow the pager: t = 0 on Home / the other tabs, 1 on Shorts, and in between while the pages slide, so the
    // header fades Home <-> Shorts (white <-> black) together with the finger instead of flipping when the swipe ends.
    val shortsT by remember(shortsIndex) {
        androidx.compose.runtime.derivedStateOf {
            if (shortsIndex < 0) 0f else (1f - kotlin.math.abs(pagerPosition() - shortsIndex)).coerceIn(0f, 1f)
        }
    }
    val t = shortsT
    val shortsSurface = Color(0x33FFFFFF)
    val surface = lerp(Card, shortsSurface, t)
    val fg = lerp(TextPrimary, Color.White, t)
    val muted = lerp(TextMuted, Color(0xB3FFFFFF), t)
    val selBg = lerp(Accent, Color.White, t)
    val onSel = lerp(OnAccent, Color.Black, t)
    val barColor = lerp(Bg, Color.Black, t)
    val greetingStyle = androidx.compose.ui.text.TextStyle(
        fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp
    )

    Layout(
        content = {
            // 0: greeting (under the photo; fades out completely while the header collapses). Two lines: "Welcome back,"
            // quieter, the account's first name stronger. Nothing at all when signed out.
            Column(
                Modifier.graphicsLayer {
                    val p = fOf()
                    alpha = (1f - p * 2.2f).coerceIn(0f, 1f)
                    translationY = -p * 16.dp.toPx()
                }
            ) {
                if (firstName.isNotEmpty()) {
                    Text(
                        "Welcome back,", style = greetingStyle, color = fg.copy(alpha = 0.55f),
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "$firstName!", style = greetingStyle, color = fg,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
            // 6: navigation row: Shorts button + Home | Subscription | Library, with ONE indicator that follows the pager
            NavRow(tabs, nav, pagerPosition, surface, selBg, onSel, muted)
        },
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // flat bar over the full screen width (status bar included): the screen colour, black on Shorts
                // (the video is dark, white icons)
                drawRect(barColor)
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
        val hasGreet = firstName.isNotEmpty()
        val greetTop = top + avBig + GreetGapTop.roundToPx()
        val greeting = measurables[0].measure(Constraints(maxWidth = (w - 2 * pad).coerceAtLeast(0)))

        // navigation row: Shorts button + pill; moves up to sit under the small photo row
        val navH = NavH.roundToPx()
        val navTopBig = top + navTopBigDp(hasGreet).roundToPx() - TopPad.roundToPx()
        val navTopSmall = top + avSm + NavGapSmall.roundToPx()
        val navTop = li(navTopBig, navTopSmall, p)
        val navRow = measurables[6].measure(Constraints.fixed((w - 2 * pad).coerceAtLeast(0), navH))

        val h = navTop + navH + li(BottomBig.roundToPx(), BottomSmall.roundToPx(), p)
        layout(w, h) {
            if (hasGreet && p < 0.999f) greeting.place(pad, greetTop)
            avatar.place(pad, cy - av / 2)
            searchBg.place(searchLeft, cy - btn / 2)
            searchIcon.place(iconX, cy - iconSz / 2)
            if (searchText.width > 0) searchText.place(searchLeft + textLeft, cy - searchText.height / 2)
            bell.place(bellX, cy - btn / 2)
            navRow.place(pad, navTop)
        }
    }
}

/**
 * The navigation row: a round Shorts button and, next to it, the pill with Home | Subscription | Library.
 * ONE filled indicator sits behind the open tab and is driven straight by the pager position: while you swipe it is
 * between the two tabs by exactly as much as the pages are, so it sticks to the finger (and when a tab is tapped it
 * follows the pager's own glide). From Home to Shorts it slides out of the pill into the round Shorts button, changing
 * its shape and colour on the way. The icon / label colours follow the same number.
 */
@Composable
private fun NavRow(
    tabs: List<TabSpec>,
    nav: Nav,
    pagerPosition: () -> Float,
    surface: Color,
    selBg: Color,
    onSel: Color,
    idle: Color
) {
    val n = tabs.size
    val hasShorts = tabs.firstOrNull()?.route == Route.Shorts
    val density = LocalDensity.current
    // bounds of every item (window px), measured once laid out; index = the pager page of that tab
    val bX = remember(n) { androidx.compose.runtime.mutableStateListOf(*Array(n) { 0f }) }
    val bTop = remember(n) { androidx.compose.runtime.mutableStateListOf(*Array(n) { 0f }) }
    val bW = remember(n) { androidx.compose.runtime.mutableStateListOf(*Array(n) { 0f }) }
    val bH = remember(n) { androidx.compose.runtime.mutableStateListOf(*Array(n) { 0f }) }
    var root by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val pos = pagerPosition().coerceIn(0f, (n - 1).coerceAtLeast(0).toFloat())
    // text that never gets cut ("Library" in full): the label size ignores the phone's font scale
    val labelSp = (13f / density.fontScale).sp
    val navH = NavH

    Box(Modifier.fillMaxSize().onGloballyPositioned { root = it.positionInWindow() }) {
        // 1: the resting pill behind all the tabs
        Box(Modifier.fillMaxSize().clip(CircleShape).background(surface))
        // 2: the indicator: ONE filled pill behind the open tab. Its position AND size are the bounds of the two tabs
        // it is between (mixed by the pager position), and those bounds keep changing while a label opens / closes, so
        // it slides and stretches together with the labels, and sticks to the finger while swiping.
        Box(
            Modifier.fillMaxSize().drawBehind {
                if (n == 0) return@drawBehind
                val pp = pagerPosition().coerceIn(0f, (n - 1).coerceAtLeast(0).toFloat())
                val lo = kotlin.math.floor(pp).toInt().coerceIn(0, n - 1)
                val hi = (lo + 1).coerceAtMost(n - 1)
                val f = pp - lo
                if (bW[lo] <= 0f || bW[hi] <= 0f) return@drawBehind
                val x = lf(bX[lo], bX[hi], f) - root.x
                val y = lf(bTop[lo], bTop[hi], f) - root.y
                val w = lf(bW[lo], bW[hi], f)
                val h = lf(bH[lo], bH[hi], f)
                drawRoundRect(selBg, Offset(x, y), Size(w, h), CornerRadius(h / 2f, h / 2f))
            }
        )
        // 3: the tabs on top. Every tab is its icon; the label of a tab opens next to the icon as the indicator arrives
        // and closes as it leaves (same number as the indicator: the pager position), so only the open tab has its name.
        val gapPx = with(density) { 6.dp.roundToPx() }
        Row(
            Modifier.fillMaxSize().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            tabs.forEachIndexed { i, t ->
                val k = (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)
                val tint = lerp(idle, onSel, k)
                Box(
                    Modifier
                        .fillMaxHeight()
                        .onGloballyPositioned { c ->
                            if (i < bX.size) {
                                val o = c.positionInWindow()
                                bX[i] = o.x; bTop[i] = o.y; bW[i] = c.size.width.toFloat(); bH[i] = c.size.height.toFloat()
                            }
                        }
                        .bouncyClickable(pressedScale = 0.94f, onClick = { if (nav.current != t.route) nav.selectTab(t.route) }),
                    contentAlignment = Alignment.Center
                ) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(t.icon, contentDescription = t.label, tint = tint, modifier = Modifier.size(22.dp))
                        Text(
                            t.label, color = tint, fontSize = labelSp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
                            modifier = Modifier
                                .layout { m, c ->
                                    val kk = (1f - kotlin.math.abs(pagerPosition() - i)).coerceIn(0f, 1f)
                                    val pl = m.measure(androidx.compose.ui.unit.Constraints())
                                    val w = ((pl.width + gapPx) * kk).roundToInt()
                                    layout(w, pl.height) { pl.placeRelative((gapPx * kk).roundToInt(), 0) }
                                }
                                .graphicsLayer {
                                    alpha = (1f - kotlin.math.abs(pagerPosition() - i)).coerceIn(0f, 1f)
                                }
                        )
                    }
                }
            }
        }
    }
}
