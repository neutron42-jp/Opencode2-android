package ai.opencode.app

import android.content.Intent
import android.webkit.JavascriptInterface

/** Called from the WebUI (built-in userscript buttons). */
class AppBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun reload() {
        activity.runOnUiThread { activity.reloadWebView() }
    }

    @JavascriptInterface
    fun openSettings() {
        activity.runOnUiThread { activity.openSettings() }
    }

    @JavascriptInterface
    fun getServerUrl(): String = activity.currentServerUrl()

    @JavascriptInterface
    fun onFirstRender() {
        activity.runOnUiThread { activity.onFirstRender() }
    }
}
