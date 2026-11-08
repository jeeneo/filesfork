package texteditor.ui.dialogs.reload

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ReloadFileDialog(
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reload file?") },
        text = { Text("Unsaved changes will be lost.") },
        confirmButton = {
            TextButton(onClick = onReload) { Text("Reload") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
