package ai.opencode.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLHandshakeException

/** First-run / re-login / URL-edit screen. No browser UI here on purpose. */
class SetupActivity : AppCompatActivity() {

    companion object {
        /** When set, always show the form (used to fix a broken URL). */
        const val EXTRA_EDIT_URL = "edit_url"
    }

    private lateinit var prefs: ServerPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = ServerPrefs(this)

        val editUrl = intent.getStringExtra(EXTRA_EDIT_URL)

        // Share intent: pairing links re-point the server, other text is chat input.
        val shared = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val sharedPair = shared?.let { ServerPrefs.splitPairingLink(it) }
        if (sharedPair != null) {
            prefs.serverUrl = ServerPrefs.normalize(sharedPair.first)
            openMain(sharedPair.second)
            return
        }
        shared?.let { prefs.pendingShare = it }

        // Already configured -> straight into the app, no browser detour.
        // Edit mode and fresh shares always show the form.
        if (editUrl == null) {
            prefs.serverUrl?.let { openMain(); return }
        }

        setContentView(R.layout.activity_setup)

        val urlInput = findViewById<TextInputEditText>(R.id.input_server_url)
        val pairInput = findViewById<TextInputEditText>(R.id.input_pair_link)
        val connect = findViewById<MaterialButton>(R.id.btn_connect)
        val error = findViewById<TextView>(R.id.text_setup_error)
        urlInput.setText(editUrl ?: prefs.serverUrl.orEmpty())

        // Pre-fill when the user shares a pairing link to the app.
        if (intent?.action == Intent.ACTION_SEND) {
            val s = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (s.contains("/auth/connect/")) pairInput.setText(s.trim())
        }

        connect.setOnClickListener {
            error.visibility = View.GONE
            val pair = ServerPrefs.splitPairingLink(pairInput.text.toString())
            if (pair != null) {
                prefs.serverUrl = ServerPrefs.normalize(pair.first)
                openMain(pair.second)
                return@setOnClickListener
            }
            val urlRaw = urlInput.text.toString()
            if (urlRaw.isBlank()) {
                Toast.makeText(this, getString(R.string.setup_need_url), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = ServerPrefs.normalize(urlRaw)
            connect.isEnabled = false
            connect.text = getString(R.string.checking)
            Thread {
                val result = checkServer(url)
                runOnUiThread {
                    connect.isEnabled = true
                    connect.text = getString(R.string.setup_connect)
                    when (result) {
                        CheckResult.OK, CheckResult.SSL_ISSUE -> {
                            prefs.serverUrl = url
                            openMain()
                        }
                        CheckResult.UNREACHABLE -> {
                            error.text = getString(R.string.err_unreachable)
                            error.visibility = View.VISIBLE
                        }
                        CheckResult.NOT_OPENCODE -> {
                            error.text = getString(R.string.err_not_opencode)
                            error.visibility = View.VISIBLE
                        }
                    }
                }
            }.start()
        }
    }

    private fun openMain(firstUrl: String? = null) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_FIRST_URL, firstUrl ?: prefs.serverUrl)
            }
        )
        finish()
    }

    private enum class CheckResult { OK, SSL_ISSUE, UNREACHABLE, NOT_OPENCODE }

    /**
     * Lightweight fingerprint: /api/info answers 200 (open) or 401
     * (password-protected) on an opencode server.
     */
    private fun checkServer(base: String): CheckResult {
        return try {
            val conn = URL("$base/api/info").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Accept", "application/json")
                when (conn.responseCode) {
                    200, 401 -> CheckResult.OK
                    else -> CheckResult.NOT_OPENCODE
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: SSLHandshakeException) {
            // Self-hosted certs: the WebView lets the user through anyway.
            CheckResult.SSL_ISSUE
        } catch (e: Exception) {
            CheckResult.UNREACHABLE
        }
    }
}
