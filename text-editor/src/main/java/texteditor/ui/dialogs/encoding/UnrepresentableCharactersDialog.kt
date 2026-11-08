package texteditor.ui.dialogs.encoding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun UnrepresentableCharactersDialog(
    encodingLabel: String,
    onSaveAsUtf8: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cannot save file") },
        text = { Text("Some characters cannot be saved as $encodingLabel.") },
        confirmButton = {
            TextButton(onClick = onSaveAsUtf8) { Text("Save as UTF-8") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
