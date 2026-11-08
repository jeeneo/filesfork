package texteditor.ui.dialogs.jump

import androidx.compose.runtime.Composable
import texteditor.ui.dialogs.data.DialogRequest
import ui.dialogs.DialogAction
import ui.dialogs.TextInputDialog

@Composable
fun JumpToLineDialog(
    request: DialogRequest.JumpToLine,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    TextInputDialog(
        title = "Jump to line",
        onDismissRequest = onDismiss,
        placeholder = "${request.currentLine}..${request.lineCount}",
        initialValue = "",
        trailingActions = listOf(
            DialogAction(text = "CANCEL", onClick = { onDismiss() }),
            DialogAction(
                text = "OK",
                onClick = { input ->
                    val line = input.toIntOrNull() ?: return@DialogAction
                    onJump(line)
                    onDismiss()
                },
            ),
        ),
    )
}
