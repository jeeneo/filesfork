package texteditor.server.data

// SPDX-License-Identifier: GPL-3.0-or-later

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class ServerSettings(
    val serverPort: Int = 8066,
    val baseUrlOverride: String = "",
    val serverIp: String = "0.0.0.0",
    val rootPath: String? = null,
)

class ServerSettingsRepository(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("texteditor_server", Context.MODE_PRIVATE)

    val settings: Flow<ServerSettings> =
        callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    trySend(read())
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(read())
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    private fun read(): ServerSettings =
        ServerSettings(
            serverPort = prefs.getInt("port", 8066),
            baseUrlOverride = prefs.getString("baseUrlOverride", "").orEmpty(),
            serverIp = prefs.getString("serverIp", "0.0.0.0").orEmpty(),
            rootPath = prefs.getString("rootPath", null),
        )

    fun setPort(port: Int) {
        prefs.edit { putInt("port", port) }
    }

    fun setBaseUrlOverride(url: String) {
        prefs.edit { putString("baseUrlOverride", url.trim()) }
    }

    fun rootPath(): String? = prefs.getString("rootPath", null)

    fun setRootPath(path: String?) {
        prefs.edit {
            if (path.isNullOrBlank()) {
                remove("rootPath")
            } else {
                putString("rootPath", path)
            }
        }
    }
}
