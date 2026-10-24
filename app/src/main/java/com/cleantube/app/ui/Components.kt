package com.cleantube.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.sync.withPermit
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cleantube.app.data.ApiException
import com.cleantube.app.data.ChannelItem
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Paged
import com.cleantube.app.data.PlaylistItem
import com.cleantube.app.data.Playback
import com.cleantube.app.data.VideoItem
import com.cleantube.app.data.formatCount
import com.cleantube.app.data.formatDuration
import com.cleantube.app.data.timeAgo

// ------------------------------------------------------------------ basics

@Composable
fun AppImage(url: String, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    // the picture fades in over the grey tile instead of popping in
    var loaded by remember(url) { mutableStateOf(false) }
    val fade by animateFloatAsState(if (loaded) 1f else 0f, tween(300), label = "img")
    Box(modifier.clip(shape).background(Hairline)) {
        AsyncImage(
            model = url.ifEmpty { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            modifier = Modifier.matchParentSize().alpha(fade)
        )
    }
}

/** Rounded button / chip. [active] = inverted (white pill, black text). */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false
) {
    val bg by animateColorAsState(if (active) Accent else SecondaryContainer, tween(240), label = "pillBg")
    val fg by animateColorAsState(if (active) OnAccent else OnSecondaryContainer, tween(240), label = "pillFg")
    val edge by animateColorAsState(if (active) Accent else Hairline, tween(240), label = "pillEdge")
    Row(
        modifier = modifier
            .bouncyClickable(pressedScale = 0.92f, onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
        }
        if (text.isNotEmpty()) {
            Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Card)
            .heightIn(min = 46.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, color = TextMuted, fontSize = 15.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontFamily = AppFont),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorBox(error: ApiException, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val message = when (error.kind) {
        ApiException.Kind.NO_KEY -> "Add your free YouTube API key to start."
        ApiException.Kind.BAD_KEY -> "YouTube rejected your API key.\n${error.message.orEmpty()}"
        ApiException.Kind.QUOTA ->
            "Today's free YouTube API quota is used up. It resets around 12:30 PM IST."
        ApiException.Kind.NETWORK -> "No internet connection."
        ApiException.Kind.COMMENTS_OFF -> "Comments are turned off for this video."
        else -> error.message ?: "Something went wrong."
    }
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(message, color = TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        val needsKey = error.kind == ApiException.Kind.NO_KEY || error.kind == ApiException.Kind.BAD_KEY
        if (needsKey) {
            Pill("Open settings", onClick = { nav.push(Route.Settings) }, active = true)
        } else if (onRetry != null && error.kind != ApiException.Kind.COMMENTS_OFF) {
            Pill("Retry", onClick = onRetry, active = true)
        }
    }
}

// ----------------------------------------------------------------- lists

@Composable
fun LoadMoreEffect(state: LazyListState, paged: Paged<*>) {
    val nearEnd by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, paged.items.size, paged.loading) {
        if (nearEnd && paged.error == null) paged.loadMore()
    }
}

/** Pull down on the list to refresh it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorTop: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = pullState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = indicatorTop),
                containerColor = Card,
                color = Accent
            )
        }
    ) { content() }
}

/** Room under a list: a little air plus the system navigation bar (there is no floating bar any more). */
@Composable
fun listBottomPad(): androidx.compose.ui.unit.Dp =
    androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp

@Composable
fun <T> PagedColumn(
    paged: Paged<T>,
    modifier: Modifier = Modifier,
    state: LazyListState = paged.listState,
    emptyText: String = "Nothing here yet",
    contentPadding: PaddingValues = PaddingValues(bottom = listBottomPad()),
    /** Where the pull-to-refresh spinner appears (below a header that floats over the list). */
    refreshIndicatorTop: androidx.compose.ui.unit.Dp = 0.dp,
    header: (LazyListScope.() -> Unit)? = null,
    /** Extra work on pull-to-refresh (Home refreshes its Quick picks too). */
    onRefresh: (() -> Unit)? = null,
    /** When set, the item at index i is drawn by this instead of [row] (used by the bento feed). */
    indexRow: (@Composable (Int) -> Unit)? = null,
    row: @Composable (T) -> Unit = {}
) {
    // only the very first time: a list that is already loaded (cached) is not fetched again
    LaunchedEffect(paged) { if (paged.items.isEmpty() && !paged.loading) paged.loadMore() }
    LoadMoreEffect(state, paged)
    // rows that were already shown don't animate again when scrolled back into view
    val seen = paged.seen
    // fast start: once the list rests for a moment, prepare the links of the videos that are on screen
    LaunchedEffect(state, paged) {
        snapshotFlow { state.firstVisibleItemIndex }.collectLatest { first ->
            kotlinx.coroutines.delay(200)
            paged.items.drop(first).take(7).filterIsInstance<VideoItem>().filter { !it.short && !it.live && it.durationSec !in 1..62 }
                .forEach { com.cleantube.app.data.Playback.prefetch(it.id) }
        }
    }

    PullRefresh(
        refreshing = paged.refreshing,
        onRefresh = { paged.pullRefresh(); onRefresh?.invoke() },
        modifier = modifier.fillMaxSize(),
        indicatorTop = refreshIndicatorTop
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = state, contentPadding = contentPadding) {
            header?.invoke(this)
            items(paged.items.size) { i ->
                Box(Modifier.entrance(seen, i, if (i < 6) 60 + i * 55 else 0)) {
                    if (indexRow != null) indexRow(i) else row(paged.items[i])
                }
            }
            item {
                val err = paged.error
                if (err != null) {
                    ErrorBox(err, onRetry = { paged.loadMore() })
                } else if (paged.loading) {
                    if (paged.items.isEmpty()) {
                        // first page still loading: shimmering placeholders instead of a lonely spinner
                        Column {
                            repeat(3) { VideoRowSkeleton() }
                        }
                    } else {
                        Loading()
                    }
                } else if (paged.items.isEmpty() && paged.finished) {
                    EmptyState(emptyText)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ rows

internal fun videoMeta(v: VideoItem): String =
    listOf(
        v.viewsText.ifEmpty { if (v.views >= 0) "${formatCount(v.views)} views" else "" },
        v.ageText.ifEmpty { timeAgo(v.publishedAt) }
    ).filter { it.isNotEmpty() }.joinToString(" • ")

@Composable
fun VideoRow(
    v: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null
) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    // the channel logo: from the feed when it has it, otherwise looked up once per channel
    val avatar by produceState(ChannelAvatars.known(v.channelId, v.channelThumb), v.channelId, v.channelThumb) {
        if (value.isEmpty() && v.channelId.isNotEmpty()) value = ChannelAvatars.load(v.channelId)
    }

    // YouTube style card: thumbnail with the channel pill (logo + name) top left, duration top right and a
    // NEW badge bottom left; below it the title and "views • time" with the menu button on the right.
    val thumbC = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val isNew = remember(v.id, v.ageText) {
        !v.live && com.cleantube.app.data.Api.ageMinutes(v.ageText) <= 2 * 1440L &&
            com.cleantube.app.data.Graph.store.history.none { it.id == v.id }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .prefetchOnPress(v.id)
            .plainClickable { thumbC[0]?.takeIf { it.isAttached }?.let { VideoHost.rememberOrigin(it.boundsInWindow()) }; onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onGloballyPositioned { thumbC[0] = it }
                .clip(RoundedCornerShape(16.dp))
                .background(Hairline)
        ) {
            VideoThumb(v.id, Modifier.fillMaxSize())
            if (v.channelTitle.isNotEmpty()) {
                ChannelPill(
                    v.channelTitle,
                    avatar,
                    Modifier.align(Alignment.TopStart).padding(10.dp).widthIn(max = 240.dp),
                    onClick = if (v.channelId.isNotEmpty()) ({ nav.push(Route.Channel(v.channelId)) }) else null
                )
            }
            if (v.live) {
                Badge("LIVE", Color(0xFFE53935), Modifier.align(Alignment.BottomEnd).padding(10.dp))
            } else if (v.durationSec > 0) {
                Text(
                    formatDuration(v.durationSec),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            if (isNew) {
                Text(
                    "NEW",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                        .background(Color(0xFF2BD34F))
                        .padding(start = 12.dp, end = 14.dp, top = 3.dp, bottom = 3.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    v.title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = videoMeta(v)
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.size(5.dp))
                    Text(meta, color = TextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextMuted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Save to playlist") },
                        onClick = { menu = false; saveDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = { menu = false; shareVideo(ctx, v.id) }
                    )
                    if (v.channelId.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Go to channel") },
                            onClick = { menu = false; nav.push(Route.Channel(v.channelId)) }
                        )
                    }
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = { menu = false; onRemove() }
                        )
                    }
                }
            }
        }
    }
    if (saveDialog) SaveToPlaylistDialog(v) { saveDialog = false }
}

/** Sharp 720p thumbnail when YouTube has one, otherwise the standard medium one. */
@Composable
internal fun VideoThumb(id: String, modifier: Modifier = Modifier) {
    var fallback by remember(id) { mutableStateOf(false) }
    // no fade / zoom: the picture simply shows when it is there
    AsyncImage(
        model = if (fallback) "https://i.ytimg.com/vi/$id/mqdefault.jpg" else "https://i.ytimg.com/vi/$id/hq720.jpg",
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { fallback = true },
        modifier = modifier
    )
}

/** Small frosted pill with the channel's initial and name, like the reference design. */
@Composable
private fun ChannelPill(name: String, thumb: String = "", modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xB3FFFFFF))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(Accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "•",
                color = OnAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            // the logo covers the initial once it has loaded
            if (thumb.isNotEmpty()) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        // fixed dark text: the pill is always a light frosted chip on top of the thumbnail
        Text(
            name,
            color = Color(0xFF1B1B1F),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun Badge(text: String, bg: Color, modifier: Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun ChannelRow(c: ChannelItem, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(24.dp))
            .background(Card)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppImage(c.thumb, Modifier.size(52.dp), CircleShape)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = c.subsText.ifEmpty { if (c.subs >= 0) "${formatCount(c.subs)} subscribers" else c.description }
            if (sub.isNotEmpty()) {
                Text(sub, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun PlaylistRow(p: PlaylistItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(24.dp))
            .background(Card)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(144.dp).aspectRatio(16f / 9f)) {
            AppImage(p.thumb, Modifier.fillMaxSize())
            if (p.countText.isNotEmpty()) Badge(p.countText, Color(0xAA000000), Modifier.align(Alignment.BottomEnd).padding(5.dp))
            else if (p.count >= 0) Badge("${p.count} videos", Color(0xAA000000), Modifier.align(Alignment.BottomEnd).padding(5.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(4.dp))
            Text(p.channelTitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- dialog

@Composable
fun SaveToPlaylistDialog(video: VideoItem, onDismiss: () -> Unit) {
    val store = Graph.store
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text("Save to playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                store.playlists.forEach { p ->
                    val inList = p.videos.any { it.id == video.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { store.toggleInPlaylist(p.id, video) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = inList,
                            onCheckedChange = { store.toggleInPlaylist(p.id, video) },
                            colors = CheckboxDefaults.colors(checkedColor = Accent)
                        )
                        Text(p.name, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillTextField(newName, { newName = it }, "New playlist", Modifier.weight(1f))
                    Pill(
                        "Add",
                        onClick = {
                            if (newName.isNotBlank()) {
                                store.createPlaylist(newName, video)
                                newName = ""
                            }
                        },
                        active = true
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = TextPrimary) } }
    )
}

/** Channel logos by channel id: remembered from feeds that carry them, fetched (two at a time) for the rest. */
object ChannelAvatars {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val gate = kotlinx.coroutines.sync.Semaphore(2)

    /** [fromFeed] when the feed had the logo (and remember it), otherwise what was learned earlier, otherwise "". */
    fun known(channelId: String, fromFeed: String): String {
        if (fromFeed.isNotEmpty()) {
            if (channelId.isNotEmpty()) cache[channelId] = fromFeed
            return fromFeed
        }
        return cache[channelId].orEmpty()
    }

    suspend fun load(channelId: String): String {
        cache[channelId]?.let { return it }
        return gate.withPermit { fetch(channelId) }
    }

    private suspend fun fetch(channelId: String): String {
        cache[channelId]?.let { return it }
        val t = try {
            com.cleantube.app.data.Api.channel(channelId).thumb
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
        if (t.isNotEmpty()) cache[channelId] = t
        return t
    }
}

/**
 * Looks up the stream links of the first few videos of a list in the background, so tapping one starts playing
 * without waiting for that lookup (links only, no video data is loaded). Shorts and live streams are skipped.
 */
@Composable
fun PrefetchTop(items: List<VideoItem>, count: Int = 5) {
    val ids = items.asSequence().filter { !it.short && !it.live && it.durationSec !in 1..62 }.take(count).map { it.id }.toList()
    LaunchedEffect(ids) { ids.forEach { Playback.prefetch(it) } }
}
