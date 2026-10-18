package com.cleantube.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cleantube.app.data.Graph
import com.cleantube.app.data.VideoItem
import kotlin.math.min
import kotlin.math.roundToInt

private val HeartRed = Color(0xFFFF3B5C)
private val LikeBlue = Color(0xFF2F6BFF)
private val HashBlue = Color(0xFF3EA6FF)
private val BarFill = Color(0xB3E6E6E6)
private val BarInk = Color(0xFF1A1A1A)

private class Burst(val id: Int, val x: Float, val y: Float)
private class RippleEvent(val id: Int, val side: Int, val at: Offset)
private class FlashEvent(val id: Int, val showPlay: Boolean)

/**
 * One full-screen Short: thumbnail (until the video is really playing), touch gestures, the right-hand action bar,
 * and the channel / caption / audio block at the bottom-left.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShortsPage(
    video: VideoItem,
    meta: ShortMeta?,
    isActive: Boolean,
    live: Boolean,
    player: ShortsPlayer,
    reaction: Int,
    onReact: (Int) -> Unit,
    onComments: () -> Unit,
    onMenu: (Boolean) -> Unit,
    onShare: () -> Unit,
    onRemix: () -> Unit,
    onOpenChannel: () -> Unit,
    onSubscribe: () -> Unit,
    onRetry: () -> Unit,
    onOpenInYouTube: () -> Unit
) {
    val bursts = remember { mutableStateListOf<Burst>() }
    var nextId by remember { mutableIntStateOf(0) }
    var ripple by remember { mutableStateOf<RippleEvent?>(null) }
    var flash by remember { mutableStateOf<FlashEvent?>(null) }
    var captionExpanded by remember { mutableStateOf(false) }

    val activeNow by rememberUpdatedState(isActive)
    val reactionNow by rememberUpdatedState(reaction)
    val onReactNow by rememberUpdatedState(onReact)
    val onMenuNow by rememberUpdatedState(onMenu)

    val channel = meta?.channel
    val rawHandle = channel?.handle.orEmpty()
    val handle = when {
        rawHandle.isEmpty() -> video.channelTitle
        rawHandle.startsWith("@") -> rawHandle
        else -> "@$rawHandle"
    }
    val avatar = channel?.thumb.orEmpty()
    val channelId = channel?.id.orEmpty().ifEmpty { video.channelId }
    val canSubscribe = channelId.isNotEmpty()
    val subscribed = canSubscribe && Graph.store.isSubscribed(channelId)
    val description = meta?.details?.description.orEmpty()
    val likeText = meta?.details?.likesText.orEmpty().ifEmpty { "Like" }
    val commentText = meta?.details?.commentCountText.orEmpty().ifEmpty { "Comments" }

    BoxWithConstraints(Modifier.fillMaxSize().background(if (live) Color.Transparent else Color.Black)) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()

        fun burst(x: Float, y: Float) {
            nextId += 1
            bursts.add(Burst(nextId, x, y))
        }

        if (!live) {
            AsyncImage(
                model = "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg",
                contentDescription = null,
                contentScale = if (video.short) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // dark fade behind the bottom text
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(300.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
        )

        // ---- touch gestures (below the buttons, above the video)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(video.id) {
                    detectTapGestures(
                        onTap = {
                            if (activeNow) {
                                val willPlay = !player.playing
                                player.togglePlay()
                                nextId += 1
                                flash = FlashEvent(nextId, willPlay)
                            }
                        },
                        onDoubleTap = { off ->
                            if (activeNow) {
                                val third = size.width / 3f
                                when {
                                    off.x < third -> {
                                        player.seekBy(-5f)
                                        nextId += 1
                                        ripple = RippleEvent(nextId, -1, off)
                                    }
                                    off.x > 2f * third -> {
                                        player.seekBy(5f)
                                        nextId += 1
                                        ripple = RippleEvent(nextId, 1, off)
                                    }
                                    else -> {
                                        if (reactionNow != 1) onReactNow(1)
                                        burst(off.x, off.y)
                                    }
                                }
                            }
                        },
                        onLongPress = { if (activeNow) onMenuNow(false) }
                    )
                }
        )

        // ---- feedback animations
        ripple?.let { key(it.id) { SeekRipple(it) } }
        flash?.let { key(it.id) { CenterFlash(it) } }
        bursts.forEach { b -> key(b.id) { HeartBurst(b) { bursts.remove(b) } } }

        // ---- loading / error
        if (isActive && player.failedId == video.id) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("This Short can't be played inside the app.", color = Color.White, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Retry", onClick = onRetry)
                    Pill("Open in YouTube", onClick = onOpenInYouTube, active = true)
                }
            }
        } else if (isActive && !player.started) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(30.dp)
            )
        }

        // ---- right-hand action bar + sound disk
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(34.dp))
                    .background(BarFill)
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MoreRing { onMenu(true) }
                ActionItem(
                    icon = if (reaction == 1) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    label = likeText,
                    tint = if (reaction == 1) LikeBlue else BarInk,
                    description = "Like"
                ) {
                    if (reaction == 1) {
                        onReact(0)
                    } else {
                        onReact(1)
                        burst(wPx - 120f, hPx * 0.6f)
                    }
                }
                ActionItem(
                    icon = Icons.Rounded.ThumbDown,
                    label = "Dislike",
                    tint = if (reaction == -1) LikeBlue else BarInk,
                    description = "Dislike"
                ) { onReact(if (reaction == -1) 0 else -1) }
                ActionItem(Icons.Rounded.ChatBubbleOutline, commentText, BarInk, "Comments", onComments)
                ActionItem(Icons.Rounded.Share, "Share", BarInk, "Share", onShare)
                ActionItem(Icons.Rounded.ContentCut, "Remix", BarInk, "Remix", onRemix)
            }
            SoundDisk(
                url = avatar.ifEmpty { video.thumb },
                spinning = isActive && player.playing,
                onClick = { onMenu(true) }
            )
        }

        // ---- bottom-left: channel, caption, audio
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 88.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppImage(
                    url = avatar,
                    modifier = Modifier.size(38.dp).clickable(onClick = onOpenChannel),
                    shape = CircleShape
                )
                Text(
                    handle,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable(onClick = onOpenChannel)
                )
                if (canSubscribe) SubscribeButton(subscribed, onSubscribe)
            }
            Caption(
                title = video.title,
                description = description,
                expanded = captionExpanded,
                onToggle = { captionExpanded = !captionExpanded }
            )
            Row(
                Modifier
                    .widthIn(max = 260.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickable { onMenu(true) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Original audio - ${video.channelTitle.ifEmpty { handle }}",
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    description: String,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .widthIn(min = 46.dp, max = 64.dp)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(30.dp))
        if (label.isNotEmpty()) {
            Text(label, color = BarInk, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun MoreRing(onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .border(BorderStroke(2.dp, BarInk), CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = BarInk, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SoundDisk(url: String, spinning: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "disk")
    val rot by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "rot"
    )
    AppImage(
        url = url,
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer { rotationZ = if (spinning) rot else 0f }
            .border(BorderStroke(2.dp, Color.White), CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape
    )
}

@Composable
private fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (subscribed) Color(0x66000000) else Color.White)
            .border(if (subscribed) BorderStroke(1.dp, Color(0x66FFFFFF)) else BorderStroke(0.dp, Color.Transparent), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (subscribed) {
            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (subscribed) "Subscribed" else "Subscribe",
            color = if (subscribed) Color.White else Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

private fun styledCaption(t: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    Regex("#[\\p{L}\\p{N}_]+").findAll(t).forEach { m ->
        append(t.substring(last, m.range.first))
        withStyle(SpanStyle(color = HashBlue, fontWeight = FontWeight.Bold)) { append(m.value) }
        last = m.range.last + 1
    }
    append(t.substring(last))
}

/** Two lines with an "...more" link; tap to expand to the full description, tap again to fold. */
@Composable
private fun Caption(title: String, description: String, expanded: Boolean, onToggle: () -> Unit) {
    val text = if (expanded && description.isNotBlank()) description else title
    var overflow by remember(text, expanded) { mutableStateOf(false) }
    val hasMore = !expanded && (overflow || description.trim().length > title.trim().length + 8)

    Column(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
    ) {
        Text(
            styledCaption(text),
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { overflow = it.hasVisualOverflow },
            modifier = if (expanded) Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()) else Modifier
        )
        if (hasMore) Text("...more", color = Color(0xFFCCCCCC), fontSize = 14.sp)
        if (expanded) Text("less", color = Color(0xFFCCCCCC), fontSize = 14.sp)
    }
}

// ------------------------------------------------------------------ animations

@Composable
private fun CenterFlash(e: FlashEvent) {
    val a = remember(e.id) { Animatable(1f) }
    LaunchedEffect(e.id) { a.animateTo(0f, tween(650)) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(76.dp)
                .graphicsLayer {
                    alpha = a.value
                    val s = 0.8f + 0.4f * (1f - a.value)
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(Color(0x66000000)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (e.showPlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

@Composable
private fun SeekRipple(e: RippleEvent) {
    val p = remember(e.id) { Animatable(0f) }
    LaunchedEffect(e.id) { p.animateTo(1f, tween(550)) }
    Canvas(Modifier.fillMaxSize()) {
        val f = p.value
        drawCircle(
            color = Color.White.copy(alpha = 0.30f * (1f - f)),
            radius = 56.dp.toPx() + 110.dp.toPx() * f,
            center = e.at
        )
    }
    Box(
        Modifier.fillMaxSize().padding(horizontal = 36.dp),
        contentAlignment = if (e.side < 0) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Text(
            if (e.side < 0) "-5s" else "+5s",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { alpha = 1f - p.value }
        )
    }
}

private val heartDx = floatArrayOf(-150f, -90f, -40f, 0f, 40f, 90f, 150f)
private val heartRise = floatArrayOf(380f, 480f, 560f, 620f, 540f, 450f, 360f)
private val heartRot = floatArrayOf(-24f, -14f, -6f, 0f, 8f, 16f, 26f)

/** A handful of hearts floating up from where the like happened. */
@Composable
private fun HeartBurst(b: Burst, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    val d = LocalDensity.current.density
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing))
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        for (i in heartDx.indices) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = HeartRed,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (b.x + heartDx[i] * d * p.value - 15.dp.toPx()).roundToInt(),
                            (b.y - heartRise[i] * d * p.value - 15.dp.toPx()).roundToInt()
                        )
                    }
                    .size(30.dp)
                    .graphicsLayer {
                        alpha = (1f - p.value).coerceIn(0f, 1f)
                        rotationZ = heartRot[i]
                        val s = 0.6f + 0.8f * min(1f, p.value * 4f)
                        scaleX = s
                        scaleY = s
                    }
            )
        }
    }
}

// ------------------------------------------------------------------ progress / scrub bar

/** Ultra-slim playback bar on the bottom edge. Drag (or tap) anywhere along it to jump to that point. */
@Composable
internal fun ShortsProgressBar(progress: () -> Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    val onSeekNow by rememberUpdatedState(onSeek)

    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { off -> onSeekNow((off.x / size.width).coerceIn(0f, 1f)) })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        dragging = true
                        dragFrac = (off.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeekNow(dragFrac)
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        dragFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                        change.consume()
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val thick = (if (dragging) 6.dp else 2.dp).toPx()
            val y = size.height - thick
            val f = if (dragging) dragFrac else progress().coerceIn(0f, 1f)
            drawRect(Color.White.copy(alpha = 0.28f), topLeft = Offset(0f, y), size = Size(size.width, thick))
            drawRect(Color.White, topLeft = Offset(0f, y), size = Size(size.width * f, thick))
            if (dragging) drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(size.width * f, y + thick / 2f))
        }
    }
}
