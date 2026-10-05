package ai.opencode.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Single server profile + pending share text. Encrypted store comes later. */
class ServerPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("opencode", Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit { putString(KEY_URL, value) }

    var pendingShare: String?
        get() = prefs.getString(KEY_SHARE, null)
        set(value) = prefs.edit { putString(KEY_SHARE, value) }

    var userScriptEnabled: Boolean
        get() = prefs.getBoolean(KEY_US_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_US_ENABLED, value) }

    var userCss: String?
        get() = prefs.getString(KEY_US_CSS, null)
        set(value) = prefs.edit { putString(KEY_US_CSS, value) }

    var userJs: String?
        get() = prefs.getString(KEY_US_JS, null)
        set(value) = prefs.edit { putString(KEY_US_JS, value) }

    var refreshRequested: Boolean
        get() = prefs.getBoolean(KEY_REFRESH, false)
        set(value) = prefs.edit { putBoolean(KEY_REFRESH, value) }

    fun clearSession() {
        prefs.edit {
            remove(KEY_SHARE)
        }
    }

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_SHARE = "pending_share"
        private const val KEY_US_ENABLED = "userscript_enabled"
        private const val KEY_US_CSS = "userscript_css"
        private const val KEY_US_JS = "userscript_js"
        private const val KEY_REFRESH = "refresh_requested"

        /** Normalize user input: trim, add http:// when missing, drop trailing /. */
        fun normalize(raw: String): String {
            var v = raw.trim()
            if (!v.startsWith("http://") && !v.startsWith("https://")) v = "http://$v"
            return v.trimEnd('/')
        }

        /**
         * Pairing links look like http://host:port/auth/connect/<code>.
         * Returns (baseUrl, fullPairingUrl) or null when not a pairing link.
         */
        fun splitPairingLink(raw: String): Pair<String, String>? {
            val v = raw.trim()
            val idx = v.indexOf("/auth/connect/")
            if (idx < 0) return null
            val base = v.substring(0, idx).trimEnd('/')
            if (base.isEmpty()) return null
            return base to v
        }
    }
}
