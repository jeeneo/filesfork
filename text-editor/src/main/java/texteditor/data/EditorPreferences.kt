package texteditor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import org.json.JSONArray
import texteditor.data.encoding.EncodingCatalog
import texteditor.data.encoding.LineEnding
import texteditor.data.registry.FontRegistry
import texteditor.data.registry.ThemeRegistry

private val Context.editorPrefs: SharedPreferences
    get() = getSharedPreferences("text_editor_prefs", Context.MODE_PRIVATE)

internal object PrefKeys {
    const val WORD_WRAP = "word_wrap"
    const val SYNTAX_HIGHLIGHT = "syntax_highlight"
    const val MINIMAP_SHOWN = "minimap_shown"
    const val MINIMAP_BLOCKS = "minimap_blocks"
    const val SYMBOL_BAR = "symbol_bar"
    const val LINE_NUMBERS = "line_numbers"
    const val SELECTED_THEME = "selected_theme"
    const val TEXT_SIZE_PX = "text_size_px"
    const val SELECTED_FONT = "selected_font"
    const val INVISIBLE_CHARS = "invisible_chars"
    const val READ_ONLY = "read_only"
    const val AUTO_COMPLETION = "auto_completion"
    const val DEFAULT_ENCODING = "default_encoding"
    const val DEFAULT_LINE_ENDING = "default_line_ending"
    const val DETECT_ENCODING = "detect_encoding"
    const val PINNED_FILES = "pinned_files"
    const val AUTO_FOCUS = "auto_focus_editor"
    const val SHOW_DOC_TABS = "show_document_tabs"
}

internal object EditorDefaults {
    const val WORD_WRAP = true
    const val SYNTAX_HIGHLIGHT = true
    const val TEXT_SIZE_PX = 0f
    const val MINIMAP_SHOWN = false
    const val MINIMAP_BLOCKS = false
    const val SYMBOL_BAR = true
    const val LINE_NUMBERS = true
    const val INVISIBLE_CHARS = true
    const val READ_ONLY = false
    const val AUTO_COMPLETION = true
    const val DEFAULT_ENCODING = "utf8"
    const val DEFAULT_LINE_ENDING = "LF"
    const val DETECT_ENCODING = true
    const val SELECTED_THEME = "default"
    const val SELECTED_FONT = "fonts/CascadiaCode-Regular.ttf"
    const val AUTO_FOCUS = true
    const val SHOW_DOC_TABS = false
}

@Immutable
internal data class EditorPrefsSnapshot(
    val wordWrap: Boolean,
    val syntaxHighlight: Boolean,
    val textSizePx: Float,
    val miniMap: Boolean,
    val miniMapBlocks: Boolean,
    val symbolBar: Boolean,
    val lineNumbers: Boolean,
    val invisibleChars: Boolean,
    val readOnly: Boolean,
    val autoCompletion: Boolean,
    val defaultEncodingId: String,
    val defaultLineEnding: LineEnding,
    val detectEncoding: Boolean,
    val pinnedPaths: List<String>,
    val selectedTheme: String,
    val selectedFont: String,
    val autoFocus: Boolean,
    val documentTabs: Boolean,
)

internal class EditorPreferences(private val context: Context) {
    fun load(): EditorPrefsSnapshot {
        val prefs = context.editorPrefs
        val defaultEncodingId =
            prefs.getString(PrefKeys.DEFAULT_ENCODING, EditorDefaults.DEFAULT_ENCODING)
                ?: EditorDefaults.DEFAULT_ENCODING
        val defaultLineEnding = runCatching {
            LineEnding.valueOf(
                prefs.getString(PrefKeys.DEFAULT_LINE_ENDING, EditorDefaults.DEFAULT_LINE_ENDING)
                    ?: EditorDefaults.DEFAULT_LINE_ENDING
            )
        }.getOrDefault(LineEnding.LF)
        return EditorPrefsSnapshot(
            wordWrap = prefs.getBoolean(PrefKeys.WORD_WRAP, EditorDefaults.WORD_WRAP),
            syntaxHighlight = prefs.getBoolean(
                PrefKeys.SYNTAX_HIGHLIGHT, EditorDefaults.SYNTAX_HIGHLIGHT
            ),
            textSizePx = prefs.getFloat(PrefKeys.TEXT_SIZE_PX, EditorDefaults.TEXT_SIZE_PX),
            miniMap = prefs.getBoolean(PrefKeys.MINIMAP_SHOWN, EditorDefaults.MINIMAP_SHOWN),
            miniMapBlocks = prefs.getBoolean(
                PrefKeys.MINIMAP_BLOCKS, EditorDefaults.MINIMAP_BLOCKS
            ),
            symbolBar = prefs.getBoolean(PrefKeys.SYMBOL_BAR, EditorDefaults.SYMBOL_BAR),
            lineNumbers = prefs.getBoolean(PrefKeys.LINE_NUMBERS, EditorDefaults.LINE_NUMBERS),
            invisibleChars = prefs.getBoolean(
                PrefKeys.INVISIBLE_CHARS, EditorDefaults.INVISIBLE_CHARS
            ),
            readOnly = prefs.getBoolean(PrefKeys.READ_ONLY, EditorDefaults.READ_ONLY),
            autoCompletion = prefs.getBoolean(
                PrefKeys.AUTO_COMPLETION, EditorDefaults.AUTO_COMPLETION
            ),
            defaultEncodingId = if (EncodingCatalog.byId(defaultEncodingId) == null) {
                EditorDefaults.DEFAULT_ENCODING
            } else {
                defaultEncodingId
            },
            defaultLineEnding = defaultLineEnding,
            detectEncoding = prefs.getBoolean(
                PrefKeys.DETECT_ENCODING, EditorDefaults.DETECT_ENCODING
            ),
            pinnedPaths = parsePinnedPaths(prefs.getString(PrefKeys.PINNED_FILES, null)),
            selectedTheme = resolveSelectedId(
                stored = prefs.getString(PrefKeys.SELECTED_THEME, null) ?: "",
                availableIds = ThemeRegistry.availableThemes.map { it.id },
                defaultName = EditorDefaults.SELECTED_THEME,
            ),
            selectedFont = resolveSelectedId(
                stored = prefs.getString(PrefKeys.SELECTED_FONT, null) ?: "",
                availableIds = FontRegistry.availableFonts(context, includeSystemFonts = true)
                    .map { it.id },
                defaultName = EditorDefaults.SELECTED_FONT,
            ),
            autoFocus = prefs.getBoolean(PrefKeys.AUTO_FOCUS, EditorDefaults.AUTO_FOCUS),
            documentTabs = prefs.getBoolean(PrefKeys.SHOW_DOC_TABS, EditorDefaults.SHOW_DOC_TABS)
        )
    }

    fun save(snapshot: EditorPrefsSnapshot) {
        context.editorPrefs.edit {
            putFloat(PrefKeys.TEXT_SIZE_PX, snapshot.textSizePx)
            putBoolean(PrefKeys.WORD_WRAP, snapshot.wordWrap)
            putBoolean(PrefKeys.SYNTAX_HIGHLIGHT, snapshot.syntaxHighlight)
            putString(PrefKeys.SELECTED_THEME, snapshot.selectedTheme)
            putBoolean(PrefKeys.MINIMAP_SHOWN, snapshot.miniMap)
            putBoolean(PrefKeys.MINIMAP_BLOCKS, snapshot.miniMapBlocks)
            putBoolean(PrefKeys.SYMBOL_BAR, snapshot.symbolBar)
            putBoolean(PrefKeys.LINE_NUMBERS, snapshot.lineNumbers)
            putString(PrefKeys.SELECTED_FONT, snapshot.selectedFont)
            putBoolean(PrefKeys.INVISIBLE_CHARS, snapshot.invisibleChars)
            putBoolean(PrefKeys.READ_ONLY, snapshot.readOnly)
            putBoolean(PrefKeys.AUTO_COMPLETION, snapshot.autoCompletion)
            putString(PrefKeys.DEFAULT_ENCODING, snapshot.defaultEncodingId)
            putString(PrefKeys.DEFAULT_LINE_ENDING, snapshot.defaultLineEnding.name)
            putBoolean(PrefKeys.DETECT_ENCODING, snapshot.detectEncoding)
            putString(PrefKeys.PINNED_FILES, JSONArray(snapshot.pinnedPaths).toString())
            putBoolean(PrefKeys.AUTO_FOCUS, snapshot.autoFocus)
            putBoolean(PrefKeys.SHOW_DOC_TABS, snapshot.documentTabs)
        }
    }
}

private fun parsePinnedPaths(raw: String?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())
}

private fun resolveSelectedId(
    stored: String,
    availableIds: List<String>,
    defaultName: String = "",
): String = when {
    stored.isNotBlank() && stored in availableIds -> stored
    defaultName.isNotBlank() && defaultName in availableIds -> defaultName
    else -> availableIds.firstOrNull() ?: ""
}
