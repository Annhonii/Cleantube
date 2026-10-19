package com.cleantube.app.ui

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
    val firstRoute = visible.first().route
    // where the top bar's chip is (the selected tab's index); above the screens so the slide survives a tab switch
    val tabPos = remember { Animatable(visible.indexOfFirst { it.route == nav.current }.toFloat()) }
    LaunchedEffect(Unit) {
        if (nav.isTabRoot && nav.current == Route.Home && visible.none { it.route == Route.Home }) {
            nav.selectTab(firstRoute)
        }
    }
    // LocalContentColor: any Text without its own colour follows the theme (light or dark).
    CompositionLocalProvider(
        LocalNav provides nav,
        LocalFullscreen provides fullscreen,
        LocalTabPos provides tabPos,
        LocalContentColor provides TextPrimary
    ) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            val r = nav.current

            // Swipe left / right anywhere on a tab screen: next / previous tab (in the user's order, hidden tabs
            // skipped). Vertical scrolling and horizontal lists inside the screen get the gesture first.
            val swipePx = with(LocalDensity.current) { 90.dp.toPx() }
            val navState = rememberUpdatedState(nav)
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragCancel = { total = 0f },
                        onDragEnd = {
                            val n = navState.value
                            if (n.isTabRoot) {
                                val target = when {
                                    total <= -swipePx -> adjacentTab(Graph.store, n.current, 1)
                                    total >= swipePx -> adjacentTab(Graph.store, n.current, -1)
                                    else -> null
                                }
                                if (target != null) n.selectTab(target)
                            }
                            total = 0f
                        },
                        onHorizontalDrag = { _, amount -> total += amount }
                    )
                }
            ) {
            key(r) {
                when (r) {
                    Route.Home -> HomeScreen()
                    Route.Shorts -> ShortsScreen()
                    Route.Feed -> FeedScreen()
                    Route.Library -> LibraryScreen()
                    Route.History -> HistoryScreen()
                    Route.Settings -> SettingsScreen()
                    Route.Login -> LoginScreen()
                    Route.Downloads -> DownloadsScreen()
                    is Route.LocalPlay -> LocalPlayScreen(r)
                    is Route.Search -> SearchScreen(r)
                    is Route.Video -> VideoScreen(r)
                    is Route.Channel -> ChannelScreen(r)
                    is Route.Playlist -> PlaylistScreen(r)
                    is Route.LocalList -> LocalListScreen(r)
                }
            }
            }

            if (nav.isTabRoot) {
                FloatingSearchBar(
                    onClick = { nav.push(Route.Search()) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

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
        BackHandler(enabled = !nav.isTabRoot || nav.current != firstRoute) {
            // inside the Shorts page, YouTube's own history goes first
            if (nav.current == Route.Shorts && ShortsWeb.goBack()) return@BackHandler
            if (!nav.back()) nav.selectTab(firstRoute)
        }
        // Registered last so it wins while a video is fullscreen.
        BackHandler(enabled = fullscreen.view != null) {
            fullscreen.requestExit()
        }
    }
}
