package texteditor.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults.contentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import texteditor.OpenDocument
import texteditor.data.PRELOAD_BG_DARK
import texteditor.fileNameOf
import texteditor.parentOf
import ui.SimpleShadow

@Composable
fun EditorDrawerSheet(
    documents: List<OpenDocument>,
    pinnedPaths: List<String>,
    activeDocumentId: Long,
    onSelect: (Long) -> Unit,
    onOpenPath: (String) -> Unit,
    onNewFile: () -> Unit,
    onSaveAll: () -> Unit,
    onClose: (Long) -> Unit,
    onMinimize: () -> Unit,
    onCloseDocuments: (List<Long>) -> Unit = {},
    width: Dp = DrawerDefaults.MaximumDrawerWidth,
) {
    var closeMenuExpanded by remember { mutableStateOf(false) }
    var fileMenuExpanded by remember { mutableStateOf(false) }
    val itemIconTint = contentColor.copy(alpha = 0.5f)
    val openByPath = documents.mapNotNull { document ->
        document.path?.let { it to document }
    }.toMap()
    val pinnedPathSet = pinnedPaths.toSet()
    val openUnpinned = documents.filter {
        it.path == null || it.path !in pinnedPathSet
    }
    val displayedDocuments = buildList {
        pinnedPaths.forEach { path ->
            openByPath[path]?.let { document -> add(document) }
        }
        addAll(openUnpinned)
    }
    val activeIndex = displayedDocuments.indexOfFirst { it.id == activeDocumentId }

    ModalDrawerSheet(
        modifier = Modifier.requiredWidth(width),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Start
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Text editor",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp),
            )
            Box {
                IconButton(onClick = { closeMenuExpanded = true }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close options")
                }
                DropdownMenu(
                    expanded = closeMenuExpanded,
                    onDismissRequest = { closeMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Close others") },
                        onClick = {
                            closeMenuExpanded = false
                            onCloseDocuments(
                                displayedDocuments
                                    .map { it.id }
                                    .filter { it != activeDocumentId }
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Close all") },
                        onClick = {
                            closeMenuExpanded = false
                            onCloseDocuments(displayedDocuments.map { it.id })
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Close unmodified") },
                        onClick = {
                            closeMenuExpanded = false
                            onCloseDocuments(
                                displayedDocuments
                                    .filter { !it.isModified }
                                    .map { it.id }
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Close above") },
                        onClick = {
                            closeMenuExpanded = false
                            if (activeIndex > 0) {
                                onCloseDocuments(
                                    displayedDocuments.take(activeIndex).map { it.id }
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Close below") },
                        onClick = {
                            closeMenuExpanded = false
                            if (activeIndex >= 0) {
                                onCloseDocuments(
                                    displayedDocuments.drop(activeIndex + 1).map { it.id }
                                )
                            }
                        },
                    )
                }
            }
            Box {
                IconButton(onClick = { fileMenuExpanded = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "File options")
                }
                DropdownMenu(
                    expanded = fileMenuExpanded,
                    onDismissRequest = { fileMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = null,
                                tint = itemIconTint,
                            )
                        },
                        text = { Text("Save all") },
                        onClick = {
                            fileMenuExpanded = false
                            onSaveAll()
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = itemIconTint,
                            )
                        },
                        text = { Text("New file") },
                        onClick = {
                            fileMenuExpanded = false
                            onNewFile()
                        },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(PRELOAD_BG_DARK))
            ) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (pinnedPaths.isEmpty() && openUnpinned.isEmpty()) {
                        item {
                            Text(
                                text = "No files open",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (pinnedPaths.isNotEmpty()) {
                        item {
                            SectionLabel("Pinned")
                        }
                        items(pinnedPaths, key = { "pinned:$it" }) { path ->
                            val open = openByPath[path]
                            DocumentRow(
                                document = open ?: OpenDocument(
                                    id = 0L,
                                    path = path,
                                    name = fileNameOf(path),
                                    directory = parentOf(path),
                                    isModified = false,
                                ),
                                selected = open != null && open.id == activeDocumentId,
                                onSelect = {
                                    if (open != null) onSelect(open.id) else onOpenPath(path)
                                },
                                onClose = open?.let { document -> { onClose(document.id) } },
                            )
                        }
                        if (openUnpinned.isNotEmpty()) {
                            item {
                                SectionLabel("Open")
                            }
                        }
                    }
                    items(openUnpinned, key = { it.id }) { document ->
                        DocumentRow(
                            document = document,
                            selected = document.id == activeDocumentId,
                            onSelect = { onSelect(document.id) },
                            onClose = { onClose(document.id) },
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 8.dp, end = 8.dp),
                ) {
                    Button(
                        onClick = onMinimize,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.inverseSurface
                        )
                    ) {
                        Text("MINIMIZE")
                    }
                }
            }
            SimpleShadow()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun DocumentRow(
    document: OpenDocument,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val name = document.name ?: document.path?.let(::fileNameOf) ?: "New file"
    val location = document.path?.let(::parentOf) ?: document.directory ?: "Not saved yet"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onSelect)
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.padding(end = 10.dp),
            imageVector = Icons.Filled.Edit,
            contentDescription = null
        )
        Column(modifier = Modifier.weight(1f)) {
            val thisText = if (document.isModified) "*$name" else name
            Text(
                text = thisText,
                fontWeight = FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close $name")
            }
        }
    }
}
