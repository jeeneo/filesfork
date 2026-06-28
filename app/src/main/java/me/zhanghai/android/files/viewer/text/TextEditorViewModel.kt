package me.zhanghai.android.filesfork.viewer.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import java8.nio.file.Files
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class CodeEditorState(
    val editor: CodeEditor? = null, val initialContent: Content = Content()
) {
    var content by mutableStateOf(initialContent)
}

sealed interface LoadState {
    data object Loading : LoadState
    data object Success : LoadState
    data class Error(val message: String) : LoadState
}

class TextEditorViewModel : ViewModel() {
    val editorState = CodeEditorState()
    var loadState: LoadState by mutableStateOf(LoadState.Loading)
        private set
    var isModified: Boolean by mutableStateOf(false)
        private set
    var syntaxHighlight: Boolean by mutableStateOf(true)
        private set
    var wordWrap: Boolean by mutableStateOf(false)
        private set

    private var originalContent: String = ""
    private var currentPath: Path? = null

    fun load(path: Path) {
        currentPath = path
        loadState = LoadState.Loading
        isModified = false
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    Files.newBufferedReader(path, Charsets.UTF_8).use { it.readText() }
                }
                originalContent = text
                editorState.content = Content(text)
                loadState = LoadState.Success
            } catch (e: OutOfMemoryError) {
                loadState = LoadState.Error(e.localizedMessage ?: "Out of memory")
            } catch (e: IOException) {
                loadState = LoadState.Error(e.localizedMessage ?: "Read error")
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

    fun toggleSyntaxHighlight() {
        syntaxHighlight = !syntaxHighlight
    }

    fun toggleWordWrap() {
        wordWrap = !wordWrap
    }
}
