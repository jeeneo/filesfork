package me.zhanghai.android.filesfork.viewer.text

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import java8.nio.file.Files
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private val android.content.Context.editorPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "text_editor_prefs"
)

sealed interface AppLoadState {
    data object Idle : AppLoadState
    data object Ready : AppLoadState
    data object GrammarReady : AppLoadState
    data class Error(val message: String) : AppLoadState
}


private object PrefKeys {
    val WORD_WRAP = booleanPreferencesKey("word_wrap")
    val SYNTAX_HIGHLIGHT = booleanPreferencesKey("syntax_highlight")
    val MINIMAP_SHOWN = booleanPreferencesKey("minimap_shown")
    val MINIMAP_BLOCKS = booleanPreferencesKey("minimap_blocks")
    val SELECTED_THEME = stringPreferencesKey("selected_theme")
    val TEXT_SIZE_PX = floatPreferencesKey("text_size_px")
}

sealed interface LoadState {
    data object Loading : LoadState
    data object Success : LoadState
    data class Error(val message: String) : LoadState
}

class TextEditorViewModel(application: Application) : AndroidViewModel(application) {
    var appLoadState: AppLoadState by mutableStateOf(AppLoadState.Idle)
        private set
    var loadState: LoadState by mutableStateOf(LoadState.Loading)
        private set
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
    var selectedTheme: String by mutableStateOf("darcula")
        private set
    var prefsLoaded: Boolean by mutableStateOf(false)
        private set
    var textSizePx: Float by mutableFloatStateOf(0f)
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
            val app = getApplication<Application>()
            TextEditorInitializer.initThemeAndPrefs(app)
            loadPrefs()
            appLoadState = AppLoadState.Ready
            load(path)
            launch {
                TextEditorInitializer.initGrammars(app)
                appLoadState = AppLoadState.GrammarReady
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
        prefsLoaded = true
    }

    private fun savePrefs() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PrefKeys.WORD_WRAP] = wordWrap
                prefs[PrefKeys.SYNTAX_HIGHLIGHT] = syntaxHighlight
                prefs[PrefKeys.SELECTED_THEME] = selectedTheme
                prefs[PrefKeys.MINIMAP_SHOWN] = miniMap
                prefs[PrefKeys.MINIMAP_BLOCKS] = miniMapBlocks
            }
        }
    }

    fun toggleWordWrap() {
        wordWrap = !wordWrap
        savePrefs()
    }

    fun toggleSyntaxHighlight() {
        syntaxHighlight = !syntaxHighlight
        savePrefs()
    }

    fun setTheme(theme: String) {
        selectedTheme = theme
        savePrefs()
    }

    fun saveTextSize(px: Float) {
        textSizePx = px
        viewModelScope.launch {
            dataStore.edit { it[PrefKeys.TEXT_SIZE_PX] = px }
        }
    }

    fun toggleMinimap() {
        miniMap = !miniMap
        savePrefs()
    }

    fun toggleMinimapBlocks() {
        miniMapBlocks = !miniMapBlocks
        savePrefs()
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
