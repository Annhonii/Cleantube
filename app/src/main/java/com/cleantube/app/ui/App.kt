package com.cleantube.app.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.cleantube.app.data.Session
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CleanTubeRoot(nav: Nav, fullscreen: FullscreenHost) {
    LaunchedEffect(Unit) { Session.refresh() }
    CompositionLocalProvider(LocalNav provides nav, LocalFullscreen provides fullscreen) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            val r = nav.current

            key(r) {
                when (r) {
                    Route.Home -> HomeScreen()
                    Route.Feed -> FeedScreen()
                    Route.Library -> LibraryScreen()
                    Route.History -> HistoryScreen()
                    Route.Settings -> SettingsScreen()
                    Route.Login -> LoginScreen()
                    is Route.Search -> SearchScreen(r)
                    is Route.Video -> VideoScreen(r)
                    is Route.Channel -> ChannelScreen(r)
                    is Route.Playlist -> PlaylistScreen(r)
                    is Route.LocalList -> LocalListScreen(r)
                }
            }

            if (nav.isTabRoot) {
                FloatingNav(
                    selected = r,
                    onSelect = { nav.selectTab(it) },
                    onSearch = { nav.push(Route.Search()) },
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
        BackHandler(enabled = !nav.isTabRoot || nav.current != Route.Home) {
            if (!nav.back()) nav.selectTab(Route.Home)
        }
        // Registered last so it wins while a video is fullscreen.
        BackHandler(enabled = fullscreen.view != null) {
            fullscreen.requestExit()
        }
    }
}
