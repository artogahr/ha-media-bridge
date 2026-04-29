package io.github.artogahr.hamediabridge

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MediaListenerService : NotificationListenerService() {

    private val tag = "HAMediaBridge"
    private val callbacks = mutableMapOf<String, MediaController.Callback>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sessionManager: MediaSessionManager

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            syncControllers(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        sessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val cn = ComponentName(this, MediaListenerService::class.java)
        sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, cn)
        syncControllers(sessionManager.getActiveSessions(cn))
        Log.i(tag, "Listener connected")
    }

    override fun onListenerDisconnected() {
        callbacks.clear()
        Log.i(tag, "Listener disconnected")
    }

    private fun syncControllers(controllers: List<MediaController>) {
        val activePkgs = controllers.map { it.packageName }.toSet()

        // Drop callbacks for sessions that are gone
        callbacks.keys.toList().filterNot { it in activePkgs }.forEach { callbacks.remove(it) }

        // Register callbacks for new sessions
        controllers.filterNot { it.packageName in callbacks }.forEach { ctrl ->
            val pkg = ctrl.packageName
            if (!isPackageAllowed(pkg)) return@forEach

            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() } ?: return
                    report(
                        pkg     = pkg,
                        title   = title,
                        artist  = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                        mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                    )
                }
            }

            ctrl.registerCallback(cb, handler)
            callbacks[pkg] = cb
            Log.d(tag, "Tracking session: $pkg")
        }
    }

    private fun isPackageAllowed(pkg: String): Boolean {
        val filter = prefs().getString(PREF_PACKAGE_FILTER, "")?.trim() ?: return true
        if (filter.isEmpty()) return true
        return filter.split(",").map { it.trim() }.contains(pkg)
    }

    private fun report(pkg: String, title: String, artist: String?, mediaId: String?) {
        val haUrl     = prefs().getString(PREF_HA_URL, "")?.trimEnd('/') ?: return
        val webhookId = prefs().getString(PREF_WEBHOOK_ID, "") ?: return

        if (haUrl.isEmpty() || webhookId.isEmpty()) {
            Log.w(tag, "HA URL or webhook ID not set — skipping")
            return
        }

        val payload = JSONObject().apply {
            put("title",       title)
            put("app_package", pkg)
            if (!artist.isNullOrBlank())  put("artist",   artist)
            if (!mediaId.isNullOrBlank()) put("media_id", mediaId)
        }.toString()

        Log.d(tag, "Reporting: $payload")

        Thread {
            try {
                val conn = URL("$haUrl/api/webhook/$webhookId")
                    .openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 5_000
                    readTimeout    = 5_000
                    doOutput       = true
                    outputStream.use { it.write(payload.toByteArray()) }
                }
                Log.i(tag, "Webhook response: ${conn.responseCode} for '$title'")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(tag, "Webhook failed: ${e.message}")
            }
        }.start()
    }

    private fun prefs() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    companion object {
        const val PREFS_NAME           = "ha_media_bridge"
        const val PREF_HA_URL          = "ha_url"
        const val PREF_WEBHOOK_ID      = "webhook_id"
        const val PREF_PACKAGE_FILTER  = "package_filter"
    }
}
