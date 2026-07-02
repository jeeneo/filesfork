@file:Suppress("SpellCheckingInspection")

package me.zhanghai.android.filesfork.viewer.text

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

object FontRegistry {
    data class FontOption(val id: String, val displayName: String)
    private const val IMPORTED_FONTS_DIR = "imported_fonts"
    private const val BUNDLED_FONTS_ASSET_DIR = "fonts"
    private val IMPORTABLE_EXTENSIONS = setOf("ttf", "otf", "ttc")
    private fun importedFontsDir(context: Context): File =
        File(context.filesDir, IMPORTED_FONTS_DIR).apply { mkdirs() }
    private var cachedBundledFonts: List<FontOption>? = null
    private fun bundledFonts(context: Context): List<FontOption> {
        cachedBundledFonts?.let { return it }
        val names = try {
            context.assets.list(BUNDLED_FONTS_ASSET_DIR) ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        val result =
            names.filter { it.substringAfterLast('.', "").lowercase() in IMPORTABLE_EXTENSIONS }
                .sorted().map { name ->
                    FontOption(
                        id = "$BUNDLED_FONTS_ASSET_DIR/$name", displayName = name
                    )
                }
        cachedBundledFonts = result
        return result
    }

    private fun bundledFontIds(context: Context): Set<String> =
        bundledFonts(context).map { it.id }.toSet()

    fun defaultFont(context: Context): FontOption = bundledFonts(context).first()

    private var cachedSystemFonts: List<FontOption>? = null
    private var cachedImportedFonts: List<FontOption>? = null
    private val typefaceCache = mutableMapOf<String, Typeface>()

    fun scanSystemFonts(): List<FontOption> {
        cachedSystemFonts?.let { return it }
        val files = File("/system/fonts").listFiles { f ->
            f.isFile && f.canRead() && f.extension.lowercase() in IMPORTABLE_EXTENSIONS
        } ?: emptyArray()
        val result = files.sortedBy { it.name }.map { file ->
            FontOption(id = file.absolutePath, displayName = file.name)
        }
        cachedSystemFonts = result
        return result
    }

    fun scanImportedFonts(context: Context): List<FontOption> {
        cachedImportedFonts?.let { return it }
        val files = importedFontsDir(context).listFiles { f ->
            f.isFile && f.extension.lowercase() in IMPORTABLE_EXTENSIONS
        } ?: emptyArray()
        val result = files.sortedBy { it.name }.map { file ->
            FontOption(id = file.absolutePath, displayName = file.name)
        }
        cachedImportedFonts = result
        return result
    }

    fun availableFonts(context: Context): List<FontOption> =
        bundledFonts(context) + scanImportedFonts(context) + scanSystemFonts()

    fun isImported(context: Context, fontId: String): Boolean =
        File(fontId).parentFile == importedFontsDir(context)

    fun loadTypeface(context: Context, fontId: String): Typeface {
        typefaceCache[fontId]?.let { return it }

        val id =
            if (availableFonts(context).any { it.id == fontId }) fontId else defaultFont(context).id

        val typeface = try {
            if (id in bundledFontIds(context)) {
                Typeface.createFromAsset(context.assets, id)
            } else {
                Typeface.createFromFile(id)
            }
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
        typefaceCache[id] = typeface
        return typeface
    }

    fun importFont(context: Context, uri: Uri, originalFileName: String): FontOption? {
        val ext = originalFileName.substringAfterLast('.', "").lowercase()
        if (ext !in IMPORTABLE_EXTENSIONS) return null
        val safeStem =
            originalFileName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_")
        val destFile = File(importedFontsDir(context), "$safeStem.$ext")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            typefaceCache[destFile.absolutePath] = Typeface.createFromFile(destFile)
            cachedImportedFonts = null

            FontOption(id = destFile.absolutePath, displayName = originalFileName)
        } catch (_: Exception) {
            destFile.delete()
            null
        }
    }

    fun deleteImportedFont(context: Context, fontId: String, currentFontId: String): String {
        val file = File(fontId)
        if (file.parentFile == importedFontsDir(context)) {
            file.delete()
            cachedImportedFonts = null
            typefaceCache.remove(fontId)
        }
        return if (currentFontId == fontId) defaultFont(context).id else currentFontId
    }
}
