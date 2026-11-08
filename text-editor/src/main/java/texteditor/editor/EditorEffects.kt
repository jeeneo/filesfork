package texteditor.editor

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import texteditor.LoadState
import texteditor.SoraEditorViewModel

@Composable
internal fun EditorEffects(
    viewModel: SoraEditorViewModel,
    editorRef: CodeEditor?,
    settings: EditorSettings,
    typeface: Typeface,
    activeLanguage: TextMateLanguage?,
    onReloaded: () -> Unit,
) {
    LaunchedEffect(editorRef, settings.theme) {
        editorRef?.let { applyTheme(it, settings.theme) }
    }
    LaunchedEffect(editorRef, settings, typeface) {
        editorRef?.applySettings(settings, typeface)
    }
    LaunchedEffect(editorRef, activeLanguage) {
        val editor = editorRef ?: return@LaunchedEffect
        if (editor.editorLanguage !== activeLanguage) {
            editor.setEditorLanguage(activeLanguage)
        }
    }
    LaunchedEffect(editorRef, viewModel.grammarEpoch) {
        if (viewModel.grammarEpoch == 0) return@LaunchedEffect
        val language = editorRef?.editorLanguage as? TextMateLanguage ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { language.reloadGrammar() }
    }
    LaunchedEffect(viewModel.reloadEpoch) {
        if (viewModel.reloadEpoch == 0) return@LaunchedEffect
        val editor = editorRef ?: return@LaunchedEffect
        if (viewModel.loadState !is LoadState.Success) return@LaunchedEffect
        editor.setText(viewModel.content)
        onReloaded()
    }
    DisposableEffect(editorRef) {
        val editor = editorRef ?: return@DisposableEffect onDispose { }
        if (viewModel.textSizePx > 0f) editor.setTextSizePx(viewModel.textSizePx)
        val receipt = editor.subscribeEvent(ScrollEvent::class.java) { _, _ ->
            viewModel.saveTextSize(editor.textSizePx)
        }
        onDispose {
            receipt.unsubscribe()
            viewModel.saveTextSize(editor.textSizePx)
        }
    }
}
