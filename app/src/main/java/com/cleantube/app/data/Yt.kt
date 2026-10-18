package com.cleantube.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

/**
 * Transport for YouTube's internal ("InnerTube") API.
 *
 * A hidden WebView sits on https://www.youtube.com, so requests carry the real browser's cookies,
 * headers and TLS. assets/yt.js does the actual calls and parsing; this class only sends it commands.
 */
object Yt {
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var ready = CompletableDeferred<Unit>()
    private var started = false
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val ids = AtomicInteger(1)

    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
    }

    class Bridge {
        @JavascriptInterface
        fun onResult(id: Int, ok: Boolean, json: String) {
            val d = pending.remove(id) ?: return
            if (ok) d.complete(json) else d.completeExceptionally(IllegalStateException(json))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startOnMain() {
        if (started) return
        started = true
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        // Skips the EU cookie-consent wall.
        cm.setCookie("https://www.youtube.com", "SOCS=CAI; Domain=.youtube.com; Path=/; Secure")
        val js = app.assets.open("yt.js").bufferedReader().use { it.readText() }

        val w = WebView(app)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = DESKTOP_UA
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        hideRequestedWith(w)
        w.addJavascriptInterface(Bridge(), "AndroidBridge")
        var injected = false
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (injected || url == null || !url.startsWith("https://www.youtube.com")) return
                injected = true
                view.evaluateJavascript(js) { ready.complete(Unit) }
            }
        }
        web = w
        // robots.txt is a tiny same-origin page: cookies and fetch() work, nothing heavy loads.
        w.loadUrl("https://www.youtube.com/robots.txt")
    }

    private var localeSent = false

    private suspend fun ensure() {
        main.post { startOnMain() }
        try {
            withTimeout(20_000) { ready.await() }
        } catch (e: TimeoutCancellationException) {
            throw ApiException(ApiException.Kind.NETWORK, "Couldn't reach YouTube. Check your internet and try again.")
        }
        if (!localeSent) {
            localeSent = true
            setLocale("en", Graph.store.region)
        }
    }

    /** Runs one command from yt.js and returns its JSON result. */
    suspend fun run(cmd: String, args: JSONObject = JSONObject()): JSONObject {
        ensure()
        val id = ids.getAndIncrement()
        val d = CompletableDeferred<String>()
        pending[id] = d
        val script = "CT.run($id,${JSONObject.quote(cmd)},${JSONObject.quote(args.toString())})"
        main.post { web?.evaluateJavascript(script, null) }
        val raw = try {
            withTimeout(30_000) { d.await() }
        } catch (e: TimeoutCancellationException) {
            pending.remove(id)
            throw ApiException(ApiException.Kind.NETWORK, "YouTube took too long to answer.")
        } catch (e: ApiException) {
            throw e
        } catch (e: IllegalStateException) {
            val m = e.message.orEmpty()
            val kind = if (m.contains("HTTP 404")) ApiException.Kind.NOT_FOUND else ApiException.Kind.OTHER
            throw ApiException(kind, "YouTube error: $m")
        }
        return try {
            JSONObject(raw)
        } catch (e: org.json.JSONException) {
            throw ApiException(ApiException.Kind.OTHER, "YouTube sent something unexpected.")
        }
    }

    /** Stops WebView from adding "X-Requested-With: <package>", which Google uses to spot embedded browsers. */
    fun hideRequestedWith(w: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(w.settings, emptySet())
        }
    }

    fun setLocale(hl: String, gl: String) {
        main.post { web?.evaluateJavascript("CT.run(0,'locale',${JSONObject.quote("{\"hl\":\"$hl\",\"gl\":\"$gl\"}")})", null) }
    }
}

/** Login state shared by all screens. */
object Session {
    var loggedIn by mutableStateOf(false)
        private set
    var name by mutableStateOf("")
        private set
    var thumb by mutableStateOf("")
        private set
    var handle by mutableStateOf("")
        private set

    private fun hasAuthCookie(): Boolean {
        val c = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
        return c.contains("SAPISID=") || c.contains("__Secure-3PAPISID=")
    }

    /** Re-reads the cookie jar; call after login/logout and on start. */
    suspend fun refresh() {
        loggedIn = hasAuthCookie()
        if (!loggedIn) {
            name = ""; thumb = ""; handle = ""
            return
        }
        try {
            val a = Yt.run("account")
            name = a.optString("name")
            thumb = a.optString("thumb")
            handle = a.optString("handle")
        } catch (e: Exception) {
            // The account name is cosmetic.
        }
    }

    fun markLoggedIn() {
        loggedIn = hasAuthCookie()
    }

    fun logout() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
        loggedIn = false
        name = ""; thumb = ""; handle = ""
    }
}
