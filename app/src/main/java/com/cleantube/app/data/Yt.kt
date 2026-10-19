package com.cleantube.app.data

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
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

    /**
     * Accepts a plain cookie string ("SID=...; SAPISID=...") or the token text of the NewPipe fork
     * (lines like `***COOKIE*** =...`). Returns the cookie list, or null if no usable cookie is inside.
     */
    fun parseToken(text: String): List<Pair<String, String>>? {
        var cookie = ""
        var templated = false
        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("***COOKIE***") || line.startsWith("***INNERTUBE COOKIE***")) {
                cookie = line.substringAfter("=").trim()
                templated = true
            }
        }
        if (!templated) cookie = text.replace("\r", " ").replace("\n", " ").trim().removePrefix("Cookie:").removePrefix("cookie:").trim()
        cookie = cookie.replace("\r", "").replace("\n", "").trim()
        val pairs = cookie.split(";").map { it.trim() }.mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }
        return if (pairs.any { it.first == "SAPISID" || it.first == "__Secure-3PAPISID" }) pairs else null
    }

    /** Stores the pasted cookies and checks them with YouTube. Returns an error message, or null on success. */
    suspend fun loginWithToken(text: String): String? {
        val pairs = parseToken(text) ?: return "That doesn't contain a YouTube login cookie (SAPISID is missing)."
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        pairs.forEach { (k, v) ->
            for (domain in listOf("youtube.com", "google.com")) {
                cm.setCookie("https://www.$domain", "$k=$v; Domain=.$domain; Path=/; Secure")
            }
        }
        cm.flush()
        markLoggedIn()
        try {
            Yt.run("reset")
        } catch (e: Exception) {
            // checked below
        }
        refresh()
        if (name.isEmpty() && handle.isEmpty()) {
            logout()
            return "YouTube didn't accept that token. It may have expired: copy a fresh one."
        }
        return null
    }

    /** Copies the current session as token text (full access to the account, so it is flagged sensitive). */
    fun copyToken(context: Context): Boolean {
        val cookie = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
        if (cookie.isEmpty()) return false
        val text = "***COOKIE*** =$cookie\n***NAME*** =$name"
        val clip = ClipData.newPlainText("YouTube token", text)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        return true
    }

    fun logout() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
        loggedIn = false
        name = ""; thumb = ""; handle = ""
    }
}
