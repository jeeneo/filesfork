package texteditor.ui.dialogs.language

import android.content.res.Configuration
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import texteditor.data.EditorLanguages
import ui.dialogs.BaseDialog
import ui.dialogs.DialogAction
import ui.dialogs.DialogTextField
import ui.theme.AppTheme

@Composable
fun LanguagePickerDialog(
    detectedScope: String?,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val options = remember {
        listOf(null, EditorLanguages.PLAIN_TEXT) + EditorLanguages.scopes
    }

    fun labelOf(scope: String?): String =
        if (scope == null) "Auto (${EditorLanguages.displayName(detectedScope)})"
        else EditorLanguages.displayName(scope)

    val filtered = remember(options, query, detectedScope) {
        val q = query.trim()
        if (q.isEmpty()) options else options.filter { labelOf(it).contains(q, ignoreCase = true) }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = options.indexOf(selected).coerceAtLeast(0)
    )
    BaseDialog(
        title = "Syntax",
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
            if (filtered.isEmpty()) {
                Text(
                    text = "No matches for \"$query\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(filtered) { scope ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(scope)
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            RadioButton(selected = scope == selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = labelOf(scope),
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

@Preview(
    showBackground = true,
    showSystemUi = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun LanguagePickerDialogPreview() {
    AppTheme {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                LanguagePickerDialog(
                    detectedScope = "source.java",
                    selected = null,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
    }
}
