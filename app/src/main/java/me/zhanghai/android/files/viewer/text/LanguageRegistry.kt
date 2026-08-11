@file:Suppress("SpellCheckingInspection")

package me.zhanghai.android.filesfork.viewer.text

import android.content.Context
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader

object LanguageRegistry {
    private const val TAG = "LanguageRegistry"
    private const val LANGUAGES_JSON_PATH = "textmate/languages/languages.json"
    private val FILE_EXTENSIONS_BY_LANGUAGE: Map<String, List<String>> = mapOf(
        "java" to listOf("java"),
        "java-properties" to listOf("properties"),
        "kotlin" to listOf("kt", "kts"),
        "python" to listOf("py"),
        "lua" to listOf("lua", "eel"),
        "shellscript" to listOf("sh"),
        "xml" to listOf("xml", "xsd", "svg"),
        "json" to listOf("json", "jsonc"),
        "jsonl" to listOf("jsonl", "ndjson"),
        "html" to listOf("html", "htm"),
        "toml" to listOf("toml"),
        "javascript" to listOf("js", "jsx", "mjs", "cjs"),
        "typescript" to listOf("ts", "tsx"),
        "markdown" to listOf("md", "markdown", "mkdown", "mkd", "mdown"),
        "log" to listOf("log", "logfile"),
        "nix" to listOf("nix")
    )

    private var _initialized = false
    private lateinit var _scopeByLanguage: Map<String, String>
    private lateinit var _scopeByExtension: Map<String, String>
    private lateinit var _languageNames: Set<String>

    fun scopeForExtension(extension: String): String? {
        if (!_initialized) return null
        return _scopeByExtension[extension.lowercase()]
    }

    fun scopeForLanguage(language: String): String? {
        if (!_initialized) return null
        return _scopeByLanguage[language.lowercase()]
    }

    fun supportedLanguages(): List<String> {
        if (!_initialized) return emptyList()
        return _languageNames.toList().sorted()
    }

    fun initialize(context: Context) {
        if (_initialized) return
        _initialized = true

        try {
            val languagesJson = context.assets.open(LANGUAGES_JSON_PATH).use { inputStream ->
                inputStream.bufferedReader().use(BufferedReader::readText)
            }
            _scopeByLanguage = parseLanguagesJson(languagesJson)
            _languageNames = _scopeByLanguage.keys
            _scopeByExtension = buildExtensionMap(_scopeByLanguage)
            Log.d(
                TAG,
                "LanguageRegistry initialized: ${_languageNames.size} languages, ${_scopeByExtension.size} extensions"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LanguageRegistry", e)
            _scopeByLanguage = emptyMap()
            _scopeByExtension = emptyMap()
            _languageNames = emptySet()
        }
    }

    private fun parseLanguagesJson(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        return try {
            val root = JSONObject(json)
            val languages = root.optJSONArray("languages") ?: return result
            for (i in 0 until languages.length()) {
                val obj = languages.getJSONObject(i)
                val name = obj.optString("name").takeIf { it.isNotEmpty() } ?: continue
                val scopeName = obj.optString("scopeName").takeIf { it.isNotEmpty() } ?: continue
                result[name.lowercase()] = scopeName
            }
            result
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse languages JSON", e)
            result
        }
    }

    private fun buildExtensionMap(scopeByLanguage: Map<String, String>): Map<String, String> {
        return buildMap {
            for ((language, extensions) in FILE_EXTENSIONS_BY_LANGUAGE) {
                val scope = scopeByLanguage[language.lowercase()]
                if (scope != null) {
                    for (ext in extensions) {
                        put(ext.lowercase(), scope)
                    }
                } else {
                    Log.w(TAG, "No scope found for language: $language")
                }
            }
        }
    }
}
