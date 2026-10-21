package com.cleantube.app.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Session
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CleanTubeRoot(nav: Nav, fullscreen: FullscreenHost) {
    LaunchedEffect(Unit) { Session.refresh() }

    // The app starts on Home; if the user turned Home off in Settings, start on the first visible tab instead.
    val visible = visibleTabs(Graph.store)
    val firstRoute = visible.firstOrNull { it.route == Route.Home }?.route ?: visible.first().route
    val visibleRoutes = visible.map { it.route }
    val navState = rememberUpdatedState(nav)
    val visibleState = rememberUpdatedState(visible)

    // The tab screens live in a pager: they follow the finger while you swipe, both pages are visible side by
    // side, and the pill's chip reads its position from this state.
    val pagerState = rememberPagerState(
        initialPage = visible.indexOfFirst { it.route == nav.current }.coerceAtLeast(0)
    ) { visible.size }
    val pagerLive = remember { mutableStateOf(false) }

    // swipe finished on another page -> that tab is now the open one
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val route = visibleState.value.getOrNull(page)?.route ?: return@collect
            val n = navState.value
            if (n.isTabRoot && n.current != route && visibleState.value.any { it.route == n.current }) {
                n.selectTab(route)
            }
        }
    }
    // a tab was picked some other way (tap on the pill, drag on the pill, back) -> the pager glides there
    val currentRoute = nav.current
    LaunchedEffect(currentRoute, visibleRoutes) {
        if (!navState.value.isTabRoot) return@LaunchedEffect
        val idx = visibleRoutes.indexOf(currentRoute)
        if (idx < 0 || pagerState.targetPage == idx) return@LaunchedEffect
        if (pagerLive.value) pagerState.animateScrollToPage(idx, animationSpec = tween(520, easing = Motion.Emphasized))
        else pagerState.scrollToPage(idx)
    }
    val chipPosition: () -> Float = {
        if (navState.value.current in visibleRoutes) pagerState.currentPage + pagerState.currentPageOffsetFraction else -1f
    }
    LaunchedEffect(Unit) {
        if (nav.isTabRoot && nav.current == Route.Home && visible.none { it.route == Route.Home }) {
            nav.selectTab(firstRoute)
        }
    }
    // LocalContentColor: any Text without its own colour follows the theme (light or dark).
    CompositionLocalProvider(
        LocalNav provides nav,
        LocalFullscreen provides fullscreen,
        LocalContentColor provides TextPrimary
    ) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            val r = nav.current

            AnimatedContent(
                targetState = r,
                transitionSpec = { screenTransition(nav, visibleRoutes) },
                contentKey = { if (it in visibleRoutes) "tabs" else it },
                modifier = Modifier.fillMaxSize(),
                label = "screens"
            ) { route ->
                // every screen paints its own background, so the one underneath never shows through while sliding
                Box(Modifier.fillMaxSize().background(Bg)) {
                    if (route in visibleRoutes) TabsPager(pagerState, visible, pagerLive)
                    else OtherScreen(route)
                }
            }

            // the header card: one card for Home and Shorts. It collapses with the Home feed's scroll, floats over the
            // Shorts video already collapsed, shrinks / grows while the pages slide between the two, and leaves upwards
            // when a screen is pushed.
            val homeIdx = visibleRoutes.indexOf(Route.Home)
            val shortsIdx = visibleRoutes.indexOf(Route.Shorts)
            val headerPos: () -> Float = { pagerState.currentPage + pagerState.currentPageOffsetFraction }
            val headerOn by remember(homeIdx, shortsIdx) {
                derivedStateOf { headerVisibility(headerPos(), homeIdx, shortsIdx) > 0.01f }
            }
            AnimatedVisibility(
                // hides under a Shorts sheet / menu
                visible = nav.isTabRoot && !(nav.current == Route.Shorts && ShortsUi.overlayOpen),
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { -it } + fadeIn(tween(240)),
                exit = slideOutVertically(tween(260, easing = Motion.EmphasizedAccelerate)) { -it } + fadeOut(tween(180))
            ) {
                if (headerOn) CollapsingHeader(headerPos, homeIdx, shortsIdx)
            }

            // the search bar floats up when a tab opens and drops away when a screen is pushed
            AnimatedVisibility(
                // on the Shorts tab it floats over the Short, shrunk (see BottomNavPill); hides under a Shorts sheet / menu
                visible = nav.isTabRoot && !(nav.current == Route.Shorts && ShortsUi.overlayOpen),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(dampingRatio = 0.72f, stiffness = 420f)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(240, easing = Motion.EmphasizedAccelerate)) { it } + fadeOut(tween(160))
            ) {
                BottomNavPill(position = chipPosition, shortsIndex = shortsIdx)
            }

            // the video page / mini player: above the screens and the bottom bar
            VideoSheet(tabRoot = nav.isTabRoot, hidden = nav.current == Route.Shorts)

            // Fullscreen video from the YouTube player, drawn above everything.
            val fsView = fullscreen.view
            if (fsView != null) {
                AndroidView(
                    factory = {
                        (fsView.parent as? ViewGroup)?.removeView(fsView)
                        fsView
                    },
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                )
            }
        }

        // Back: pop a screen; on a non-Home tab go to Home first; on Home let the system close the app.
        BackHandler(enabled = (!nav.isTabRoot || nav.current != firstRoute) && !ShortsUi.overlayOpen && !VideoHost.expanded) {
            if (!nav.back()) nav.selectTab(firstRoute)
        }
        // Back minimises an open video page into the mini player first.
        BackHandler(enabled = VideoHost.expanded && fullscreen.view == null && !VideoHost.commentsOpen) {
            VideoHost.requestCollapse()
        }
        // opening another screen from the video page (channel, search...) minimises it
        LaunchedEffect(nav.stack.size) {
            if (VideoHost.expanded) VideoHost.requestCollapse()
        }
        // Registered last so it wins while a video is fullscreen.
        BackHandler(enabled = fullscreen.view != null) {
            fullscreen.requestExit()
        }
    }
}

/** The tab screens side by side. Swiping drags the pages with the finger; the far page fades and shrinks a little. */
@Composable
private fun TabsPager(state: PagerState, tabs: List<TabSpec>, live: MutableState<Boolean>) {
    DisposableEffect(Unit) {
        live.value = true
        onDispose { live.value = false }
    }
    HorizontalPager(
        state = state,
        modifier = Modifier.fillMaxSize(),
        key = { tabs.getOrNull(it)?.id ?: it },
        flingBehavior = PagerDefaults.flingBehavior(
            state = state,
            snapAnimationSpec = spring(dampingRatio = 0.92f, stiffness = 300f)
        )
    ) { page ->
        val route = tabs.getOrNull(page)?.route
        Box(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .graphicsLayer {
                    val off = ((state.currentPage - page) + state.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    alpha = 1f - 0.35f * off
                    val s = 1f - 0.07f * off
                    scaleX = s
                    scaleY = s
                }
        ) {
            if (route != null) OtherScreen(route)
        }
    }
}

/** One screen by its route (the tab screens as well as the pushed ones). */
@Composable
private fun OtherScreen(route: Route) {
    when (route) {
        Route.Home -> HomeScreen()
        Route.Shorts -> ShortsScreen()
        Route.Feed -> FeedScreen()
        Route.Trending -> TrendingScreen()
        Route.Library -> LibraryScreen()
        Route.Notifications -> NotificationsScreen()
        Route.Settings -> SettingsScreen()
        Route.History -> HistoryScreen()
        Route.Playlists -> PlaylistsScreen()
        Route.Login -> LoginScreen()
        Route.Downloads -> DownloadsScreen()
        is Route.LocalPlay -> LocalPlayScreen(route)
        is Route.Search -> SearchScreen(route)
        is Route.Video -> Unit   // videos are drawn by VideoSheet
        is Route.Channel -> ChannelScreen(route)
        is Route.Playlist -> PlaylistScreen(route)
        is Route.LocalList -> LocalListScreen(route)
    }
}
