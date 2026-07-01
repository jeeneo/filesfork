package me.zhanghai.android.filesfork.viewer.text

import android.content.Context
import android.util.Log
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONObject

object ThemeRegistry {
    private const val TAG = "ThemeManager"
    private const val THEMES_DIR = "themes"
    private const val DEFAULT_THEME = "darcula"

    data class ThemeInfo(val id: String, val displayName: String, val isDark: Boolean)

    var availableThemes: List<ThemeInfo> = emptyList()
        private set

    fun initialize(context: Context) {
        val ids = try {
            context.assets.list(THEMES_DIR)?.filter { it.endsWith(".json") }
                ?.map { it.removeSuffix(".json") } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list themes", e)
            emptyList()
        }

        val themeRegistry = ThemeRegistry.getInstance()
        val loaded = mutableListOf<ThemeInfo>()

        for (id in ids) {
            val path = "$THEMES_DIR/$id.json"
            try {
                val jsonText = context.assets.open(path).bufferedReader().use { it.readText() }
                val json = JSONObject(jsonText)
                val displayName = json.optString("name").ifBlank { id }
                val isDark = json.optString("type").equals("light", ignoreCase = true).not()
                themeRegistry.loadTheme(
                    ThemeModel(
                        IThemeSource.fromInputStream(
                            FileProviderRegistry.getInstance().tryGetInputStream(path), path, null
                        ), id
                    ).apply { this.isDark = isDark })

                loaded += ThemeInfo(id = id, displayName = displayName, isDark = isDark)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load theme: $id", e)
            }
        }
        val sorted = loaded.sortedBy { it.displayName.lowercase() }
        availableThemes = sorted
        val default = if (sorted.any { it.id == DEFAULT_THEME }) DEFAULT_THEME
        else sorted.firstOrNull()?.id ?: DEFAULT_THEME
        themeRegistry.setTheme(default)
    }
}
