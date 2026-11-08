@file:Suppress("SpellCheckingInspection")

package texteditor.data.registry

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import java.io.File

object FontRegistry {
    data class FontOption(
        val id: String,
        val displayName: String,
    )

    data class FontData(
        val bytes: ByteArray,
        val mimeType: String,
        val format: String,
    )

    private const val IMPORTED_FONTS_DIR = "imported_fonts"
    private const val BUNDLED_FONTS_ASSET_DIR = "fonts"
    private const val DEFAULT_FALLBACK_ID = "__default_monospace__"
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
                        id = "$BUNDLED_FONTS_ASSET_DIR/$name",
                        displayName = name,
                    )
                }
        cachedBundledFonts = result
        return result
    }

    private fun bundledFontIds(context: Context): Set<String> =
        bundledFonts(context).map { it.id }.toSet()

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

    fun availableFonts(
        context: Context,
        includeSystemFonts: Boolean = false,
    ): List<FontOption> {
        val base = bundledFonts(context) + scanImportedFonts(context)
        return if (includeSystemFonts) base + scanSystemFonts() else base
    }

    fun isImported(
        context: Context,
        fontId: String,
    ): Boolean = File(fontId).parentFile == importedFontsDir(context)

    fun isSystemFont(fontId: String): Boolean = scanSystemFonts().any { it.id == fontId }

    private fun resolveFontId(
        context: Context,
        fontId: String,
    ): String? = if (availableFonts(context, includeSystemFonts = true).any { it.id == fontId }) {
        fontId
    } else {
        bundledFonts(context).firstOrNull()?.id
    }

    fun readFontData(
        context: Context,
        fontId: String,
    ): FontData? {
        val resolvedId = resolveFontId(context, fontId) ?: return null
        val bytes = try {
            if (resolvedId in bundledFontIds(context)) {
                context.assets.open(resolvedId).use { it.readBytes() }
            } else {
                File(resolvedId).readBytes()
            }
        } catch (_: Exception) {
            return null
        }
        return when (resolvedId.substringAfterLast('.', "").lowercase()) {
            "otf" -> FontData(bytes, "font/otf", "opentype")
            "ttc" -> FontData(bytes, "font/collection", "truetype")
            else -> FontData(bytes, "font/ttf", "truetype")
        }
    }

    fun loadTypeface(
        context: Context,
        fontId: String,
    ): Typeface {
        typefaceCache[fontId]?.let { return it }
        val resolvedId = resolveFontId(context, fontId) ?: DEFAULT_FALLBACK_ID

        if (resolvedId == DEFAULT_FALLBACK_ID) {
            typefaceCache[resolvedId] = Typeface.MONOSPACE
            return Typeface.MONOSPACE
        }

        val typeface = try {
            val isBundled = resolvedId in bundledFontIds(context)
            val weighted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val builder = if (isBundled) {
                        Typeface.Builder(context.assets, resolvedId)
                    } else {
                        Typeface.Builder(resolvedId)
                    }
                    builder.setFontVariationSettings("'wght' 500").build()
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            weighted ?: if (isBundled) {
                Typeface.createFromAsset(context.assets, resolvedId)
            } else {
                Typeface.createFromFile(resolvedId)
            }
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
        typefaceCache[resolvedId] = typeface
        return typeface
    }

    fun importFont(
        context: Context,
        uri: Uri,
        originalFileName: String,
    ): FontOption? {
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

    fun deleteImportedFont(
        context: Context,
        fontId: String,
        currentFontId: String,
    ): String {
        val file = File(fontId)
        if (file.parentFile == importedFontsDir(context)) {
            file.delete()
            cachedImportedFonts = null
            typefaceCache.remove(fontId)
        }
        return if (currentFontId == fontId) bundledFonts(context).firstOrNull()?.id
            ?: DEFAULT_FALLBACK_ID else currentFontId
    }
}
