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
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
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
    }

    private lateinit var webView: WebView
    private lateinit var errorView: View
    private lateinit var prefs: ServerPrefs
    private var fileChooser: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = ServerPrefs(this)
        setContentView(R.layout.activity_main)

        errorView = findViewById(R.id.error_view)
        findViewById<MaterialButton>(R.id.btn_retry).setOnClickListener {
            errorView.visibility = View.GONE
            webView.reload()
        }
        findViewById<MaterialButton>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        webView = findViewById(R.id.webview)
        with(webView.settings) {
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
        webView.setOnLongClickListener { true }
        webView.isLongClickable = false
        webView.isHapticFeedbackEnabled = false

        CookieManager.getInstance().setAcceptCookie(true)
        webView.addJavascriptInterface(AppBridge(this), "OpenCodeApp")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                errorView.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView, url: String) {
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
                    errorView.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.error_detail).text =
                        "詳細: ${error.errorCode} ${error.description}"
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                // Self-hosted server on a private tailnet: let the user proceed.
                // Browser equivalent of tapping through a cert warning.
                handler.proceed()
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
                        Toast.makeText(this@MainActivity, "開けませんでした", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
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
        if (prefs.refreshRequested && this::webView.isInitialized) {
            prefs.refreshRequested = false
            webView.reload()
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_RELOAD) {
            if (this::webView.isInitialized) webView.reload()
            return
        }
        val first = intent?.getStringExtra(EXTRA_FIRST_URL) ?: requireServerUrl()
        if (this::webView.isInitialized && webView.url == null) {
            webView.loadUrl(first)
        }
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
        if (this::webView.isInitialized) {
            errorView.visibility = View.GONE
            webView.reload()
        }
    }

    fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    fun currentServerUrl(): String = prefs.serverUrl ?: ""

    /** Built-in drawer buttons (更新 / アプリ設定), styled like stock ones. */
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
                Toast.makeText(this, "共有テキストをクリップボードにコピーしました", Toast.LENGTH_LONG).show()
            }
        }
    }
}
