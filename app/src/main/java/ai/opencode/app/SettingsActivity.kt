package ai.opencode.app

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/** Native settings: server, session, and userscript editor. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val prefs = ServerPrefs(this)

        findViewById<TextView>(R.id.text_server_url).text =
            prefs.serverUrl ?: getString(R.string.not_set)

        findViewById<MaterialButton>(R.id.btn_change_server).setOnClickListener {
            prefs.serverUrl = null
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.btn_logout).setOnClickListener {
            CookieManager.getInstance().removeAllCookies {
                Toast.makeText(this, getString(R.string.logged_out), Toast.LENGTH_SHORT).show()
            }
            prefs.refreshRequested = true
            finish()
        }

        val enable = findViewById<CheckBox>(R.id.check_enable)
        val appmenu = findViewById<CheckBox>(R.id.check_appmenu)
        val css = findViewById<TextInputEditText>(R.id.input_css)
        val js = findViewById<TextInputEditText>(R.id.input_js)
        enable.isChecked = prefs.userScriptEnabled
        appmenu.isChecked = prefs.appMenuEnabled
        css.setText(prefs.userCss.orEmpty())
        js.setText(prefs.userJs.orEmpty())

        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            prefs.userScriptEnabled = enable.isChecked
            prefs.appMenuEnabled = appmenu.isChecked
            prefs.userCss = css.text.toString()
            prefs.userJs = js.text.toString()
            prefs.refreshRequested = true
            finish()
        }
    }
}
