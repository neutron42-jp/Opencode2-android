package ai.opencode.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.net.InetSocketAddress
import java.net.Socket
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Fullscreen WebView host. No toolbar, no URL bar: this must feel like
 * an app, not a browser. Settings live in [SettingsActivity], reachable
 * from the launcher icon shortcut or the error screen.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FIRST_URL = "first_url"
        const val ACTION_RELOAD = "ai.opencode.app.RELOAD"
        private const val REQ_FILE = 1001
        private const val WATCHDOG_MS = 12_000L
        private const val PROBE_TIMEOUT_MS = 3_000
        private const val SILENCE_MS = 3_000L
    }

    private lateinit var webContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var errorView: View
    private lateinit var loadingView: View
    private lateinit var prefs: ServerPrefs
    private var fileChooser: ValueCallback<Array<Uri>>? = null

    // WebView has no load timeout of its own: without this, a dead route
    // (e.g. VPN off) sits on a black screen until TCP gives up.
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var watchdog: Runnable? = null
    private var pageDone = false
    private var expectRender = true
    private var awaitStart = false
    private var healedCurrent = false

    private fun armWatchdog() {
        cancelWatchdog()
        pageDone = false
        if (!expectRender) return
        EventLog.log("watchdog", "armed ${WATCHDOG_MS}ms")
        watchdog = Runnable {
            if (!pageDone && this::webView.isInitialized) {
                EventLog.log("watchdog", "fired, stopping load")
                webView.stopLoading()
                showLoadError(getString(R.string.err_timeout))
            }
        }
        watchdogHandler.postDelayed(watchdog!!, WATCHDOG_MS)
    }

    private fun cancelWatchdog() {
        watchdog?.let { watchdogHandler.removeCallbacks(it) }
        watchdog = null
    }

    private fun showLoadError(detail: String) {
        loadingView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        findViewById<TextView>(R.id.error_detail).text = detail
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView {
        val wv = WebView(this)
        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }
        // Kill browser feel: no long-press popup, no text-selection handles menu.
        wv.setOnLongClickListener { true }
        wv.isLongClickable = false
        wv.isHapticFeedbackEnabled = false

        CookieManager.getInstance().setAcceptCookie(true)
        wv.addJavascriptInterface(AppBridge(this), "OpenCodeApp")

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                awaitStart = false
                EventLog.log("web", "started $url")
                errorView.visibility = View.GONE
                loadingView.visibility = View.VISIBLE
                armWatchdog()
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                EventLog.log("web", "commit $url")
            }

            override fun onPageFinished(view: WebView, url: String) {
                injectRenderHook()
                injectBuiltIn()
                injectUserStyle()
                injectPendingShare()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    EventLog.log(
                        "web",
                        "error ${error.errorCode} ${error.description} url=${request.url}"
                    )
                    pageDone = true
                    cancelWatchdog()
                    loadingView.visibility = View.GONE
                    errorView.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.error_detail).text =
                        getString(R.string.error_detail, error.errorCode, error.description)
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                // Self-hosted server on a private tailnet: let the user proceed.
                // Browser equivalent of tapping through a cert warning.
                handler.proceed()
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                EventLog.log("web", "renderer gone, crashed=${detail.didCrash()}")
                pageDone = true
                cancelWatchdog()
                showLoadError("Renderer gone (crashed=${detail.didCrash()})")
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                // Stay inside the app for the configured server, open the
                // system handler for everything else (no in-app browser tabs).
                return if (isSameHost(target)) {
                    false
                } else {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                    }.onFailure {
                        Toast.makeText(this@MainActivity, getString(R.string.open_failed), Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooser?.onReceiveValue(null)
                fileChooser = filePathCallback
                runCatching {
                    val intent = fileChooserParams.createIntent().apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQ_FILE)
                }.onFailure {
                    fileChooser?.onReceiveValue(null)
                    fileChooser = null
                }
                return true
            }
        }
        return wv
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = ServerPrefs(this)
        EventLog.init(filesDir, BuildConfig.VERSION_NAME)
        setContentView(R.layout.activity_main)

        errorView = findViewById(R.id.error_view)
        loadingView = findViewById(R.id.loading_view)
        findViewById<MaterialButton>(R.id.btn_retry).setOnClickListener {
            reloadNow()
        }
        findViewById<MaterialButton>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btn_edit_url).setOnClickListener {
            startActivity(
                Intent(this, SetupActivity::class.java).apply {
                    putExtra(SetupActivity.EXTRA_EDIT_URL, prefs.serverUrl)
                }
            )
        }

        webContainer = findViewById(R.id.webview_container)
        webView = newWebView()
        webContainer.addView(
            webView, 0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (this@MainActivity::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (prefs.refreshRequested) {
            prefs.refreshRequested = false
            reloadNow()
        } else {
            probeServer()
        }
    }

    /**
     * A loaded page shows no errors when the route dies later
     * (e.g. VPN off while backgrounded), so probe on return.
     */
    private fun probeServer() {
        val base = prefs.serverUrl ?: return
        if (!this::webView.isInitialized || webView.url == null) return
        if (errorView.visibility == View.VISIBLE) return
        if (loadingView.visibility == View.VISIBLE) return
        Thread {
            EventLog.log("probe", "start $base")
            val ok = runCatching {
                val u = Uri.parse(base)
                val addr = try {
                    java.net.InetAddress.getByName(u.host).hostAddress
                } catch (e: Exception) {
                    "dns-fail:${e.message?.take(60)}"
                }
                EventLog.log("probe", "dns ${u.host} => $addr")
                Socket().use { s ->
                    s.connect(
                        InetSocketAddress(u.host, if (u.port != -1) u.port else 80),
                        PROBE_TIMEOUT_MS
                    )
                }
                true
            }.getOrDefault(false)
            runOnUiThread {
                EventLog.log("probe", if (ok) "ok" else "failed")
                if (!ok && errorView.visibility != View.VISIBLE &&
                    this::webView.isInitialized
                ) {
                    webView.stopLoading()
                    showLoadError(getString(R.string.err_timeout))
                }
            }
        }.start()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_RELOAD) {
            reloadNow()
            return
        }
        val first = intent?.getStringExtra(EXTRA_FIRST_URL) ?: requireServerUrl()
        if (!this::webView.isInitialized) return
        if (webView.url == null || webView.url != first ||
            intent?.hasExtra(EXTRA_FIRST_URL) == true
        ) {
            lastUrl = first
            reloadNow()
        }
    }

    private var lastUrl: String? = null

    /**
     * Every refresh path goes through here. reload() is a no-op when the
     * previous load never committed (e.g. first launch failed), leaving a
     * dead black page, so always navigate explicitly.
     */
    fun reloadNow() {
        if (!this::webView.isInitialized) return
        val u = lastUrl ?: prefs.serverUrl ?: return
        lastUrl = u
        // about:blank carries no app UI, so nothing to wait for.
        expectRender = u != "about:blank"
        EventLog.log("nav", "load $u")
        errorView.visibility = View.GONE
        awaitStart = true
        healedCurrent = false
        webView.loadUrl(u)
        watchdogHandler.postDelayed({
            if (awaitStart && this::webView.isInitialized) {
                onSilentLoad()
            }
        }, SILENCE_MS)
    }

    /**
     * loadUrl produced zero callbacks: the instance may be wedged.
     * Recreate it once, then give up with a proper error screen.
     */
    private fun onSilentLoad() {
        if (!this::webView.isInitialized) return
        EventLog.log("web", "silent, url=${webView.url}")
        webView.evaluateJavascript("navigator.userAgent") { ua ->
            EventLog.log("web", "ping => ${ua?.take(80)}")
        }
        if (healedCurrent) {
            EventLog.log("web", "still silent after recreate, giving up")
            awaitStart = false
            webView.stopLoading()
            showLoadError(getString(R.string.err_timeout))
            return
        }
        healedCurrent = true
        EventLog.log("web", "recreating WebView")
        runCatching {
            webContainer.removeView(webView)
            webView.destroy()
        }
        webView = newWebView()
        webContainer.addView(
            webView, 0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        lastUrl?.let { webView.loadUrl(it) }
        watchdogHandler.postDelayed({
            if (awaitStart && this::webView.isInitialized) {
                EventLog.log("web", "still silent after recreate, giving up")
                awaitStart = false
                webView.stopLoading()
                showLoadError(getString(R.string.err_timeout))
            }
        }, SILENCE_MS)
    }

    @Deprecated("file chooser needs the legacy callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            val uris = when {
                resultCode != RESULT_OK -> null
                data?.clipData != null -> {
                    val cd = data.clipData!!
                    Array(cd.itemCount) { cd.getItemAt(it).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> emptyArray()
            }
            fileChooser?.onReceiveValue(uris)
            fileChooser = null
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        cancelWatchdog()
        fileChooser?.onReceiveValue(null)
        fileChooser = null
        if (this::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    private fun requireServerUrl(): String =
        prefs.serverUrl ?: run {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            "about:blank"
        }

    private fun isSameHost(target: String): Boolean {
        return runCatching {
            val base = Uri.parse(requireServerUrl())
            val other = Uri.parse(target)
            base.host == other.host && base.port == other.port
        }.getOrDefault(false)
    }

    fun reloadWebView() {
        reloadNow()
    }

    fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    fun currentServerUrl(): String = prefs.serverUrl ?: ""

    /** Called from the page once the WebUI has rendered its first frame. */
    fun onFirstRender() {
        if (!this::webView.isInitialized) return
        EventLog.log("web", "first render")
        pageDone = true
        cancelWatchdog()
        loadingView.visibility = View.GONE
    }

    /** Watches #root until the SPA renders, then reports back. */
    private fun injectRenderHook() {
        webView.evaluateJavascript(
            "(function(){" +
                "if(window.__ocRenderHook)return;window.__ocRenderHook=true;" +
                "function ok(){var r=document.getElementById('root');" +
                "if(r&&r.children.length>0){if(window.OpenCodeApp)OpenCodeApp.onFirstRender();return true;}" +
                "return false;}" +
                "if(ok())return;" +
                "var o=new MutationObserver(function(){if(ok())o.disconnect();});" +
                "o.observe(document.documentElement,{childList:true,subtree:true});" +
                "})()",
            null
        )
    }

    /** Built-in drawer buttons (Reload / App settings), styled like stock ones. */
    private fun injectBuiltIn() {
        if (!prefs.appMenuEnabled) return
        webView.evaluateJavascript(BuiltInScript.JS, null)
    }

    /** Custom CSS/JS from the in-app editor, applied after every page load. */
    private fun injectUserStyle() {
        if (!prefs.userScriptEnabled) return
        prefs.userCss?.takeIf { it.isNotBlank() }?.let { css ->
            val esc = css
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("$", "\\$")
            webView.evaluateJavascript(
                "(function(){var s=document.getElementById('opencode-app-style');" +
                    "if(!s){s=document.createElement('style');s.id='opencode-app-style';" +
                    "document.head.appendChild(s);}s.textContent=`$esc`;})()",
                null
            )
        }
        prefs.userJs?.takeIf { it.isNotBlank() }?.let { js ->
            webView.evaluateJavascript(js, null)
        }
    }

    /** Best-effort: shared text goes to the focused field, else clipboard. */
    private fun injectPendingShare() {
        val text = prefs.pendingShare ?: return
        prefs.pendingShare = null
        val escaped = text
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")
            .take(4000)
        webView.evaluateJavascript(
            """(function(){var t=`$escaped`;var el=document.activeElement;
              if(el&&(el.tagName==='TEXTAREA'||(el.tagName==='INPUT'&&el.type==='text'))){
                el.value+=(el.value?'\\n':'')+t;el.dispatchEvent(new Event('input',{bubbles:true}));return 'pasted';
              }return 'clipboard';})()"""
        ) { result ->
            if (result?.contains("clipboard") == true) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("opencode share", text))
                Toast.makeText(this, getString(R.string.share_clipboard), Toast.LENGTH_LONG).show()
            }
        }
    }
}
