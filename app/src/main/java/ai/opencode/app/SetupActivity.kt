package ai.opencode.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/** First-run / re-login screen. No browser UI here on purpose. */
class SetupActivity : AppCompatActivity() {

    private lateinit var prefs: ServerPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = ServerPrefs(this)

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
        prefs.serverUrl?.let { openMain() ; return }

        setContentView(R.layout.activity_setup)

        val urlInput = findViewById<TextInputEditText>(R.id.input_server_url)
        val pairInput = findViewById<TextInputEditText>(R.id.input_pair_link)
        val connect = findViewById<MaterialButton>(R.id.btn_connect)
        prefs.serverUrl?.let { urlInput.setText(it) }

        // Pre-fill when the user shares a pairing link to the app.
        if (intent?.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (shared.contains("/auth/connect/")) pairInput.setText(shared.trim())
        }

        connect.setOnClickListener {
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
            prefs.serverUrl = ServerPrefs.normalize(urlRaw)
            openMain()
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
}
