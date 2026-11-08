package texteditor.ui.dialogs.encoding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.dialogs.BaseDialog
import ui.dialogs.DialogAction

@Composable
fun DocumentOptionsDialog(
    encodingLabel: String,
    lineEndingLabel: String,
    onEncoding: () -> Unit,
    onLineEndings: () -> Unit,
    onDismiss: () -> Unit,
) {
    BaseDialog(
        title = "Document options",
        onDismissRequest = onDismiss,
        useContentPadding = false,
        content = {
            Column {
                OptionRow(
                    title = "Encoding",
                    value = encodingLabel,
                    onClick = onEncoding,
                )
                Spacer(Modifier.height(8.dp))
                OptionRow(
                    title = "Line endings",
                    value = lineEndingLabel,
                    onClick = onLineEndings,
                )
            }
        },
        trailingActions = listOf(
            DialogAction("Cancel", onClick = { onDismiss() })
        )
    )
}

@Composable
private fun OptionRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
