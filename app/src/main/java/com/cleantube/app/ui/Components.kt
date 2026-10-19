package com.cleantube.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.cleantube.app.data.VideoItem
import com.cleantube.app.data.formatCount
import com.cleantube.app.data.formatDuration
import com.cleantube.app.data.timeAgo

// ------------------------------------------------------------------ basics

@Composable
fun AppImage(url: String, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    AsyncImage(
        model = url.ifEmpty { null },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(shape)
            .background(Hairline)
    )
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
    val bg = if (active) TextPrimary else Card
    val fg = if (active) Bg else TextPrimary
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .border(BorderStroke(1.dp, if (active) bg else Hairline), CircleShape)
            .clickable(onClick = onClick)
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
            .border(BorderStroke(1.dp, Hairline), CircleShape)
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
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontFamily = Geist),
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
        CircularProgressIndicator(color = TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
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

@Composable
fun <T> PagedColumn(
    paged: Paged<T>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    emptyText: String = "Nothing here yet",
    contentPadding: PaddingValues = PaddingValues(bottom = 120.dp),
    header: (LazyListScope.() -> Unit)? = null,
    row: @Composable (T) -> Unit
) {
    LaunchedEffect(paged) { paged.loadMore() }
    LoadMoreEffect(state, paged)

    LazyColumn(modifier = modifier.fillMaxSize(), state = state, contentPadding = contentPadding) {
        header?.invoke(this)
        items(paged.items.size) { i -> row(paged.items[i]) }
        item {
            val err = paged.error
            if (err != null) {
                ErrorBox(err, onRetry = { paged.loadMore() })
            } else if (paged.loading) {
                Loading()
            } else if (paged.items.isEmpty() && paged.finished) {
                EmptyState(emptyText)
            }
        }
    }
}

// ------------------------------------------------------------------ rows

private fun videoMeta(v: VideoItem): String =
    listOf(
        v.viewsText.ifEmpty { if (v.views >= 0) "${formatCount(v.views)} views" else "" },
        v.ageText.ifEmpty { timeAgo(v.publishedAt) }
    ).filter { it.isNotEmpty() }.joinToString(" • ")

@Composable
fun VideoRow(
    v: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    container: Color = Card,
    square: Boolean = false
) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(if (square) RectangleShape else RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(144.dp).aspectRatio(16f / 9f)) {
            AppImage(v.thumb, Modifier.fillMaxSize(), if (square) RectangleShape else RoundedCornerShape(12.dp))
            if (v.live) {
                Badge("LIVE", Accent, Modifier.align(Alignment.BottomEnd).padding(5.dp))
            } else if (v.durationSec > 0) {
                Badge(formatDuration(v.durationSec), Color(0xAA000000), Modifier.align(Alignment.BottomEnd).padding(5.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                v.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(4.dp))
            Text(v.channelTitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(videoMeta(v), color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    if (saveDialog) SaveToPlaylistDialog(v) { saveDialog = false }
}

@Composable
private fun Badge(text: String, bg: Color, modifier: Modifier) {
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
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .clickable(onClick = onClick)
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
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .clickable(onClick = onClick)
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
