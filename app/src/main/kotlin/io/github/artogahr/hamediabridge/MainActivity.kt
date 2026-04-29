package io.github.artogahr.hamediabridge

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.artogahr.hamediabridge.MediaListenerService.Companion.PREF_HA_URL
import io.github.artogahr.hamediabridge.MediaListenerService.Companion.PREF_PACKAGE_FILTER
import io.github.artogahr.hamediabridge.MediaListenerService.Companion.PREF_WEBHOOK_ID
import io.github.artogahr.hamediabridge.MediaListenerService.Companion.PREFS_NAME

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs         = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val etHaUrl       = findViewById<EditText>(R.id.et_ha_url)
        val etWebhookId   = findViewById<EditText>(R.id.et_webhook_id)
        val etPkgFilter   = findViewById<EditText>(R.id.et_package_filter)
        val btnSave       = findViewById<Button>(R.id.btn_save)
        val btnPermission = findViewById<Button>(R.id.btn_grant_permission)
        val tvStatus      = findViewById<TextView>(R.id.tv_status)

        // Restore saved values
        etHaUrl.setText(prefs.getString(PREF_HA_URL, ""))
        etWebhookId.setText(prefs.getString(PREF_WEBHOOK_ID, ""))
        etPkgFilter.setText(prefs.getString(PREF_PACKAGE_FILTER, ""))

        btnSave.setOnClickListener {
            prefs.edit()
                .putString(PREF_HA_URL,         etHaUrl.text.toString().trimEnd('/'))
                .putString(PREF_WEBHOOK_ID,     etWebhookId.text.toString().trim())
                .putString(PREF_PACKAGE_FILTER, etPkgFilter.text.toString().trim())
                .apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            updateStatus(tvStatus)
        }

        btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        updateStatus(tvStatus)
    }

    override fun onResume() {
        super.onResume()
        updateStatus(findViewById(R.id.tv_status))
    }

    private fun updateStatus(tv: TextView) {
        tv.text = if (isListenerEnabled())
            getString(R.string.status_active)
        else
            getString(R.string.status_no_permission)
    }

    private fun isListenerEnabled(): Boolean {
        val cn   = ComponentName(this, MediaListenerService::class.java).flattenToString()
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.split(":").contains(cn)
    }
}
