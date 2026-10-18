package com.cleantube.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Paged
import com.cleantube.app.data.YouTubeApi

private val categories: List<Pair<String, String?>> = listOf(
    "All" to null,
    "Music" to "10",
    "Gaming" to "20",
    "Entertainment" to "24",
    "Sports" to "17",
    "News" to "25"
)

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val store = Graph.store
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf<String?>(null) }

    val apiKey = store.apiKey
    val region = store.region

    Column(Modifier.fillMaxSize()) {
        TabTopBar("cleantube", brand = true)

        if (apiKey.isEmpty()) {
            ApiKeySetup(Modifier.padding(16.dp))
        } else {
            val paged = remember(apiKey, region, category) {
                Paged(scope) { token -> YouTubeApi.trending(region, category, token) }
            }
            PagedColumn(
                paged = paged,
                emptyText = "No trending videos for region $region.",
                header = {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories.size) { i ->
                                val (name, id) = categories[i]
                                Pill(name, onClick = { category = id }, active = category == id)
                            }
                        }
                    }
                }
            ) { v ->
                VideoRow(v, onClick = { nav.push(Route.Video(v.id, v)) })
            }
        }
    }
}
