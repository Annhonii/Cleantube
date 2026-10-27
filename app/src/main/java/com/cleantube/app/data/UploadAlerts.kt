package com.cleantube.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cleantube.app.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * "New upload" notifications for channels with the bell turned on, the way NewPipe does it:
 * a periodic background job reads each channel's public RSS feed (no login, no WebView) and
 * notifies about videos published after the last check.
 */
object UploadAlerts {
    private const val WORK = "upload_alerts"
    private const val WORK_NOW = "upload_alerts_now"
    const val CHANNEL = "uploads"

    /** (Re)schedules the periodic check, or cancels it when it has nothing to do. */
    fun sync(ctx: Context, replace: Boolean = true) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        val store = Graph.store
        // signed in: YouTube's own inbox is read AND the bells set in the app are checked; signed out: the app's bells
        if (!store.alertsEnabled || (!Session.loggedIn && store.bellIds.isEmpty())) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val req = PeriodicWorkRequestBuilder<ChannelCheckWorker>(store.alertHours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    /** "Check now" button: one immediate run. */
    fun checkNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<ChannelCheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "New uploads", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }
}

private class Upload(val id: String, val title: String, val url: String, val publishedMs: Long)

class ChannelCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val store = Graph.store
        if (!store.alertsEnabled) return Result.success()
        // signed in: YouTube's own notification inbox is the only source; the app just forwards it as phone notifications
        if (Session.loggedIn) return checkInbox()
        val channels = store.subscriptions.filter { store.isBelled(it.id) }
        var failed = 0
        for (ch in channels) {
            val uploads = try {
                fetch(ch.id)
            } catch (e: Exception) {
                failed++
                continue
            }
            if (uploads.isEmpty()) continue
            val last = store.lastSeen(ch.id)
            val newest = uploads.maxOf { it.publishedMs }
            if (last > 0) {
                // oldest first, at most 5 per channel per check so a long break doesn't flood the shade
                uploads.filter { it.publishedMs > last }.sortedBy { it.publishedMs }.takeLast(5).forEach { notifyUpload(ch, it) }
            }
            if (newest > last) store.setLastSeen(ch.id, newest)
        }
        store.lastAlertCheck = System.currentTimeMillis()
        // every channel failing usually means no real connection: let WorkManager try again soon
        return if (channels.isNotEmpty() && failed == channels.size) Result.retry() else Result.success()
    }

    /**
     * Signed in: YouTube already decides which channels notify (the bell on its own channel page), so the app reads the
     * same notification inbox as the Notification screen and shows the new uploads / live streams as phone notifications.
     */
    private suspend fun checkInbox(): Result {
        val store = Graph.store
        val items = try {
            Api.notifications(null).items
        } catch (e: Exception) {
            return Result.retry()
        }
        val seen = store.seenInbox()
        // everything the official bell shows: uploads, live streams, premieres, comments, replies, mentions, other
        val videos = items
        if (seen != null) {
            // newest last, so the phone shows them in the same order as YouTube's list
            videos.filter { it.id !in seen && !it.read }.take(10).reversed().forEach { notifyInbox(it) }
        }
        // first run only records what is already there, so old notifications never flood the phone
        val all = (seen ?: LinkedHashSet()).also { s -> videos.forEach { s.add(it.id) } }
        store.saveSeenInbox(all)
        store.lastAlertCheck = System.currentTimeMillis()
        return Result.success()
    }

    private fun notifyInbox(n: NotificationItem) {
        val ctx = applicationContext
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        UploadAlerts.ensureChannel(ctx)
        val url = when {
            n.videoId.isNotEmpty() -> "https://www.youtube.com/watch?v=${n.videoId}"
            n.channelId.isNotEmpty() -> "https://www.youtube.com/channel/${n.channelId}"
            else -> null
        }
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (url != null) open.setAction(Intent.ACTION_VIEW).setData(Uri.parse(url))
        val pi = PendingIntent.getActivity(
            ctx, n.id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(ctx, UploadAlerts.CHANNEL)
            .setSmallIcon(com.cleantube.app.R.drawable.ic_launcher_monochrome)
            .setContentTitle(
                when (n.kind) {
                    "live" -> "Live now"
                    "premiere" -> "Premiere"
                    "upload" -> "New video"
                    "comment" -> "New comment"
                    "reply" -> "New reply"
                    "mention" -> "Mention"
                    else -> "YouTube"
                }
            )
            .setContentText(n.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.message))
            .setAutoCancel(true)
            .setGroup("uploads")
            .setContentIntent(pi)
        try {
            NotificationManagerCompat.from(ctx).notify(3000 + (n.id.hashCode() and 0xFFFFF), b.build())
        } catch (e: SecurityException) {
            // permission revoked
        }
    }

    private suspend fun fetch(channelId: String): List<Upload> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            parse(r.body?.string().orEmpty())
        }
    }

    private fun parse(xml: String): List<Upload> =
        entry.findAll(xml).mapNotNull { m ->
            val body = m.groupValues[1]
            val id = tag(body, "yt:videoId") ?: return@mapNotNull null
            val href = linkHref.find(body)?.groupValues?.get(1).orEmpty()
            if ("/shorts/" in href) return@mapNotNull null
            val title = unescape(tag(body, "title").orEmpty())
            val ms = try {
                OffsetDateTime.parse(tag(body, "published").orEmpty()).toInstant().toEpochMilli()
            } catch (e: Exception) {
                return@mapNotNull null
            }
            Upload(id, title, "https://www.youtube.com/watch?v=$id", ms)
        }.toList()

    private fun tag(s: String, name: String): String? =
        Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(s)?.groupValues?.get(1)?.trim()

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")

    private fun notifyUpload(ch: ChannelItem, u: Upload) {
        val ctx = applicationContext
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        UploadAlerts.ensureChannel(ctx)
        val open = Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(u.url))
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, u.id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, UploadAlerts.CHANNEL)
            .setSmallIcon(com.cleantube.app.R.drawable.ic_launcher_monochrome)
            .setContentTitle(ch.title)
            .setContentText(u.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(u.title))
            .setWhen(u.publishedMs)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setGroup("uploads")
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(2000 + (u.id.hashCode() and 0xFFFFF), n)
        } catch (e: SecurityException) {
            // permission revoked between the check above and now
        }
    }

    private companion object {
        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val entry = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        val linkHref = Regex("<link[^>]*rel=\"alternate\"[^>]*href=\"([^\"]*)\"")
    }
}
