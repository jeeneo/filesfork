package texteditor.ui.dialogs.appearance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import texteditor.data.registry.ThemeRegistry as EditorThemeRegistry

@Composable
fun ThemeChoiceDialog(
    selected: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val themes = EditorThemeRegistry.availableThemes
    SingleChoiceDialog(
        title = "Theme",
        options = themes,
        selected = themes.find { it.id == selected } ?: themes.firstOrNull(),
        label = { it?.displayName ?: "" },
        onSelect = { onThemeSelected(it?.id ?: "") },
        onDismiss = onDismiss,
    )
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            LazyColumn {
                items(options) { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = {
                            onSelect(option)
                            onDismiss()
                        })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label(option),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = onDismiss) { Text("Cancel") }
    })
}
