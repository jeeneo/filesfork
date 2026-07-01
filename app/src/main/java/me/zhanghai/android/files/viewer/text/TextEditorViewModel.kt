@file:Suppress("SpellCheckingInspection")

package me.zhanghai.android.filesfork.viewer.text

import android.app.Application
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.drawable.toDrawable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import java8.nio.file.Files
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private val android.content.Context.editorPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "text_editor_prefs"
)

enum class SaveButtonState { IDLE, SAVED, ERROR }

data class TopBarAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

fun buildColorScheme(): TextMateColorScheme =
    TextMateColorScheme.create(ThemeRegistry.getInstance())

val SYMBOLS = listOf(
    ">>" to "\t",
    "{" to "{}",
    "}" to "}",
    "(" to "(",
    ")" to ")",
    "=" to "=",
    "," to ",",
    "." to ".",
    ";" to ";",
    "\"" to "\"",
    "?" to "?",
    "+" to "+",
    "-" to "-",
    "*" to "*",
    "/" to "/",
    "<" to "<",
    ">" to ">",
    "[" to "[",
    "]" to "]",
    ":" to ":"
)

private object PrefKeys {
    val WORD_WRAP = booleanPreferencesKey("word_wrap")
    val SYNTAX_HIGHLIGHT = booleanPreferencesKey("syntax_highlight")
    val MINIMAP_SHOWN = booleanPreferencesKey("minimap_shown")
    val MINIMAP_BLOCKS = booleanPreferencesKey("minimap_blocks")
    val SYMBOL_BAR = booleanPreferencesKey("symbol_bar")
    val LINE_NUMBERS = booleanPreferencesKey("line_numbers")
    val SELECTED_THEME = stringPreferencesKey("selected_theme")
    val TEXT_SIZE_PX = floatPreferencesKey("text_size_px")
    val SELECTED_FONT = stringPreferencesKey("selected_font")
    val INVISIBLE_CHARS = booleanPreferencesKey("invisible_chars")
}

sealed interface LoadState {
    data object Loading : LoadState
    data object Success : LoadState
    data class Error(val message: String) : LoadState
}

class TextEditorViewModel(application: Application) : AndroidViewModel(application) {
    var loadState: LoadState by mutableStateOf(LoadState.Loading)
        private set
    var grammarsReady: Boolean by mutableStateOf(false)
        private set
    private var grammarLoadJob: Job? = null
    var isModified: Boolean by mutableStateOf(false)
        private set
    var syntaxHighlight: Boolean by mutableStateOf(true)
        private set
    var wordWrap: Boolean by mutableStateOf(false)
        private set
    var miniMap: Boolean by mutableStateOf(false)
        private set
    var miniMapBlocks: Boolean by mutableStateOf(false)
        private set
    var symbolBar: Boolean by mutableStateOf(false)
        private set
    var lineNumbers: Boolean by mutableStateOf(true)
    var selectedTheme: String by mutableStateOf("darcula")
        private set
    var prefsLoaded: Boolean by mutableStateOf(false)
        private set
    var textSizePx: Float by mutableFloatStateOf(0f)
        private set
    var selectedFont: String by mutableStateOf("asset:fira_code")
        private set
    var fontOptions: List<FontRegistry.FontOption> by mutableStateOf(emptyList())
        private set
    var invisibleChars: Boolean by mutableStateOf(false)
        private set
    var content: Content by mutableStateOf(Content())
        private set
    private var originalContent: String = ""
    private val dataStore get() = getApplication<Application>().editorPrefsDataStore
    private var pendingSelectionLeft: Int = -1
    private var pendingSelectionRight: Int = -1
    private var pendingScrollX: Int = 0
    private var pendingScrollY: Int = 0

    fun initialize(path: Path) {
        viewModelScope.launch {
            if (prefsLoaded) return@launch
            val app = getApplication<Application>()
            TextEditorInitializer.initThemeAndPrefs(app)
            loadPrefs()
            load(path)
            refreshFontOptions()
            grammarsLoaded(app)
        }
    }

    private fun grammarsLoaded(app: Application) {
        if (grammarsReady) return
        if (grammarLoadJob?.isActive == true) return

        grammarLoadJob = viewModelScope.launch {
            try {
                TextEditorInitializer.initGrammars(app)
                grammarsReady = true
            } finally {
                grammarLoadJob = null
            }
        }
    }

    fun saveCursorState(editor: CodeEditor) {
        val cursor = editor.cursor
        pendingSelectionLeft = cursor.left
        pendingSelectionRight = cursor.right
        pendingScrollX = editor.scroller.currX
        pendingScrollY = editor.scroller.currY
    }

    fun restoreCursorState(editor: CodeEditor) {
        if (pendingSelectionLeft < 0) return
        val len = editor.text.length
        val left = pendingSelectionLeft.coerceIn(0, len)
        val right = pendingSelectionRight.coerceIn(0, len)
        val leftPos = editor.text.indexer.getCharPosition(minOf(left, right))
        val rightPos = editor.text.indexer.getCharPosition(maxOf(left, right))
        editor.setSelectionRegion(
            leftPos.line, leftPos.column, rightPos.line, rightPos.column, false
        )
        editor.scroller.startScroll(pendingScrollX, pendingScrollY, 0, 0, 0)
        editor.scroller.abortAnimation()
        editor.postInvalidate()
    }

    private suspend fun loadPrefs() {
        val prefs = dataStore.data.first()
        wordWrap = prefs[PrefKeys.WORD_WRAP] ?: false
        syntaxHighlight = prefs[PrefKeys.SYNTAX_HIGHLIGHT] ?: true
        selectedTheme = prefs[PrefKeys.SELECTED_THEME] ?: "darcula"
        textSizePx = prefs[PrefKeys.TEXT_SIZE_PX] ?: 0f
        miniMap = prefs[PrefKeys.MINIMAP_SHOWN] ?: false
        miniMapBlocks = prefs[PrefKeys.MINIMAP_BLOCKS] ?: false
        symbolBar = prefs[PrefKeys.SYMBOL_BAR] ?: false
        lineNumbers = prefs[PrefKeys.LINE_NUMBERS] ?: true
        selectedFont = prefs[PrefKeys.SELECTED_FONT] ?: "asset:fira_code"
        invisibleChars = prefs[PrefKeys.INVISIBLE_CHARS] ?: false
        prefsLoaded = true
    }

    private fun savePrefs() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PrefKeys.TEXT_SIZE_PX] = textSizePx
                prefs[PrefKeys.WORD_WRAP] = wordWrap
                prefs[PrefKeys.SYNTAX_HIGHLIGHT] = syntaxHighlight
                prefs[PrefKeys.SELECTED_THEME] = selectedTheme
                prefs[PrefKeys.MINIMAP_SHOWN] = miniMap
                prefs[PrefKeys.MINIMAP_BLOCKS] = miniMapBlocks
                prefs[PrefKeys.SYMBOL_BAR] = symbolBar
                prefs[PrefKeys.LINE_NUMBERS] = lineNumbers
                prefs[PrefKeys.SELECTED_FONT] = selectedFont
                prefs[PrefKeys.INVISIBLE_CHARS] = invisibleChars
            }
        }
    }

    fun refreshFontOptions() {
        viewModelScope.launch(Dispatchers.IO) {
            fontOptions = FontRegistry.availableFonts(getApplication())
        }
    }

    fun toggleWordWrap() {
        wordWrap = !wordWrap
        savePrefs()
    }

    fun toggleSyntaxHighlight() {
        syntaxHighlight = !syntaxHighlight
        savePrefs()
        if (syntaxHighlight) grammarsLoaded(getApplication())
    }

    fun setTheme(theme: String) {
        selectedTheme = theme
        savePrefs()
    }

    fun saveTextSize(px: Float) {
        textSizePx = px
        savePrefs()
    }

    fun toggleMinimap() {
        miniMap = !miniMap
        savePrefs()
    }

    fun toggleMinimapBlocks() {
        miniMapBlocks = !miniMapBlocks
        savePrefs()
    }

    fun toggleSymbolBar() {
        symbolBar = !symbolBar
        savePrefs()
    }

    fun toggleLineNumbers() {
        lineNumbers = !lineNumbers
        savePrefs()
    }

    fun toggleInvisibleChars() {
        invisibleChars = !invisibleChars
        savePrefs()
    }

    fun setFont(font: String) {
        selectedFont = font
        savePrefs()
    }

    fun deleteFont(fontId: String) {
        FontRegistry.deleteImportedFont(fontId)
        if (selectedFont == fontId) {
            setFont("asset:fira_code")
        }
        refreshFontOptions()
    }

    fun load(path: Path) {
        loadState = LoadState.Loading
        isModified = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decoder = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
                val text = Files.newInputStream(path).use { stream ->
                    java.io.InputStreamReader(stream, decoder).readText()
                }
                originalContent = text
                withContext(Dispatchers.Main) {
                    content = Content(text)
                    loadState = LoadState.Success
                }
            } catch (e: OutOfMemoryError) {
                withContext(Dispatchers.Main) {
                    loadState = LoadState.Error(e.localizedMessage ?: "Out of memory")
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    loadState = LoadState.Error(e.localizedMessage ?: "Read error")
                }
            }
        }
    }

    fun onContentChanged(text: String) {
        isModified = text != originalContent
    }

    fun save(
        path: Path, getText: () -> String, onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val text = getText()
                withContext(Dispatchers.IO) {
                    Files.newBufferedWriter(path, Charsets.UTF_8).use { it.write(text) }
                }
                originalContent = text
                isModified = false
                onSuccess()
            } catch (e: IOException) {
                onError(e.localizedMessage ?: "Write error")
            }
        }
    }
}

fun deriveScrollbarDrawables(): Pair<ColorDrawable, ColorDrawable> {
    val trackDrawable = 0x29FFFFFF.toDrawable()
    val thumbDrawable = 0x80FFFFFF.toInt().toDrawable()
    return Pair(trackDrawable, thumbDrawable)
}
