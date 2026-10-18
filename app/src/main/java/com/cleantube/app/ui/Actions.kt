package com.cleantube.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        ctx.startActivity(Intent.createChooser(send, null))
    } catch (e: ActivityNotFoundException) {
        // nothing can share
    }
}

fun shareVideo(ctx: Context, videoId: String) = shareText(ctx, "https://youtu.be/$videoId")

/** Opens the official YouTube app if installed, otherwise the browser. */
fun openInYouTube(ctx: Context, videoId: String) {
    val uri = Uri.parse("https://www.youtube.com/watch?v=$videoId")
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube"))
    } catch (e: ActivityNotFoundException) {
        openUrl(ctx, uri.toString())
    }
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // no browser
    }
}

fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun readClipboard(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
}

/** Turns a YouTube URL (watch, youtu.be, shorts, embed, channel, @handle, playlist) into a route. */
fun routeFromUri(uri: Uri): Route? {
    val host = uri.host ?: return null
    val segs = uri.pathSegments
    if (host == "youtu.be") {
        return segs.firstOrNull()?.let { Route.Video(it) }
    }
    if (!host.endsWith("youtube.com")) return null

    val v = uri.getQueryParameter("v")
    val list = uri.getQueryParameter("list")
    val first = segs.firstOrNull().orEmpty()
    return when {
        first == "watch" && !v.isNullOrEmpty() -> Route.Video(v)
        (first == "shorts" || first == "embed" || first == "live") && segs.size > 1 -> Route.Video(segs[1])
        first == "playlist" && !list.isNullOrEmpty() -> Route.Playlist(list)
        first == "channel" && segs.size > 1 -> Route.Channel(segs[1])
        first.startsWith("@") -> Route.Channel(id = "", handle = first)
        else -> null
    }
}
