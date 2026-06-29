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
import java8.nio.file.Files
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private val android.content.Context.editorPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "text_editor_prefs"
)

sealed interface AppLoadState {
    data object Idle : AppLoadState
    data object Ready : AppLoadState          // prefs + theme done, file loading
    data object GrammarReady : AppLoadState  // grammar also done
    data class Error(val message: String) : AppLoadState
}


private object PrefKeys {
    val WORD_WRAP = booleanPreferencesKey("word_wrap")
    val SYNTAX_HIGHLIGHT = booleanPreferencesKey("syntax_highlight")
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

    private suspend fun loadPrefs() {
        val prefs = dataStore.data.first()
        wordWrap = prefs[PrefKeys.WORD_WRAP] ?: false
        syntaxHighlight = prefs[PrefKeys.SYNTAX_HIGHLIGHT] ?: true
        selectedTheme = prefs[PrefKeys.SELECTED_THEME] ?: "darcula"
        textSizePx = prefs[PrefKeys.TEXT_SIZE_PX] ?: 0f
        prefsLoaded = true
    }


    private fun savePrefs() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PrefKeys.WORD_WRAP] = wordWrap
                prefs[PrefKeys.SYNTAX_HIGHLIGHT] = syntaxHighlight
                prefs[PrefKeys.SELECTED_THEME] = selectedTheme
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
