package com.cleantube.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.cleantube.app.data.Session
import com.cleantube.app.data.Yt
import kotlinx.coroutines.launch

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&hl=en" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3D%252F"

// A plain Chrome user agent (no "; wv"), because Google refuses sign-in inside obvious embedded WebViews.
private const val CHROME_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle("Sign in", onBack = { nav.back() })
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = CHROME_MOBILE_UA
                    cm.setAcceptThirdPartyCookies(this, true)
                    Yt.hideRequestedWith(this)
                    var done = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            check(view, url)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            check(view, url)
                        }

                        private fun check(view: WebView, url: String?) {
                            if (done || url == null) return
                            val host = android.net.Uri.parse(url).host.orEmpty()
                            if (!host.endsWith("youtube.com")) return
                            val c = cm.getCookie("https://www.youtube.com").orEmpty()
                            if (c.contains("SAPISID=") || c.contains("__Secure-3PAPISID=")) {
                                done = true
                                cm.flush()
                                Session.markLoggedIn()
                                scope.launch {
                                    try { Yt.run("reset") } catch (e: Exception) { /* ignore */ }
                                    Session.refresh(force = true)
                                }
                                nav.back()
                            }
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            },
            onRelease = { it.destroy() }
        )
    }
}
