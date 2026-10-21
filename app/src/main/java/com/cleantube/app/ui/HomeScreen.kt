package com.cleantube.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.cleantube.app.data.Api
import com.cleantube.app.data.TabCache
import com.cleantube.app.data.Session

/** YouTube's own home feed: personalized when signed in, the general one otherwise. */
@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val loggedIn = Session.loggedIn

    // The header floats over the list: the feed starts below it and scrolls up underneath while it collapses.
    Box(Modifier.fillMaxSize()) {
        val paged = TabCache.paged("home:$loggedIn:${Session.handle}") { token -> Api.home(token) }
        val headerH = collapsingHeaderHeight()
        PagedColumn(
            paged = paged,
            emptyText = "Nothing to show right now. Pull to retry from the Feed tab, or check your connection.",
            contentPadding = PaddingValues(top = headerH + 8.dp, bottom = 120.dp),
            refreshIndicatorTop = headerH,
            // fixed-height rhythm: hero, two tiles, two tiles... (see BentoFeedItem)
            indexRow = { i -> BentoFeedItem(paged.items, i) { v -> nav.push(Route.Video(v.id, v)) } }
        ) { }
        CollapsingHeader(paged.listState)
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
