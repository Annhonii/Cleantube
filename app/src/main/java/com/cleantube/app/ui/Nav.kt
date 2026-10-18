package com.cleantube.app.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.VideoItem

sealed interface Route {
    // The four tabs of the floating nav
    data object Home : Route
    data object Feed : Route
    data object Library : Route
    data object History : Route

    data object Settings : Route
    data object Login : Route
    data class Search(val query: String = "") : Route
    data class Video(val id: String, val seed: VideoItem? = null) : Route
    data class Channel(val id: String, val seed: ChannelItem? = null, val handle: String = "") : Route
    data class Playlist(val id: String, val title: String = "") : Route
    data class LocalList(val id: String) : Route
}

val TabRoutes: List<Route> = listOf(Route.Home, Route.Feed, Route.Library, Route.History)

/** A tiny back stack. Tabs replace the whole stack, everything else is pushed on top. */
class Nav(start: Route = Route.Home) {
    val stack = mutableStateListOf(start)

    val current: Route get() = stack.last()
    val isTabRoot: Boolean get() = stack.size == 1 && current in TabRoutes

    fun push(r: Route) {
        stack.add(r)
    }

    fun selectTab(r: Route) {
        stack.clear()
        stack.add(r)
    }

    /** Pops one screen. Returns false when there is nothing left to pop. */
    fun back(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        return false
    }
}

val LocalNav = compositionLocalOf<Nav> { error("Nav not provided") }
