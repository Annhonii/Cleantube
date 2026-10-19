package com.cleantube.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cleantube.app.ui.CleanTubeRoot
import com.cleantube.app.ui.CleanTubeTheme
import com.cleantube.app.ui.FullscreenHost
import com.cleantube.app.ui.Nav
import com.cleantube.app.ui.PipHost
import com.cleantube.app.ui.Route
import com.cleantube.app.ui.routeFromUri

class MainActivity : ComponentActivity() {

    private val nav = Nav()
    private val fullscreen = FullscreenHost()

    // Android 13+ hides all notifications (including download progress) until the user allows them.
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_DOWNLOADS = "downloads"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // transparent bars; the icons turn dark / light with the system light / dark setting
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        fullscreen.onChanged = { on -> applyFullscreen(on) }

        // "Popup" button: shrink the video into a floating picture-in-picture window.
        PipHost.enter = { enterPopup() }
        addOnPictureInPictureModeChangedListener { info -> PipHost.active = info.isInPictureInPictureMode }
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            CleanTubeTheme {
                CleanTubeRoot(nav, fullscreen)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Opens shared / tapped YouTube links inside the app. */
    private fun handleIntent(i: Intent?) {
        if (i == null) return
        // Tapped a download notification: show the Downloads tab.
        if (i.getStringExtra(EXTRA_OPEN) == OPEN_DOWNLOADS) {
            i.removeExtra(EXTRA_OPEN)
            nav.selectTab(Route.Downloads)
            return
        }
        val uri: Uri? = when (i.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> {
                val shared = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                Regex("https?://\\S+").find(shared)?.value?.let { Uri.parse(it) }
            }
            else -> null
        }
        val route = uri?.let { routeFromUri(it) } ?: return
        nav.push(route)
    }

    override fun onResume() {
        super.onResume()
        // safety net: never stay stuck in the "player only" layout after the popup window is gone
        PipHost.active = Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode
    }

    override fun onDestroy() {
        PipHost.enter = null
        PipHost.active = false
        super.onDestroy()
    }

    private fun enterPopup() {
        if (Build.VERSION.SDK_INT < 26 || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "Popup isn't supported on this phone.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open the popup window.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyFullscreen(on: Boolean) {
        requestedOrientation =
            if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
