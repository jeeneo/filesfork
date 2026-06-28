@file:Suppress("SpellCheckingInspection")

package me.zhanghai.android.filesfork.viewer.text

import android.content.Context
import android.util.Log
import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader

object LanguageRegistry {
    private const val TAG = "LanguageRegistry"
    private const val LANGUAGES_JSON_PATH = "textmate/languages/languages.json"
    private const val FILEEXT_TOML_PATH = "textmate/languages/fileext.toml"

    private data class LanguageEntry(val extensions: List<String>)

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
            val fileExtToml = context.assets.open(FILEEXT_TOML_PATH).use { inputStream ->
                inputStream.bufferedReader().use(BufferedReader::readText)
            }
            _scopeByExtension = parseFileExtToml(fileExtToml, _scopeByLanguage)
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

    private fun parseFileExtToml(
        toml: String, scopeByLanguage: Map<String, String>
    ): Map<String, String> {
        val mapper = tomlMapper { }
        val languages = mapper.decode<Map<String, LanguageEntry>>(toml)
        return buildMap {
            for ((language, entry) in languages) {
                val scope = scopeByLanguage[language.lowercase()]
                if (scope != null) {
                    for (ext in entry.extensions) {
                        put(ext.lowercase(), scope)
                    }
                } else {
                    Log.w(TAG, "No scope found for language: $language")
                }
            }
        }
    }
}
