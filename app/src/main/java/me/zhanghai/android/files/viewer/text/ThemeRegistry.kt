package me.zhanghai.android.filesfork.viewer.text

import android.content.Context
import android.util.Log
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import org.eclipse.tm4e.core.registry.IThemeSource

object ThemeRegistry {
    private const val TAG = "ThemeManager"
    private const val THEMES_DIR = "themes"
    private const val DEFAULT_THEME = "darcula"
    var availableThemes: List<String> = emptyList()
        private set

    fun initialize(context: Context) {
        availableThemes = try {
            context.assets.list(THEMES_DIR)
                ?.filter { it.endsWith(".json") }
                ?.map { it.removeSuffix(".json") }
                ?.sorted()
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list themes", e)
            emptyList()
        }

        val themeRegistry = ThemeRegistry.getInstance()
        for (name in availableThemes) {
            val path = "$THEMES_DIR/$name.json"
            try {
                themeRegistry.loadTheme(
                    ThemeModel(
                        IThemeSource.fromInputStream(
                            FileProviderRegistry.getInstance().tryGetInputStream(path), path, null
                        ), name
                    ).apply {
                        isDark = !name.contains("light", ignoreCase = true)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load theme: $name", e)
            }
        }

        val default = if (availableThemes.contains(DEFAULT_THEME)) DEFAULT_THEME
        else availableThemes.firstOrNull() ?: DEFAULT_THEME
        themeRegistry.setTheme(default)
    }
}
