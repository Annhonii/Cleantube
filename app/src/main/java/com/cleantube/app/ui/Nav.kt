package com.cleantube.app.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.VideoItem

sealed interface Route {
    // The tabs of the floating nav
    data object Home : Route
    data object Shorts : Route
    data object Feed : Route
    data object Trending : Route
    data object Library : Route
    data object Downloads : Route
    data object Notifications : Route

    data object Settings : Route
    data object History : Route
    data object Playlists : Route
    data object Login : Route
    data class LocalPlay(val uri: String, val title: String) : Route
    data class Search(val query: String = "") : Route
    data class Video(val id: String, val seed: VideoItem? = null) : Route
    data class Channel(val id: String, val seed: ChannelItem? = null, val handle: String = "") : Route
    data class Playlist(val id: String, val title: String = "") : Route
    data class LocalList(val id: String) : Route
}

val TabRoutes: List<Route> = listOf(Route.Home, Route.Shorts, Route.Feed, Route.Library)

enum class NavKind { PUSH, POP, TAB }

/** A tiny back stack. Tabs replace the whole stack, everything else is pushed on top. */
class Nav(start: Route = Route.Home) {
    val stack = mutableStateListOf(start)

    /** What the last navigation was; the screen transition reads it to pick its animation. */
    var lastKind: NavKind = NavKind.TAB

    val current: Route get() = stack.last()
    val isTabRoot: Boolean get() = stack.size == 1 && current in TabRoutes

    fun push(r: Route) {
        lastKind = NavKind.PUSH
        stack.add(r)
    }

    fun selectTab(r: Route) {
        lastKind = NavKind.TAB
        stack.clear()
        stack.add(r)
    }

    /** Pops one screen. Returns false when there is nothing left to pop. */
    fun back(): Boolean {
        if (stack.size > 1) {
            lastKind = NavKind.POP
            stack.removeAt(stack.lastIndex)
            return true
        }
        return false
    }
}

val LocalNav = compositionLocalOf<Nav> { error("Nav not provided") }
