package com.cleantube.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.Card as M3Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cleantube.app.data.Post
import com.cleantube.app.data.VideoItem

// ---------------------------------------------------------------- Home feed entries

/** One row of the Home feed: a video, or a community post slipped in between the videos. */
sealed interface HomeEntry {
    data class Video(val v: VideoItem) : HomeEntry
    data class PostEntry(val post: Post) : HomeEntry
}

/** A post is slipped in after every [POST_EVERY] videos. */
private const val POST_EVERY = 4

/**
 * The videos with the posts of [posts] in between: after the 4th video, then after the 8th... A post's place only depends
 * on how many posts come before it, so loading more videos never moves the rows already on screen.
 */
fun mixHome(videos: List<VideoItem>, posts: List<Post>): List<HomeEntry> {
    val out = ArrayList<HomeEntry>(videos.size + videos.size / POST_EVERY + 1)
    var next = 0
    videos.forEachIndexed { i, v ->
        out += HomeEntry.Video(v)
        if ((i + 1) % POST_EVERY == 0 && next < posts.size) out += HomeEntry.PostEntry(posts[next++])
    }
    return out
}

// ---------------------------------------------------------------- likes / dislikes (kept while the app runs)

/** Your reaction to a post: 1 = liked, -1 = disliked, 0 / missing = none. Keyed by post id, so recycled rows keep it. */
private object PostReactions {
    val state: SnapshotStateMap<String, Int> = mutableStateMapOf()
}

/** "1.2K" stays as it is; a plain number moves by [delta] (your own like on top of the count). */
private fun bump(count: String, delta: Int): String {
    val n = count.trim().replace(",", "").toIntOrNull()
    return when {
        n != null -> (n + delta).coerceAtLeast(0).toString()
        count.isEmpty() && delta > 0 -> delta.toString()
        else -> count
    }
}

private val UrlRegex = Regex("""https?://[^\s]+""")
private val TagRegex = Regex("""#[\p{L}\p{N}_]+""")

private fun postUrl(id: String) = "https://www.youtube.com/post/$id"

private fun openPostLink(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun sharePost(ctx: android.content.Context, post: Post) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, postUrl(post.id))
    runCatching { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ---------------------------------------------------------------- the card

/**
 * A community post as a Material 3 card: tonal container, 16dp corners, generous padding.
 *  top: avatar, name, time and a menu; then the text (links and #hashtags are tappable), the picture / shared video /
 *  poll with rounded borders; at the bottom the action bar: like (with count), dislike, comments (with count), share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePostCard(post: Post, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var expanded by remember(post.id) { mutableStateOf(false) }
    var menu by remember(post.id) { mutableStateOf(false) }
    var comments by remember(post.id) { mutableStateOf(false) }
    val reaction = PostReactions.state[post.id] ?: 0

    M3Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Card),
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // ---- creator
            Row(verticalAlignment = Alignment.CenterVertically) {
                val toChannel = {
                    if (post.channelId.isNotEmpty()) nav.push(Route.Channel(post.channelId))
                }
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = post.channelId.isNotEmpty()) { toChannel() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (post.avatar.isNotEmpty()) {
                        AppImage(post.avatar, Modifier.size(40.dp), CircleShape)
                    } else {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(TertiaryContainer))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            post.author, color = TextPrimary, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (post.age.isNotEmpty()) {
                            Text("Post • ${post.age}", color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
                Box {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { menu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextMuted, modifier = Modifier.size(22.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; sharePost(ctx, post) })
                        DropdownMenuItem(text = { Text("Open on YouTube") }, onClick = { menu = false; openPostLink(ctx, postUrl(post.id)) })
                        if (post.channelId.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("Go to channel") }, onClick = { menu = false; nav.push(Route.Channel(post.channelId)) })
                        }
                    }
                }
            }

            // ---- text (links and hashtags are tappable)
            if (post.text.isNotBlank()) {
                PostText(
                    post.text,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    onUrl = { openPostLink(ctx, it) },
                    onTag = { nav.push(Route.Search(it)) },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            // ---- picture / shared video
            if (post.image.isNotEmpty()) {
                val shape = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(shape)
                        .background(Hairline)
                        .clickable(enabled = post.video.isNotEmpty()) { nav.push(Route.Video(post.video)) }
                ) {
                    AppImage(post.image, Modifier.matchParentSize(), shape)
                }
            }
            if (post.video.isNotEmpty() && post.videoTitle.isNotEmpty()) {
                Text(
                    post.videoTitle, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp).clickable { nav.push(Route.Video(post.video)) }
                )
            }

            // ---- poll (the options; voting happens on YouTube)
            if (post.poll.isNotEmpty()) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.poll.split(" | ").filter { it.isNotBlank() }.forEach { option ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                                .clickable { openPostLink(ctx, postUrl(post.id)) }
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            Text(option, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // ---- action bar
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PostAction(
                    icon = if (reaction == 1) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                    label = "Like",
                    count = bump(post.likes, if (reaction == 1) 1 else 0),
                    active = reaction == 1,
                    onClick = { PostReactions.state[post.id] = if (reaction == 1) 0 else 1 }
                )
                PostAction(
                    icon = if (reaction == -1) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
                    label = "Dislike",
                    count = "",
                    active = reaction == -1,
                    onClick = { PostReactions.state[post.id] = if (reaction == -1) 0 else -1 }
                )
                PostAction(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    label = "Comments",
                    count = post.comments,
                    active = false,
                    onClick = { comments = true }
                )
                Spacer(Modifier.weight(1f))
                PostAction(
                    icon = Icons.Rounded.Share, label = "Share", count = "", active = false,
                    onClick = { sharePost(ctx, post) }
                )
            }
        }
    }

    if (comments) {
        ModalBottomSheet(
            onDismissRequest = { comments = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Bg,
            contentColor = TextPrimary
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                Text(
                    if (post.comments.isNotEmpty()) "Comments • ${post.comments}" else "Comments",
                    color = TextPrimary, style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "${post.author}${if (post.age.isNotEmpty()) " • ${post.age}" else ""}",
                    color = TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)
                )
                if (post.text.isNotBlank()) {
                    Text(
                        post.text, color = TextPrimary, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp).fillMaxWidth()
                    )
                }
                Text(
                    "YouTube doesn't give the comments of a community post to apps, so they are read and written on YouTube.",
                    color = TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)
                )
                Row(
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Accent)
                        .clickable { comments = false; openPostLink(ctx, postUrl(post.id)) }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = OnAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View comments on YouTube", color = OnAccent, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** The post text: collapsed to a few lines, tap to expand; http links and #hashtags are tappable. */
@Composable
private fun PostText(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUrl: (String) -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val linkColor = if (Bg.luminance() < 0.5f) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
    val annotated: AnnotatedString = remember(text, linkColor) {
        buildAnnotatedString {
            append(text)
            UrlRegex.findAll(text).forEach { m ->
                val end = m.range.last + 1
                addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), m.range.first, end)
                addStringAnnotation("URL", m.value, m.range.first, end)
            }
            TagRegex.findAll(text).forEach { m ->
                val end = m.range.last + 1
                addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium), m.range.first, end)
                addStringAnnotation("TAG", m.value, m.range.first, end)
            }
        }
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
        maxLines = if (expanded) Int.MAX_VALUE else 6,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
        onClick = { offset ->
            val url = annotated.getStringAnnotations("URL", offset, offset).firstOrNull()
            val tag = annotated.getStringAnnotations("TAG", offset, offset).firstOrNull()
            when {
                url != null -> onUrl(url.item)
                tag != null -> onTag(tag.item)
                else -> onToggle()
            }
        }
    )
}

/** A pill-shaped action: icon and (when there is one) a count. Active = filled with the accent; the icon pops when toggled. */
@Composable
private fun PostAction(
    icon: ImageVector,
    label: String,
    count: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(if (active) Accent else SecondaryContainer, tween(180), label = "actionBg")
    val fg by animateColorAsState(if (active) OnAccent else OnSecondaryContainer, tween(180), label = "actionFg")
    val pop by animateFloatAsState(
        if (active) 1.12f else 1f,
        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "actionPop"
    )
    Row(
        Modifier
            .height(40.dp)
            .bouncyClickable(pressedScale = 0.92f, onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = if (count.isEmpty()) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = label, tint = fg,
            modifier = Modifier.size(20.dp).graphicsLayer { scaleX = pop; scaleY = pop }
        )
        if (count.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (slideInVertically(tween(160)) { it / 2 } + fadeIn(tween(160))) togetherWith
                        (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(120)))
                },
                label = "actionCount"
            ) { c ->
                Text(c, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
