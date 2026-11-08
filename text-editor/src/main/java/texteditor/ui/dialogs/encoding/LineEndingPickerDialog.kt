package texteditor.ui.dialogs.encoding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import texteditor.data.encoding.LineEnding
import ui.dialogs.BaseDialog
import ui.dialogs.DialogAction

@Composable
fun LineEndingPickerDialog(
    current: LineEnding,
    mixed: Boolean,
    onSelect: (LineEnding) -> Unit,
    onDismiss: () -> Unit,
) {
    BaseDialog(
        useContentPadding = false,
        title = "Line endings",
        onDismissRequest = onDismiss,
        content = {
            Column(modifier = Modifier.weight(1f)) {
                if (mixed) {
                    Text(
                        text = "This file mixes line ending styles.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
                for (lineEnding in LineEnding.entries) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(lineEnding) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        RadioButton(selected = lineEnding == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(lineEnding.name)
                    }
                }
            }
        },
        trailingActions = listOf(DialogAction("Cancel", onClick = { onDismiss() })),
    )
}
