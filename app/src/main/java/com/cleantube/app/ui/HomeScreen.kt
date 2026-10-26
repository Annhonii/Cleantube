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
import kotlinx.coroutines.launch

/** Home: the app's own recommendations (subscribed channels first), ranked on the phone. */
@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val loggedIn = Session.loggedIn
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // The header floats over the list: the feed starts below it and scrolls up underneath while it collapses.
    Box(Modifier.fillMaxSize()) {
        val paged = TabCache.paged("home:$loggedIn:${Session.handle}:${com.cleantube.app.data.Graph.store.officialReco}") { token -> Api.home(token) }
        val headerH = collapsingHeaderHeight(Route.Home)
        PagedColumn(
            paged = paged,
            emptyText = "Nothing to show right now. Pull down to retry, or check your connection.",
            contentPadding = PaddingValues(top = headerH + 12.dp, bottom = listBottomPad()),
            refreshIndicatorTop = headerH,
            itemCount = paged.items.size,
            indexRow = { i ->
                paged.items.getOrNull(i)?.let { v ->
                    VideoRow(
                        v,
                        onClick = { nav.push(Route.Video(v.id, v)) },
                        onNotInterested = { channel ->
                            // gone for good: never shown again here, and YouTube is told too, like in its own app
                            val store = com.cleantube.app.data.Graph.store
                            if (channel) store.blockChannel(v) else store.hideVideo(v.id)
                            val token = if (channel) v.dontRecommendChannelToken else v.notInterestedToken
                            if (token.isNotEmpty()) scope.launch { Api.sendFeedback(token) }
                            paged.removeWhere { x ->
                                if (channel) (x.channelId.ifEmpty { x.channelTitle }) == (v.channelId.ifEmpty { v.channelTitle }) else x.id == v.id
                            }
                            android.widget.Toast.makeText(
                                ctx,
                                if (channel) "Won't recommend ${v.channelTitle} again" else "Won't recommend this video again",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
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
