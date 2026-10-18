package com.cleantube.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cleantube.app.data.Yt
import com.cleantube.app.data.VideoItem

/** Lets the activity's Back button step back inside the Shorts page first. */
object ShortsWeb {
    var view: WebView? = null

    fun goBack(): Boolean {
        val v = view ?: return false
        return if (v.canGoBack()) { v.goBack(); true } else false
    }
}

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
private val shortsId = Regex("/shorts/([A-Za-z0-9_-]{11})")

/**
 * The Shorts tab is YouTube's own mobile Shorts player (same frame, same swipe, same buttons as m.youtube.com/shorts),
 * so playback is exactly what YouTube plays. A round Download button sits on top for the Short that is on screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ShortsScreen() {
    val ctx = LocalContext.current
    var currentId by remember { mutableStateOf<String?>(null) }
    var downloadDialog by remember { mutableStateOf(false) }

    // Pause the page when the app goes to the background.
    DisposableEffect(Unit) {
        val owner = ctx as? LifecycleOwner
        val obs = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) { ShortsWeb.view?.onPause() }
            override fun onResume(owner: LifecycleOwner) { ShortsWeb.view?.onResume() }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            // leaves room for the floating tab bar so it never covers YouTube's own buttons
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 72.dp),
            factory = { c ->
                WebView(c).apply {
                    val web = this
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        userAgentString = MOBILE_UA
                        cacheMode = WebSettings.LOAD_DEFAULT
                        loadWithOverviewMode = true
                    }
                    Yt.hideRequestedWith(this)
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(web, true)
                        setCookie("https://m.youtube.com", "SOCS=CAI; Domain=.youtube.com; Path=/; Secure")
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val u = request.url
                            val host = u.host.orEmpty()
                            // stay on YouTube; never jump to the YouTube app (intent:// / youtube://) or other sites
                            if (u.scheme != "https" && u.scheme != "http") return true
                            if (host.endsWith("youtube.com") || host.endsWith("google.com") || host.endsWith("youtu.be")) return false
                            openUrl(view.context, u.toString())
                            return true
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            currentId = url?.let { shortsId.find(it)?.groupValues?.get(1) }
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            currentId = url?.let { shortsId.find(it)?.groupValues?.get(1) }
                        }
                    }
                    ShortsWeb.view = this
                    loadUrl("https://m.youtube.com/shorts")
                }
            },
            onRelease = { w ->
                if (ShortsWeb.view === w) ShortsWeb.view = null
                w.stopLoading()
                w.loadUrl("about:blank")
                w.onPause()
                w.destroy()
            }
        )

        if (currentId != null) {
            IconButton(
                onClick = { downloadDialog = true },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
            ) {
                Icon(Icons.Rounded.Download, contentDescription = "Download this Short", tint = Color.White)
            }
        }
    }

    val id = currentId
    if (downloadDialog && id != null) {
        DownloadDialog(VideoItem(id = id, title = "")) { downloadDialog = false }
    }
}
