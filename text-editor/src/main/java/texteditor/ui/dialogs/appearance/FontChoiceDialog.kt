package texteditor.ui.dialogs.appearance

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import texteditor.data.registry.FontRegistry
import ui.dialogs.BaseDialog
import ui.dialogs.DialogAction
import ui.dialogs.DialogTextField

@Composable
fun FontChoiceDialog(
    fontOptions: List<FontRegistry.FontOption>,
    systemFontOptions: List<FontRegistry.FontOption>,
    onShowSystemFonts: () -> Unit,
    selected: String,
    onSelect: (String) -> Unit,
    onFontImported: (FontRegistry.FontOption) -> Unit,
    onFontDeleted: (FontRegistry.FontOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var fontPendingDelete by remember { mutableStateOf<FontRegistry.FontOption?>(null) }
    var showSystemFonts by rememberSaveable {
        mutableStateOf(FontRegistry.isSystemFont(selected))
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        } ?: uri.lastPathSegment ?: "font"
        val imported = FontRegistry.importFont(context, uri, fileName)
        if (imported != null) {
            importError = null
            onFontImported(imported)
        } else {
            importError = "Couldn't import \"$fileName\", not a valid font file"
        }
    }

    val filteredOptions = remember(fontOptions, systemFontOptions, showSystemFonts, query) {
        val options = if (showSystemFonts) fontOptions + systemFontOptions else fontOptions
        val q = query.trim()
        if (q.isEmpty()) {
            options
        } else {
            options.filter {
                it.displayName.contains(q, ignoreCase = true)
            }
        }
    }

    val listState = rememberLazyListState()
    var scrolledToSelected by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (showSystemFonts) onShowSystemFonts()
    }
    LaunchedEffect(filteredOptions, selected) {
        if (scrolledToSelected) return@LaunchedEffect
        val index = filteredOptions.indexOfFirst { it.id == selected }
        if (index >= 0) {
            listState.scrollToItem(index)
            scrolledToSelected = true
        }
    }

    fontPendingDelete?.let { font ->
        AlertDialog(
            onDismissRequest = { fontPendingDelete = null },
            title = { Text("Delete font?") },
            text = { Text("Remove \"${font.displayName}\" from imported fonts?") },
            confirmButton = {
                TextButton(onClick = {
                    onFontDeleted(font)
                    fontPendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { fontPendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    BaseDialog(
        title = "Font",
        modifier = Modifier
            .fillMaxWidth()
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
                            placeholder = "Search fonts",
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                TextButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "*/*"),
                        )
                    },
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Import font")
                }
                Spacer(Modifier.weight(1f))
                Checkbox(
                    checked = showSystemFonts,
                    onCheckedChange = {
                        showSystemFonts = it
                        if (it) onShowSystemFonts()
                    },
                )
                Spacer(Modifier.width(4.dp))
                Text("System fonts")
            }
            if (importError != null) {
                Text(
                    text = importError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            if (filteredOptions.isEmpty()) {
                Text(
                    text = if (query.isBlank()) "No fonts available" else "No fonts match \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(filteredOptions, key = { it.id }) { option ->
                        val typeface = remember(option.id) {
                            FontRegistry.loadTypeface(context, option.id)
                        }
                        val isImported = remember(option.id) {
                            FontRegistry.isImported(context, option.id)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        onSelect(option.id)
                                        onDismiss()
                                    },
                                    onLongClick = if (isImported) {
                                        { fontPendingDelete = option }
                                    } else {
                                        null
                                    },
                                )
                                .padding(horizontal = 28.dp, vertical = 12.dp),
                        ) {
                            RadioButton(selected = option.id == selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = option.displayName,
                                fontFamily = FontFamily(typeface),
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
