package com.cleantube.app

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.cleantube.app.ui.PipHost

/**
 * The floating popup window (picture in picture):
 *  - opens by itself when the user leaves the app while a video plays (Android 12+: from the Home gesture with the
 *    system's own smooth animation, older phones: from onUserLeaveHint)
 *  - the system grows the window out of the little picture of the mini player (source rect hint)
 *  - three buttons inside the popup: quality, speed (both show the current value) and fullscreen
 */
class PipSupport(private val act: ComponentActivity) {

    private val supported: Boolean
        get() = Build.VERSION.SDK_INT >= 26 &&
            act.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val c = PipHost.controller ?: return
            when (intent.getStringExtra(EXTRA)) {
                "speed" -> c.cycleSpeed()
                "quality" -> c.cycleQuality()
                "full" -> {
                    c.openFullscreen()
                    // bring the app back out of the popup window
                    try {
                        act.startActivity(
                            Intent(act, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                    } catch (_: Exception) {
                    }
                }
            }
            update()
        }
    }
    private var registered = false

    fun attach() {
        PipHost.refresh = { update() }
        if (!supported || registered) return
        ContextCompat.registerReceiver(act, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun detach() {
        PipHost.refresh = null
        if (registered) {
            try { act.unregisterReceiver(receiver) } catch (_: Exception) {}
            registered = false
        }
    }

    /** Re-sends buttons, picture rectangle and the auto-open flag. Cheap; safe to call at any time. */
    fun update() {
        if (!supported) return
        try {
            act.setPictureInPictureParams(params())
        } catch (_: Exception) {
        }
    }

    /** Opens the popup now (the "Popup" button, or leaving the app). Returns false if it couldn't. */
    fun enter(): Boolean {
        if (!supported) return false
        return try {
            act.enterPictureInPictureMode(params())
        } catch (_: Exception) {
            false
        }
    }

    /** Pre Android 12: called from onUserLeaveHint. (Android 12+ opens it by itself, with a nicer animation.) */
    fun onUserLeaveHint(enabled: Boolean) {
        if (Build.VERSION.SDK_INT in 26..30 && enabled && PipHost.autoEnter && !act.isInPictureInPictureMode) enter()
    }

    private fun params(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        sourceRect()?.let { b.setSourceRectHint(it) }
        b.setActions(actions())
        if (Build.VERSION.SDK_INT >= 31) {
            b.setAutoEnterEnabled(PipHost.autoEnter && com.cleantube.app.data.Graph.store.autoPopup)
            b.setSeamlessResizeEnabled(false) // video: cross-fade while resizing instead of stretching
            PipHost.controller?.let {
                b.setTitle(it.title.ifEmpty { null })
                b.setSubtitle(it.channel.ifEmpty { null })
            }
        }
        return b.build()
    }

    private fun sourceRect(): Rect? {
        val r = PipHost.sourceRect ?: return null
        val win = act.window?.decorView ?: return null
        val out = Rect(r)
        if (win.width <= 0 || win.height <= 0) return null
        if (!out.intersect(0, 0, win.width, win.height) || out.width() < 8 || out.height() < 8) return null
        return out
    }

    private fun actions(): List<RemoteAction> {
        val c = PipHost.controller ?: return emptyList()
        val max = if (Build.VERSION.SDK_INT >= 26) act.maxNumPictureInPictureActions else 0
        // same options as the mini player: quality + speed (the "settings" values), and fullscreen
        val list = ArrayList<RemoteAction>(3)
        list += action("quality", labelIcon(c.qualityLabel), "Quality ${c.qualityLabel}")
        list += action("speed", labelIcon(c.speedLabel), "Speed ${c.speedLabel}")
        list += action("full", Icon.createWithResource(act, R.drawable.ic_pip_fullscreen), "Fullscreen")
        return when {
            max >= list.size -> list
            max >= 1 -> listOf(list[2]) // phones with room for a single button: fullscreen only
            else -> emptyList()
        }
    }

    private fun action(name: String, icon: Icon, desc: String): RemoteAction {
        val i = Intent(ACTION).setPackage(act.packageName).putExtra(EXTRA, name)
        val pi = PendingIntent.getBroadcast(
            act, name.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return RemoteAction(icon, desc, desc, pi)
    }

    /** A white outlined pill with the value written inside ("1.5x", "720p"). */
    private fun labelIcon(text: String): Icon {
        val s = 108
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f
        }
        c.drawRoundRect(RectF(8f, 28f, s - 8f, s - 28f), 26f, 26f, ring)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; textSize = 36f
        }
        while (tp.measureText(text) > s - 28f && tp.textSize > 14f) tp.textSize -= 2f
        c.drawText(text, s / 2f, s / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
        return Icon.createWithBitmap(bmp)
    }

    private companion object {
        const val ACTION = "com.cleantube.app.PIP_ACTION"
        const val EXTRA = "a"
    }
}
