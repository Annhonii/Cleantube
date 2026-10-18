package com.cleantube.app.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun formatCount(n: Long): String = when {
    n < 0 -> ""
    n < 1_000 -> n.toString()
    n < 1_000_000 -> trim1(n / 1_000.0) + "K"
    n < 1_000_000_000 -> trim1(n / 1_000_000.0) + "M"
    else -> trim1(n / 1_000_000_000.0) + "B"
}

private fun trim1(v: Double): String {
    val s = String.format(Locale.US, "%.1f", v)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

fun formatDuration(sec: Long): String {
    if (sec < 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

private val isoDuration =
    Regex("P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?")

/** "PT1H2M3S" -> seconds. Returns 0 for live streams ("P0D"). */
fun parseIsoDuration(iso: String): Long {
    val m = isoDuration.matchEntire(iso) ?: return -1
    fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
    return g(1) * 86400 + g(2) * 3600 + g(3) * 60 + g(4)
}

fun parseIsoTime(iso: String): Long {
    if (iso.length < 19) return 0L
    return try {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        f.parse(iso.substring(0, 19))?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

fun timeAgo(iso: String): String {
    val t = parseIsoTime(iso)
    if (t == 0L) return ""
    val diff = (System.currentTimeMillis() - t) / 1000
    fun u(v: Long, name: String) = "$v $name${if (v == 1L) "" else "s"} ago"
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> u(diff / 60, "minute")
        diff < 86400 -> u(diff / 3600, "hour")
        diff < 86400 * 30 -> u(diff / 86400, "day")
        diff < 86400 * 365 -> u(diff / (86400 * 30), "month")
        else -> u(diff / (86400 * 365), "year")
    }
}

private val numericEntity = Regex("&#(\\d+);")

/** search.list returns titles with HTML entities (&amp; &#39; ...). */
fun unescapeHtml(s: String): String =
    numericEntity.replace(s) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value }
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")

/** "UCxxxx" channel id -> its automatic "all uploads" playlist "UUxxxx". */
fun uploadsPlaylistOf(channelId: String): String =
    if (channelId.startsWith("UC")) "UU" + channelId.substring(2) else channelId
