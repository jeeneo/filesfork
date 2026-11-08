package texteditor.ui.dialogs.encoding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ReopenEncodingDialog(
    documentName: String,
    onReopen: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reopen with encoding?") },
        text = { Text("$documentName has unsaved changes. They will be lost.") },
        confirmButton = {
            TextButton(onClick = onReopen) { Text("Reopen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
