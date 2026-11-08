package texteditor.ui.dialogs.data

import texteditor.data.encoding.EncodingPickerMode
import texteditor.data.encoding.LineEnding

sealed interface DialogRequest {
    data object Theme : DialogRequest

    data object Font : DialogRequest

    data class Language(
        val detectedScope: String?,
    ) : DialogRequest

    data class ConfirmClose(
        val documentId: Long,
        val documentName: String,
    ) : DialogRequest

    data object Reload : DialogRequest

    data class JumpToLine(
        val currentLine: Int,
        val lineCount: Int,
    ) : DialogRequest

    data class FileInfo(
        val byteCount: Long,
        val characterCount: Int,
        val wordCount: Int,
        val lineCount: Int,
        val encodingLabel: String,
        val lineEndingLabel: String,
        val mixedLineEndings: Boolean,
    ) : DialogRequest

    data class EncodingPicker(
        val mode: EncodingPickerMode,
        val currentId: String,
    ) : DialogRequest

    data class LineEndingPicker(
        val current: LineEnding,
        val mixed: Boolean,
    ) : DialogRequest

    data object DocumentOptions : DialogRequest

    data class ConfirmReopenEncoding(
        val schemeId: String,
        val documentName: String,
    ) : DialogRequest

    data class UnrepresentableCharacters(
        val encodingLabel: String,
    ) : DialogRequest
}
