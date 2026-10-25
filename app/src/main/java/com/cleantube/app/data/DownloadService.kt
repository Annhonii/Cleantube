package com.cleantube.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cleantube.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps downloads alive while the app is in the background.
 * Shows one live notification (percent, size, speed, time left, Cancel button) and a separate
 * "finished" / "failed" notification for every download.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels()

        if (intent?.action == ACTION_CANCEL) {
            // Tapped "Cancel" on the notification: stop the running download; the worker loop moves on / ends.
            Downloads.items.firstOrNull { it.status == DlStatus.RUNNING }?.let { Downloads.cancel(it) }
            if (worker?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground(buildProgress(null))
        if (worker?.isActive != true) {
            worker = scope.launch {
                while (true) {
                    val next = Downloads.items.lastOrNull { it.status == DlStatus.QUEUED } ?: break
                    Downloads.run(applicationContext, next) { notifyProgress(next) }
                    postResult(next)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notifyProgress(item: DownloadItem) {
        val now = System.currentTimeMillis()
        if (now - lastNotify < 700) return
        lastNotify = now
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildProgress(item))
    }

    private fun openApp(): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** "1:20 left  •  45%  •  12.3 MB of 27.1 MB  •  3.2 MB/s" */
    private fun progressText(item: DownloadItem): String {
        if (item.phase.isNotEmpty()) return item.phase
        val pct = (item.progress * 100).toInt()
        val eta = if (item.etaSec >= 0) "${formatEta(item.etaSec)} left" else "calculating time left…"
        // time left goes first: the collapsed notification cuts the line short, and that is the part people look for
        return listOf(
            eta,
            "$pct%",
            if (item.total > 0) "${formatBytes(item.bytes)} of ${formatBytes(item.total)}" else formatBytes(item.bytes),
            formatSpeed(item.speed)
        ).filter { it.isNotEmpty() }.joinToString("  •  ")
    }

    private fun buildProgress(item: DownloadItem?): Notification {
        val waiting = Downloads.items.count { it.status == DlStatus.QUEUED }
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item?.title ?: "Starting download…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
            .addAction(0, "Cancel", cancel)
        if (waiting > 0) b.setSubText("$waiting more waiting")
        if (item == null) {
            b.setProgress(100, 0, true)
        } else {
            val text = progressText(item)
            b.setContentText(text)
            b.setStyle(NotificationCompat.BigTextStyle().bigText(text))
            val indeterminate = item.phase.isNotEmpty() || item.progress <= 0f
            b.setProgress(100, (item.progress * 100).toInt(), indeterminate)
        }
        return b.build()
    }

    /** A normal (swipe-away) notification when a download ends. */
    private fun postResult(item: DownloadItem) {
        val (title, text, icon) = when (item.status) {
            DlStatus.DONE -> Triple("Download finished", item.title, android.R.drawable.stat_sys_download_done)
            DlStatus.FAILED -> Triple("Download failed", "${item.title}\n${item.error}", android.R.drawable.stat_notify_error)
            else -> return // cancelled: nothing to announce
        }
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        getSystemService(NotificationManager::class.java).notify(RESULT_BASE + (item.id.hashCode() and 0xFFFF), n)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Download progress", NotificationManager.IMPORTANCE_LOW))
            }
            if (nm.getNotificationChannel(CHANNEL_DONE) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val CHANNEL_DONE = "downloads_done"
        const val NOTIF_ID = 42
        const val RESULT_BASE = 1000
        const val ACTION_CANCEL = "com.cleantube.app.CANCEL_DOWNLOAD"
    }
}
