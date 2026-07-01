@file:Suppress("SpellCheckingInspection")

package me.zhanghai.android.filesfork.viewer.text

import android.content.Context
import android.graphics.Typeface

object FontRegistry {
    enum class FontSource { BUNDLED, SYSTEM, IMPORTED }
    data class FontOption(
        val id: String,
        val displayName: String,
        val source: FontSource,
        val path: String?,
    )

    private const val IMPORTED_FONTS_DIR = "imported_fonts"

    private fun importedFontsDir(context: Context): java.io.File =
        java.io.File(context.filesDir, IMPORTED_FONTS_DIR).apply { mkdirs() }

    private val bundledFonts = listOf(
        FontOption(
            "asset:fira_code", "Fira Code", FontSource.BUNDLED, "fonts/FiraCode-Regular.ttf"
        ),
        FontOption(
            "asset:jetbrains_mono",
            "JetBrains Mono",
            FontSource.BUNDLED,
            "fonts/JetBrainsMono-Regular.ttf"
        ),
        FontOption(
            "asset:atkinson_hyperlegible",
            "Atkinson Hyperlegible",
            FontSource.BUNDLED,
            "fonts/AtkinsonHyperlegible-Regular.ttf"
        ),
        FontOption(
            "asset:source_code_pro",
            "Source Code Pro",
            FontSource.BUNDLED,
            "fonts/SourceCodePro-Regular.ttf"
        )
    )

    private var cachedSystemFonts: List<FontOption>? = null
    private val typefaceCache = mutableMapOf<String, Typeface>()

    fun scanSystemFonts(): List<FontOption> {
        cachedSystemFonts?.let { return it }
        val dir = java.io.File("/system/fonts")
        val files = dir.listFiles { f ->
            f.isFile && f.canRead() && f.extension.lowercase() in setOf("ttf", "ttc", "otf")
        } ?: emptyArray()

        val result = files.sortedBy { it.name }.mapNotNull { file ->
            val typeface = try {
                Typeface.createFromFile(file)
            } catch (_: Exception) {
                null
            } ?: return@mapNotNull null
            if (typeface == Typeface.DEFAULT && file.name != "Roboto-Regular.ttf") {
                // createFromFile silently falls back to default on failure on some OEM builds; skip those.
            }
            FontOption(
                id = "system:${file.absolutePath}",
                displayName = file.nameWithoutExtension
                    .replace('-', ' ')
                    .replace('_', ' '),
                source = FontSource.SYSTEM,
                path = file.absolutePath,
            )
        }
        cachedSystemFonts = result
        return result
    }

    fun availableFonts(context: Context): List<FontOption> =
        bundledFonts + scanImportedFonts(context) + scanSystemFonts()

    fun loadTypeface(context: Context, fontId: String): Typeface {
        typefaceCache[fontId]?.let { return it }
        val option = availableFonts(context).find { it.id == fontId } ?: bundledFonts.first()
        val path = option.path ?: return Typeface.MONOSPACE
        val typeface = try {
            when (option.source) {
                FontSource.BUNDLED -> Typeface.createFromAsset(context.assets, path)
                FontSource.SYSTEM, FontSource.IMPORTED -> Typeface.createFromFile(path)
            }
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
        typefaceCache[fontId] = typeface
        return typeface
    }

    fun importFont(context: Context, uri: android.net.Uri, originalFileName: String): FontOption? {
        val ext = originalFileName.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("ttf", "otf", "ttc")) return null

        val safeName = originalFileName
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val destFile = java.io.File(importedFontsDir(context), "$safeName.$ext")
        val id = "imported:${destFile.absolutePath}"

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            typefaceCache.remove(id)
            val typeface = Typeface.createFromFile(destFile)
            typefaceCache[id] = typeface
            cachedImportedFonts = null

            FontOption(
                id = id,
                displayName = originalFileName
                    .substringBeforeLast('.')
                    .replace('-', ' ')
                    .replace('_', ' '),
                source = FontSource.IMPORTED,
                path = destFile.absolutePath,
            )
        } catch (_: Exception) {
            destFile.delete()
            null
        }
    }

    fun deleteImportedFont(fontId: String) {
        if (!fontId.startsWith("imported:")) return
        val path = fontId.removePrefix("imported:")
        java.io.File(path).delete()
        cachedImportedFonts = null
        typefaceCache.remove(fontId)
    }

    private var cachedImportedFonts: List<FontOption>? = null

    fun scanImportedFonts(context: Context): List<FontOption> {
        cachedImportedFonts?.let { return it }
        val files = importedFontsDir(context).listFiles { f ->
            f.isFile && f.extension.lowercase() in setOf("ttf", "otf", "ttc")
        } ?: emptyArray()
        val result = files.sortedBy { it.name }.mapNotNull { file ->
            try {
                Typeface.createFromFile(file)
            } catch (_: Exception) {
                return@mapNotNull null
            }
            FontOption(
                id = "imported:${file.absolutePath}",
                displayName = file.nameWithoutExtension
                    .replace('-', ' ')
                    .replace('_', ' '),
                source = FontSource.IMPORTED,
                path = file.absolutePath,
            )
        }
        cachedImportedFonts = result
        return result
    }
}
