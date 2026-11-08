package texteditor.ui.dialogs.encoding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import texteditor.data.encoding.EncodingCatalog
import texteditor.data.encoding.EncodingScheme
import ui.dialogs.BaseDialog
import ui.dialogs.DialogAction
import ui.dialogs.DialogTextField

private sealed interface PickerRow {
    data class Header(val title: String) : PickerRow
    data class Entry(val scheme: EncodingScheme) : PickerRow
}

@Composable
fun EncodingPickerDialog(
    currentId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val title = "Encoding"
    val rows = remember(query) {
        buildList {
            EncodingCatalog.grouped().forEach { (group, schemes) ->
                val visible = schemes.filter { it.matches(query) }
                if (visible.isNotEmpty()) {
                    add(PickerRow.Header(group))
                    visible.forEach { add(PickerRow.Entry(it)) }
                }
            }
        }
    }

    BaseDialog(
        title = title,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        usePlatformDefaults = false,
        useContentPadding = false,
        resizeForIme = true,
        topTrailingComposable = {
            if (searchOpen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        DialogTextField(
                            value = query,
                            onValueChange = { query = it },
                        )
                    }
                    IconButton(onClick = { query = ""; searchOpen = false }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close search",
                            tint = LocalContentColor.current.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            } else {
                IconButton(onClick = { query = ""; searchOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = LocalContentColor.current.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        trailingActions = listOf(DialogAction("Cancel", onClick = { onDismiss() })),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (rows.isEmpty()) {
                Text(
                    text = "No matches for \"$query\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(rows) { row ->
                        when (row) {
                            is PickerRow.Header -> Text(
                                text = row.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top = 12.dp,
                                    bottom = 4.dp,
                                ),
                            )

                            is PickerRow.Entry -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(row.scheme.id) }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                            ) {
                                RadioButton(
                                    selected = row.scheme.id == currentId,
                                    onClick = null,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = row.scheme.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
