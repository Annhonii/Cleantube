package com.cleantube.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.cleantube.app.data.Api
import com.cleantube.app.data.TabCache
import com.cleantube.app.data.Session
import com.cleantube.app.data.QuickPicks
import com.cleantube.app.data.CommunityPosts
import kotlinx.coroutines.launch

/** YouTube's own home feed: personalized when signed in, the general one otherwise. */
@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val loggedIn = Session.loggedIn

    // The header floats over the list: the feed starts below it and scrolls up underneath while it collapses.
    Box(Modifier.fillMaxSize()) {
        val paged = TabCache.paged("home:$loggedIn:${Session.handle}") { token -> Api.home(token) }
        val headerH = collapsingHeaderHeight(Route.Home)
        // Community posts of the subscribed channels are slipped in between the videos (loaded in the background,
        // the feed never waits for them)
        val subs = com.cleantube.app.data.Graph.store.subscriptions
        androidx.compose.runtime.LaunchedEffect(loggedIn, Session.handle, subs) { CommunityPosts.refresh() }
        val posts = CommunityPosts.pool
        val entries = remember(paged.items, posts) { mixHome(paged.items, posts) }
        PagedColumn(
            paged = paged,
            emptyText = "Nothing to show right now. Pull to retry from the Feed tab, or check your connection.",
            contentPadding = PaddingValues(top = headerH + 12.dp, bottom = listBottomPad()),
            refreshIndicatorTop = headerH,
            // Quick picks: newest unwatched videos of your subscribed channels, refreshed automatically
            header = { item { Column { Spacer(Modifier.height(1.dp)); QuickPicksSection() } } },
            onRefresh = { QuickPicks.request(force = true); CommunityPosts.request(force = true) },
            itemCount = entries.size,
            // videos and posts, one row each
            indexRow = { i ->
                when (val e = entries.getOrNull(i)) {
                    is HomeEntry.Video -> VideoRow(e.v, onClick = { nav.push(Route.Video(e.v.id, e.v)) })
                    is HomeEntry.PostEntry -> HomePostCard(e.post)
                    null -> Unit
                }
            }
        ) { }
        // the card itself is drawn once for the whole app (App.kt); this only tells it how far the feed is scrolled
        TrackHeaderScroll(paged.listState, Route.Home)
        PrefetchTop(paged.items)
    }
}

/** Trending: what is popular today. */
@Composable
fun TrendingScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TabTopBar("Trending")
        val paged = TabCache.paged("trending") { token -> Api.trending(token) }
        PagedColumn(
            paged = paged,
            emptyText = "Nothing to show right now. Check your connection.",
            indexRow = { i -> CompactRankRow(paged.items, i) { v -> nav.push(Route.Video(v.id, v)) } }
        ) { }
    }
}
