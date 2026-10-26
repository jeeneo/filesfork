package me.zhanghai.android.files.viewer.text

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import java8.nio.file.Files
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry as EditorThemeRegistry

private val Context.editorPrefs: SharedPreferences
    get() = getSharedPreferences("text_editor_prefs", Context.MODE_PRIVATE)

enum class SaveButtonState { IDLE, SAVED, ERROR }

data class TopBarAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

const val LARGE_FILE_WARNING_BYTES = 10L * 1024 * 1024

fun buildColorScheme(): TextMateColorScheme =
    TextMateColorScheme.create(EditorThemeRegistry.getInstance())

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
    ":" to ":",
)

private object PrefKeys {
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
}

private object EditorDefaults {
    const val WORD_WRAP = false
    const val SYNTAX_HIGHLIGHT = true
    const val TEXT_SIZE_PX = 0f
    const val MINIMAP_SHOWN = false
    const val MINIMAP_BLOCKS = false
    const val SYMBOL_BAR = false
    const val LINE_NUMBERS = true
    const val INVISIBLE_CHARS = false
    const val SELECTED_THEME = "vscode-dark-2026"
    const val SELECTED_FONT = "fonts/JetBrainsMono-variable.ttf"
}

sealed interface LoadState {
    data object Loading : LoadState
    data object Success : LoadState
    data class Error(
        val message: String,
    ) : LoadState
}

class SoraEditorViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private var pendingLargeFile: Path? = null
    private val confirmedLargeFiles = mutableSetOf<Path>()
    private var grammarLoadJob: Job? = null
    private val prefs get() = getApplication<Application>().editorPrefs
    private var pendingSelectionLeft: Int = -1
    private var pendingSelectionRight: Int = -1
    private var pendingScrollX: Int = 0
    private var pendingScrollY: Int = 0
    var largeFilePending: Boolean by mutableStateOf(false)

    var loadState: LoadState by mutableStateOf(LoadState.Loading)
        private set
    var savedPath: Path? by mutableStateOf(null)
        private set
    var openedName: String? by mutableStateOf(null)
        private set
    var isNewFile: Boolean by mutableStateOf(false)
        private set
    var grammarsReady: Boolean by mutableStateOf(false)
        private set
    var isModified: Boolean by mutableStateOf(false)
        private set
    var syntaxHighlight: Boolean by mutableStateOf(EditorDefaults.SYNTAX_HIGHLIGHT)
        private set
    var wordWrap: Boolean by mutableStateOf(EditorDefaults.WORD_WRAP)
        private set
    var miniMap: Boolean by mutableStateOf(EditorDefaults.MINIMAP_SHOWN)
        private set
    var miniMapBlocks: Boolean by mutableStateOf(EditorDefaults.MINIMAP_BLOCKS)
        private set
    var symbolBar: Boolean by mutableStateOf(EditorDefaults.SYMBOL_BAR)
        private set
    var lineNumbers: Boolean by mutableStateOf(EditorDefaults.LINE_NUMBERS)
        private set
    var selectedTheme: String by mutableStateOf(EditorDefaults.SELECTED_THEME)
        private set
    var selectedFont: String by mutableStateOf(EditorDefaults.SELECTED_FONT)
        private set
    var prefsLoaded: Boolean by mutableStateOf(false)
        private set
    var textSizePx: Float by mutableFloatStateOf(EditorDefaults.TEXT_SIZE_PX)
        private set
    var fontOptions: List<FontRegistry.FontOption> by mutableStateOf(emptyList())
        private set
    var systemFontOptions: List<FontRegistry.FontOption> by mutableStateOf(emptyList())
        private set
    var systemFontsLoaded: Boolean by mutableStateOf(false)
        private set
    var invisibleChars: Boolean by mutableStateOf(EditorDefaults.INVISIBLE_CHARS)
        private set
    var content: Content by mutableStateOf(Content())
        private set
    var newFileDir: Path? by mutableStateOf(null)
        private set

    fun initializeNewFile(directory: Path? = null) {
        viewModelScope.launch {
            if (prefsLoaded) return@launch
            val app = getApplication<Application>()
            TextEditorInitializer.initThemeAndPrefs(app)
            loadPrefs()
            newFileDir = directory
            withContext(Dispatchers.Main) {
                content = Content()
                savedPath = null
                openedName = null
                isNewFile = true
                loadState = LoadState.Success
            }
            refreshFontOptions()
            grammarsLoaded()
        }
    }

    private fun grammarsLoaded() {
        if (grammarsReady) return
        if (grammarLoadJob?.isActive == true) return

        grammarLoadJob = viewModelScope.launch {
            try {
                TextEditorInitializer.initGrammars(getApplication())
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
            leftPos.line,
            leftPos.column,
            rightPos.line,
            rightPos.column,
            false,
        )
        editor.scroller.startScroll(pendingScrollX, pendingScrollY, 0, 0, 0)
        editor.scroller.abortAnimation()
        editor.postInvalidate()
    }

    private fun loadPrefs() {
        val prefs = this.prefs
        wordWrap = prefs.getBoolean(PrefKeys.WORD_WRAP, EditorDefaults.WORD_WRAP)
        syntaxHighlight =
            prefs.getBoolean(PrefKeys.SYNTAX_HIGHLIGHT, EditorDefaults.SYNTAX_HIGHLIGHT)
        textSizePx = prefs.getFloat(PrefKeys.TEXT_SIZE_PX, EditorDefaults.TEXT_SIZE_PX)
        miniMap = prefs.getBoolean(PrefKeys.MINIMAP_SHOWN, EditorDefaults.MINIMAP_SHOWN)
        miniMapBlocks = prefs.getBoolean(PrefKeys.MINIMAP_BLOCKS, EditorDefaults.MINIMAP_BLOCKS)
        symbolBar = prefs.getBoolean(PrefKeys.SYMBOL_BAR, EditorDefaults.SYMBOL_BAR)
        lineNumbers = prefs.getBoolean(PrefKeys.LINE_NUMBERS, EditorDefaults.LINE_NUMBERS)
        invisibleChars = prefs.getBoolean(PrefKeys.INVISIBLE_CHARS, EditorDefaults.INVISIBLE_CHARS)

        val app = getApplication<Application>()

        selectedTheme = resolveSelectedId(
            stored = prefs.getString(PrefKeys.SELECTED_THEME, null) ?: "",
            availableIds = ThemeRegistry.availableThemes.map { it.id },
            defaultName = EditorDefaults.SELECTED_THEME,
        )

        selectedFont = resolveSelectedId(
            stored = prefs.getString(PrefKeys.SELECTED_FONT, null) ?: "",
            availableIds = FontRegistry.availableFonts(app, includeSystemFonts = true)
                .map { it.id },
            defaultName = EditorDefaults.SELECTED_FONT,
        )

        prefsLoaded = true
        savePrefs()
    }

    private fun savePrefs() {
        prefs.edit {
            putFloat(PrefKeys.TEXT_SIZE_PX, textSizePx)
            putBoolean(PrefKeys.WORD_WRAP, wordWrap)
            putBoolean(PrefKeys.SYNTAX_HIGHLIGHT, syntaxHighlight)
            putString(PrefKeys.SELECTED_THEME, selectedTheme)
            putBoolean(PrefKeys.MINIMAP_SHOWN, miniMap)
            putBoolean(PrefKeys.MINIMAP_BLOCKS, miniMapBlocks)
            putBoolean(PrefKeys.SYMBOL_BAR, symbolBar)
            putBoolean(PrefKeys.LINE_NUMBERS, lineNumbers)
            putString(PrefKeys.SELECTED_FONT, selectedFont)
            putBoolean(PrefKeys.INVISIBLE_CHARS, invisibleChars)
        }
    }

    fun refreshFontOptions() {
        viewModelScope.launch(Dispatchers.IO) {
            fontOptions = FontRegistry.availableFonts(getApplication(), includeSystemFonts = false)
        }
    }

    fun loadSystemFonts() {
        if (systemFontsLoaded) return
        viewModelScope.launch(Dispatchers.IO) {
            systemFontOptions =
                FontRegistry.availableFonts(getApplication(), includeSystemFonts = true)
                    .filter { it !in fontOptions }
            systemFontsLoaded = true
        }
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

    fun toggleWordWrap() {
        wordWrap = !wordWrap
        savePrefs()
    }

    fun toggleSyntaxHighlight() {
        syntaxHighlight = !syntaxHighlight
        savePrefs()
        if (syntaxHighlight) grammarsLoaded()
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

    fun load(path: Path) {
        loadState = LoadState.Loading
        isModified = false
        viewModelScope.launch(Dispatchers.IO) {
            val size = try {
                if (Files.isRegularFile(path)) Files.size(path) else -1L
            } catch (e: IOException) {
                -1L
            }
            if (size > LARGE_FILE_WARNING_BYTES && path !in confirmedLargeFiles) {
                pendingLargeFile = path
                withContext(Dispatchers.Main) {
                    largeFilePending = true
                }
                return@launch
            }
            doLoad(path)
        }
    }

    private fun doLoad(path: Path) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val builtContent = Files.newInputStream(path).use { stream ->
                    ContentIO.createFrom(stream, Charsets.UTF_8)
                }
                withContext(Dispatchers.Main) {
                    content = builtContent
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

    fun dismissLargeFileWarning() {
        val path = pendingLargeFile
        pendingLargeFile = null
        largeFilePending = false
        if (path != null) {
            confirmedLargeFiles.add(path)
            doLoad(path)
        }
    }

    fun onContentChanged(editorAction: Int) {
        isModified = editorAction != ContentChangeEvent.ACTION_SET_NEW_TEXT
    }

    fun openDocument(
        path: Path,
        name: String,
    ) {
        viewModelScope.launch {
            if (prefsLoaded) return@launch
            val app = getApplication<Application>()
            TextEditorInitializer.initThemeAndPrefs(app)
            loadPrefs()
            savedPath = path
            openedName = name
            isNewFile = false
            load(path)
            refreshFontOptions()
            grammarsLoaded()
        }
    }

    fun reloadOpen() {
        savedPath?.let { load(it) }
    }

    fun saveExisting(
        getText: () -> String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val path = savedPath ?: run {
            onError("No document open")
            return
        }
        viewModelScope.launch {
            try {
                val text = getText()
                withContext(Dispatchers.IO) {
                    Files.newBufferedWriter(path, Charsets.UTF_8).use { it.write(text) }
                }
                isModified = false
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Write error")
            }
        }
    }

    fun save(
        path: Path,
        getText: () -> String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val text = getText()
                withContext(Dispatchers.IO) {
                    Files.newBufferedWriter(path, Charsets.UTF_8).use { it.write(text) }
                }
                isModified = false
                onSuccess()
            } catch (e: IOException) {
                onError(e.localizedMessage ?: "Write error")
            }
        }
    }

    fun saveNewFile(
        fileName: String,
        getText: () -> String,
        onSaved: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val directory = newFileDir
        if (directory == null) {
            onError("No directory to save in")
            return
        }
        viewModelScope.launch {
            try {
                val text = getText()
                val file = directory.resolve(fileName)
                withContext(Dispatchers.IO) {
                    Files.newBufferedWriter(file, Charsets.UTF_8).use { it.write(text) }
                }
                withContext(Dispatchers.Main) {
                    savedPath = file
                    openedName = fileName
                    isNewFile = false
                }
                isModified = false
                onSaved()
            } catch (e: IOException) {
                onError(e.localizedMessage ?: "Write error")
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Write error")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                SoraEditorViewModel(app)
            }
        }
    }
}

fun deriveScrollbarDrawables(): Pair<ColorDrawable, ColorDrawable> {
    val trackDrawable = 0x29FFFFFF.toDrawable()
    val thumbDrawable = 0x80FFFFFF.toInt().toDrawable()
    return Pair(trackDrawable, thumbDrawable)
}
