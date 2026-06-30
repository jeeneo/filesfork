package me.zhanghai.android.filesfork.viewer.text

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.core.graphics.drawable.toDrawable
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource

object ThemeManager {
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

fun deriveScrollbarDrawables(scheme: EditorColorScheme): Pair<ColorDrawable, ColorDrawable> {
    val bgColor = scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND)
    val fgColor = scheme.getColor(EditorColorScheme.TEXT_NORMAL)
    if (bgColor == 0 || fgColor == 0) {
        return Pair(0x1A000000.toDrawable(), 0x80000000.toInt().toDrawable())
    }
    val trackR = (Color.red(fgColor) * 0.1f + Color.red(bgColor) * 0.9f).toInt()
    val trackG = (Color.green(fgColor) * 0.1f + Color.green(bgColor) * 0.9f).toInt()
    val trackB = (Color.blue(fgColor) * 0.1f + Color.blue(bgColor) * 0.9f).toInt()
    val trackAlpha = 0.6f
    val trackDrawable = Color.argb(
        (trackAlpha * 255).toInt(), trackR, trackG, trackB
    ).toDrawable()
    val thumbR = (Color.red(fgColor) * 0.5f + Color.red(bgColor) * 0.5f).toInt()
    val thumbG = (Color.green(fgColor) * 0.5f + Color.green(bgColor) * 0.5f).toInt()
    val thumbB = (Color.blue(fgColor) * 0.5f + Color.blue(bgColor) * 0.5f).toInt()
    val thumbAlpha = 0.6f
    val thumbDrawable = Color.argb(
        (thumbAlpha * 255).toInt(), thumbR, thumbG, thumbB
    ).toDrawable()
    return Pair(trackDrawable, thumbDrawable)
}
