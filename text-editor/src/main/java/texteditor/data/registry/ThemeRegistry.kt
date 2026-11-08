package texteditor.data.registry

import android.content.Context
import android.graphics.Color
import android.util.Log
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONObject
import texteditor.data.EditorAssets
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry as TextMateThemeRegistry

object ThemeRegistry {
    private const val TAG = "ThemeManager"
    private const val THEME_ID = "default"
    private const val THEME_PATH = "themes/default.json"

    data class ThemeInfo(
        val id: String,
        val displayName: String,
        val isDark: Boolean,
    )

    var availableThemes: List<ThemeInfo> = emptyList()
        private set

    @Volatile
    private var initialized = false

    val isInitialized: Boolean
        get() = initialized

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            EditorAssets.ensure(context)

            try {
                val jsonText =
                    context.assets
                        .open(THEME_PATH)
                        .bufferedReader()
                        .use { it.readText() }
                val json = JSONObject(jsonText)
                val dark = isDarkTheme(json)
                val model =
                    ThemeModel(
                        IThemeSource.fromInputStream(
                            context.assets.open(THEME_PATH),
                            THEME_PATH,
                            null,
                        ),
                        THEME_ID,
                    ).apply {
                        isDark = dark
                    }
                TextMateThemeRegistry.getInstance().loadTheme(model, true)
                availableThemes =
                    listOf(
                        ThemeInfo(
                            id = THEME_ID,
                            displayName = json.optString("name").ifBlank { THEME_ID },
                            isDark = dark,
                        ),
                    )
                Log.d(TAG, "Loaded $THEME_ID name=${json.optString("name")} dark=$dark")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load theme $THEME_ID", e)
            }
            initialized = true
        }
    }

    fun setTheme(id: String): Boolean = TextMateThemeRegistry.getInstance().setTheme(id)

    private fun isDarkTheme(theme: JSONObject): Boolean {
        when (theme.optString("type").lowercase()) {
            "light" -> return false
            "dark" -> return true
        }
        val background =
            theme.optJSONObject("colors")?.optString("editor.background")
                ?: theme.optJSONArray("settings")
                    ?.optJSONObject(0)
                    ?.optJSONObject("settings")
                    ?.optString("background")
        val color = background?.let(::parseColor) ?: return true
        val luminance =
            (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return luminance < 0.5
    }

    private fun parseColor(value: String): Int? {
        val hex = value.removePrefix("#")
        val rgb =
            when (hex.length) {
                6, 8 -> hex.takeLast(6)
                3 -> hex.map { "$it$it" }.joinToString("")
                else -> return null
            }
        return runCatching { rgb.toLong(16).toInt() or 0xFF000000.toInt() }.getOrNull()
    }
}
