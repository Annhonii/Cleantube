package com.cleantube.app.ui

import androidx.compose.foundation.layout.Column
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

    Column(Modifier.fillMaxSize()) {
        TabTopBar("cleantube", brand = true)
        val paged = TabCache.paged("home:$loggedIn:${Session.handle}") { token -> Api.home(token) }
        PagedColumn(
            paged = paged,
            emptyText = "Nothing to show right now. Pull to retry from the Feed tab, or check your connection."
        ) { v ->
            VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
        }
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
            emptyText = "Nothing to show right now. Check your connection."
        ) { v ->
            VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
        }
    }
}
