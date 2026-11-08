package texteditor.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.runtime.Immutable
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.graphics.inlayHint.ColorInlayHintRenderer
import io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import io.github.rosemoe.sora.lang.styling.line.LineBackground
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.ext.EditorSpanInteractionHandler
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.minimap.MinimapConfig
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import texteditor.buildColorScheme
import texteditor.data.registry.ThemeRegistry
import texteditor.deriveScrollbarDrawables

private const val EMBEDDED_SCAN_LINES = 40
private const val EMBEDDED_SCAN_CHARS = 200_000
private const val BOTTOM_SCROLL_EXTRA_DP = 94f
private val CURRENT_LINE_TINT = "#323232".toColorInt()

@Immutable
internal data class EditorSettings(
    val theme: String,
    val wordWrap: Boolean,
    val lineNumbers: Boolean,
    val miniMap: Boolean,
    val miniMapBlocks: Boolean,
    val invisibleChars: Boolean,
    val readOnly: Boolean,
    val autoCompletion: Boolean,
    val textSizePx: Float,
)

internal fun CodeEditor.applySettings(settings: EditorSettings, typeface: Typeface) {
    if (isWordwrap != settings.wordWrap) {
        isWordwrap = settings.wordWrap
    }
    if (isLineNumberEnabled != settings.lineNumbers) {
        isLineNumberEnabled = settings.lineNumbers
    }
    if (props.showMinimap != settings.miniMap || props.minimapConfig.minimapDrawTextAsBlocks != settings.miniMapBlocks) {
        applyMinimap(this, settings.miniMap, settings.miniMapBlocks)
    }
    applyInvisibleChars(this, settings.invisibleChars)
    if (isEditable != !settings.readOnly) {
        setEditable(!settings.readOnly)
    }
    val completion = getComponent<EditorAutoCompletion>()
    if (completion.isEnabled != settings.autoCompletion) {
        completion.setEnabled(settings.autoCompletion)
    }
    if (settings.textSizePx > 0f && textSizePx != settings.textSizePx) {
        setTextSizePx(settings.textSizePx)
    }
    typefaceText = typeface
    typefaceLineNumber = typeface
    invalidate()
}

@SuppressLint("ClickableViewAccessibility")
internal fun createCodeEditor(
    context: Context,
    settings: EditorSettings,
    typeface: Typeface,
    language: TextMateLanguage?,
    initialText: Content,
    onFirstLayout: () -> Unit,
    onContentChange: (CodeEditor, ContentChangeEvent) -> Unit,
): CodeEditor {
    val editor = CodeEditor(context)
    applyTheme(editor, settings.theme)
    editor.applySettings(settings, typeface)
    editor.setText(initialText)
    editor.setEditorLanguage(language)
    editor.subscribeEvent(LayoutStateChangeEvent::class.java) { event, _ ->
        if (!event.isLayoutBusy) onFirstLayout()
    }
    installCurrentLineHighlight(editor)
    registerInlayHints(editor)
    editor.setLineSpacing(2f, 1.1f)
    editor.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    installBottomScrollPadding(editor)
    editor.searcher.replaceOptions = EditorSearcher.ReplaceOptions(true)
    EditorSpanInteractionHandler(editor)
    editor.getComponent<EditorAutoCompletion>().apply {
        setEnabledAnimation(true)
        setEnabled(settings.autoCompletion)
    }
    editor.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
        onContentChange(editor, event)
    }
    editor.setOnTouchListener(
        SelectionListener(editor) {
            ViewCompat.getRootWindowInsets(editor)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        })
    return editor
}

internal fun applyTheme(
    editor: CodeEditor,
    themeName: String,
) {
    ThemeRegistry.setTheme(themeName)
    val scheme = editor.colorScheme as? TextMateColorScheme ?: buildColorScheme().also {
        editor.colorScheme = it
    }
    scheme.setColor(EditorColorScheme.SELECTION_HANDLE, "#e3e3e3".toColorInt())
    scheme.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, "#33e3e3e3".toColorInt())
    scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, "#33e3e3e3".toColorInt())
    scheme.setColor(EditorColorScheme.SELECTION_INSERT, "#33e3e3e3".toColorInt())
    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, "#36383a".toColorInt())
    scheme.setColor(EditorColorScheme.NON_PRINTABLE_CHAR, "#545454".toColorInt())
    scheme.setColor(EditorColorScheme.LINE_NUMBER, "#919191".toColorInt())
    scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, "#919191".toColorInt())
    scheme.setColor(EditorColorScheme.LINE_DIVIDER, "#4a4a4a".toColorInt())
    editor.isHighlightCurrentLine = false
    val dp = editor.dpUnit
    editor.setLineNumberMarginLeft(8f * dp)
    editor.setDividerMargin(8f * dp, 4f * dp)
    val (track, thumb) = deriveScrollbarDrawables()
    editor.renderer.verticalScrollbarTrackDrawable = track
    editor.renderer.verticalScrollbarThumbDrawable = thumb
    editor.renderer.horizontalScrollbarTrackDrawable = track
    editor.renderer.horizontalScrollbarThumbDrawable = thumb
}

internal fun linesToScan(text: Content, event: ContentChangeEvent): List<CharSequence>? {
    val start = event.changeStart.line
    val end = event.changeEnd.line
    return when {
        start <= end && end < text.lineCount && end - start <= EMBEDDED_SCAN_LINES -> (start..end).map {
            text.getLine(
                it
            )
        }

        text.isNotEmpty() -> listOf(text.subSequence(0, minOf(text.length, EMBEDDED_SCAN_CHARS)))

        else -> null
    }
}

private fun applyMinimap(
    editor: CodeEditor,
    showMinimap: Boolean,
    blocks: Boolean,
) {
    editor.props.showMinimap = showMinimap
    editor.props.minimapConfig = MinimapConfig(minimapDrawTextAsBlocks = blocks)
    editor.invalidate()
}

private fun applyInvisibleChars(
    editor: CodeEditor,
    enabled: Boolean,
) {
    val flags = if (enabled) {
        CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR or CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or CodeEditor.FLAG_DRAW_SOFT_WRAP
    } else {
        0
    }
    if (editor.nonPrintablePaintingFlags != flags) {
        editor.nonPrintablePaintingFlags = flags
    }
}

private fun installCurrentLineHighlight(editor: CodeEditor) {
    editor.registerExtraStylesProvider { line, styles ->
        val caret = editor.cursor
        if (!caret.isSelected && line == caret.leftLine) {
            styles.add(LineBackground(line, ConstColor(CURRENT_LINE_TINT)))
        }
    }
}

private fun installBottomScrollPadding(editor: CodeEditor) {
    editor.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        if (view.height > 0) {
            editor.verticalExtraSpaceFactor =
                (BOTTOM_SCROLL_EXTRA_DP * editor.dpUnit / view.height).coerceIn(0f, 1f)
        }
    }
}

private fun registerInlayHints(editor: CodeEditor) {
    editor.registerInlayHintRenderers(
        TextInlayHintRenderer.DefaultInstance,
        ColorInlayHintRenderer.DefaultInstance,
    )
}
