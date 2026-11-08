package texteditor.ui.dialogs.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import texteditor.SoraEditorViewModel
import texteditor.data.encoding.EncodingPickerMode
import texteditor.data.registry.FontRegistry
import texteditor.ui.dialogs.appearance.FontChoiceDialog
import texteditor.ui.dialogs.appearance.ThemeChoiceDialog
import texteditor.ui.dialogs.close.UnsavedChangesDialog
import texteditor.ui.dialogs.encoding.DocumentOptionsDialog
import texteditor.ui.dialogs.encoding.EncodingPickerDialog
import texteditor.ui.dialogs.encoding.LineEndingPickerDialog
import texteditor.ui.dialogs.encoding.ReopenEncodingDialog
import texteditor.ui.dialogs.encoding.UnrepresentableCharactersDialog
import texteditor.ui.dialogs.info.FileInfoDialog
import texteditor.ui.dialogs.jump.JumpToLineDialog
import texteditor.ui.dialogs.language.LanguagePickerDialog
import texteditor.ui.dialogs.reload.ReloadFileDialog

@Composable
fun DialogHost(
    viewModel: SoraEditorViewModel,
    onSaveAndClose: (Long) -> Unit,
    onMinimize: () -> Unit,
    onDocumentClosed: () -> Unit,
    onCloseCancelled: () -> Unit,
    onJumpToLine: (Int) -> Unit,
) {
    val controller = viewModel.dialogs
    val request = controller.requests.lastOrNull() ?: return
    val context = LocalContext.current
    when (request) {
        is DialogRequest.Theme -> ThemeChoiceDialog(
            selected = viewModel.selectedTheme,
            onThemeSelected = viewModel::setTheme,
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.Font -> FontChoiceDialog(
            fontOptions = viewModel.fontOptions,
            systemFontOptions = viewModel.systemFontOptions,
            onShowSystemFonts = viewModel::loadSystemFonts,
            selected = viewModel.selectedFont,
            onSelect = viewModel::setFont,
            onFontImported = { imported ->
                viewModel.refreshFontOptions()
                viewModel.setFont(imported.id)
            },
            onFontDeleted = { deleted ->
                val newFontId =
                    FontRegistry.deleteImportedFont(context, deleted.id, viewModel.selectedFont)
                if (newFontId != viewModel.selectedFont) {
                    viewModel.setFont(newFontId)
                }
                viewModel.refreshFontOptions()
            },
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.Language -> LanguagePickerDialog(
            detectedScope = request.detectedScope,
            selected = viewModel.languageOverride,
            onSelect = viewModel::setLanguage,
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.ConfirmClose -> UnsavedChangesDialog(
            documentName = request.documentName,
            onDiscard = {
                controller.dismissTop()
                viewModel.close(request.documentId)
                onDocumentClosed()
            },
            onSave = {
                controller.dismissTop()
                onSaveAndClose(request.documentId)
            },
            onMinimize = {
                controller.dismissTop()
                onCloseCancelled()
                onMinimize()
            },
            onDismiss = {
                controller.dismissTop()
                onCloseCancelled()
            },
        )

        is DialogRequest.Reload -> ReloadFileDialog(
            onReload = {
                controller.dismissTop()
                viewModel.reloadOpen()
            },
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.FileInfo -> FileInfoDialog(
            info = request,
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.JumpToLine -> JumpToLineDialog(
            request = request,
            onJump = onJumpToLine,
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.EncodingPicker -> EncodingPickerDialog(
            currentId = request.currentId,
            onSelect = { schemeId -> viewModel.applyEncoding(request, schemeId) },
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.LineEndingPicker -> LineEndingPickerDialog(
            current = request.current,
            mixed = request.mixed,
            onSelect = { lineEnding ->
                viewModel.setLineEnding(lineEnding)
                controller.dismissTop()
            },
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.DocumentOptions -> DocumentOptionsDialog(
            encodingLabel = viewModel.encodingLabel,
            lineEndingLabel = viewModel.lineEndingLabel,
            onEncoding = { viewModel.openEncodingPicker(EncodingPickerMode.SAVE) },
            onLineEndings = viewModel::openLineEndingPicker,
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.ConfirmReopenEncoding -> ReopenEncodingDialog(
            documentName = request.documentName,
            onReopen = {
                controller.dismissTop()
                viewModel.reopenWithEncoding(request.schemeId)
            },
            onDismiss = controller::dismissTop,
        )

        is DialogRequest.UnrepresentableCharacters -> UnrepresentableCharactersDialog(
            encodingLabel = request.encodingLabel,
            onSaveAsUtf8 = viewModel::saveAsUtf8,
            onDismiss = {
                viewModel.cancelUnencodableSave()
                onCloseCancelled()
            },
        )
    }
}
