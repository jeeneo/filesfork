package texteditor.ui.bars.topbar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import texteditor.OpenDocument
import texteditor.R
import texteditor.SaveButtonState
import texteditor.SoraEditorViewModel
import texteditor.data.EditorLoadingIndicator
import ui.theme.AppTheme
import kotlin.math.abs

@Composable
fun TopBarActions(
    title: String = "",
    cursorLine: Int = 1,
    cursorColumn: Int = 1,
    lineCount: Int = 0,
    editorRef: CodeEditor? = null,
    viewModel: SoraEditorViewModel? = null,
    saveButtonState: MutableState<SaveButtonState> = remember { mutableStateOf(SaveButtonState.IDLE) },
    onSave: () -> Unit = {},
    onSaveAs: () -> Unit = {},
    onReload: () -> Unit = {},
    onFileInfo: () -> Unit = {},
    onPreview: () -> Unit = {},
    onDocumentOptions: () -> Unit = {},
    canUndo: MutableState<Boolean> = remember { mutableStateOf(false) },
    canRedo: MutableState<Boolean> = remember { mutableStateOf(false) },
    refreshUndoRedo: () -> Unit = {},
    showSearchPanel: Boolean = false,
    onShowSearchPanelChange: (Boolean) -> Unit = {},
    onShowSettingsChange: (Boolean) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onCloseFile: () -> Unit = {},
    onShowLanguagePicker: () -> Unit = {},
    documents: List<OpenDocument> = emptyList(),
    showTabs: Boolean = viewModel?.documentTabs == true,
    activeDocumentId: Long = 0L,
    onSelectDocument: (Long) -> Unit = {},
    onCloseDocument: (Long) -> Unit = {},
    noDocuments: Boolean = false,
) {
    var fileMenuExpanded by remember { mutableStateOf(false) }
    var editMenuExpanded by remember { mutableStateOf(false) }

    val positionHistory = remember(editorRef) { mutableStateListOf<Int>() }
    var positionIndex by remember(editorRef) { mutableIntStateOf(-1) }
    LaunchedEffect(editorRef, cursorLine) {
        val editor = editorRef ?: return@LaunchedEffect
        val offset = editor.cursor.left
        if (offset == positionHistory.getOrNull(positionIndex)) return@LaunchedEffect
        while (positionHistory.size > positionIndex + 1) {
            positionHistory.removeAt(positionHistory.lastIndex)
        }
        positionHistory.add(offset)
        positionIndex = positionHistory.lastIndex
    }
    val isPinned = viewModel?.savedPath?.let { it in viewModel.pinnedPaths } == true
    var selected by remember(editorRef) { mutableIntStateOf(0) }
    DisposableEffect(editorRef) {
        val editor = editorRef ?: return@DisposableEffect onDispose { }
        fun refreshSelection() {
            val cursor = editor.cursor
            selected = abs(cursor.right - cursor.left)
        }
        refreshSelection()
        val receipt = editor.subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
            refreshSelection()
        }
        onDispose { receipt.unsubscribe() }
    }
    val encoding = viewModel?.encodingLabel ?: "???"
    val lineEnding = viewModel?.lineEndingLabel.orEmpty()
    val selectedText = if (selected >= 1) "($selected)  " else ""
    val statusSuffix = if (lineEnding.isEmpty()) encoding else "$encoding  $lineEnding"
    val statusText = if (lineCount > 0) {
        "$cursorLine:$cursorColumn  $selectedText$statusSuffix"
    } else {
        statusSuffix
    }
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Open files",
                    )
                }
                Spacer(Modifier.weight(1f))
                val painter = if (isPinned) {
                    painterResource(R.drawable.icon_unpin)
                } else {
                    painterResource(R.drawable.icon_addpin)
                }
                IconButton(
                    onClick = { viewModel?.togglePin() },
                    enabled = !noDocuments && viewModel?.savedPath != null,
                ) {
                    Icon(
                        painter = painter,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        tint = LocalContentColor.current,
                    )
                }
                IconButton(
                    onClick = {
                        editorRef?.undo()
                        refreshUndoRedo()
                    },
                    enabled = canUndo.value,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(
                    onClick = {
                        editorRef?.redo()
                        refreshUndoRedo()
                    },
                    enabled = canRedo.value,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
                IconButton(
                    onClick = onSave,
                    enabled = !noDocuments && viewModel?.isModified == true && saveButtonState.value == SaveButtonState.IDLE,
                ) {
                    Crossfade(
                        targetState = saveButtonState.value,
                        animationSpec = tween(durationMillis = 300),
                        label = "saveButtonMorph",
                    ) { state ->
                        @Suppress("CascadeIf") if (state == SaveButtonState.IDLE) Icon(
                            Icons.Filled.Save, contentDescription = "Save"
                        )
                        else if (state == SaveButtonState.SAVED) Icon(
                            Icons.Filled.Check,
                            contentDescription = "Saved",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        else if (state == SaveButtonState.ERROR) Icon(
                            Icons.Filled.Error,
                            contentDescription = "Save error",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = {
                    editMenuExpanded = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                    )
                    EditMenu(
                        expanded = editMenuExpanded,
                        onDismiss = { editMenuExpanded = false },
                        editorRef = editorRef,
                        context = context,
                    )
                }

                ActionsMenu(
                    viewModel = viewModel,
                    editorRef = editorRef,
                    cursorLine = cursorLine,
                    lineCount = lineCount,
                    positionHistory = positionHistory,
                    positionIndex = positionIndex,
                    onPositionIndexChange = { positionIndex = it },
                    showSearchPanel = showSearchPanel,
                    onShowSearchPanelChange = onShowSearchPanelChange,
                    onPreview = onPreview,
                    onShowLanguagePicker = onShowLanguagePicker,
                    onShowSettingsChange = onShowSettingsChange,
                    onCloseFile = onCloseFile,
                    isPinned = isPinned,
                    onShowFileMenu = { fileMenuExpanded = true },
                )

                ActionsSubmenu(
                    viewModel = viewModel,
                    expanded = fileMenuExpanded,
                    onDismiss = { fileMenuExpanded = false },
                    onSaveAs = onSaveAs,
                    onReload = onReload,
                    onFileInfo = onFileInfo
                )
            }
            HorizontalDivider(thickness = 0.4.dp, color = DividerDefaults.color.copy(0.8f))

            if (!noDocuments) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (documents.isNotEmpty() && showTabs) {
                        DocumentTabs(
                            documents = documents,
                            activeDocumentId = activeDocumentId,
                            onSelect = onSelectDocument,
                            onClose = onCloseDocument,
                        )
                    }

                    val alpha = if (viewModel?.isModified ?: false) 0.9f else 0.7f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.5.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .combinedClickable(
                                    onClick = onDocumentOptions,
                                    onLongClick = {
                                        context.copyToClipboard(
                                            "filename", title
                                        )
                                    },
                                )
                                .padding(start = 14.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalContentColor.current.copy(alpha = alpha),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .combinedClickable(
                                    onClick = onDocumentOptions,
                                    onLongClick = {
                                        context.copyToClipboard(
                                            "status text", statusText
                                        )
                                    },
                                )
                                .padding(end = 14.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            val settling = !noDocuments && viewModel?.editorLaidOut == false
            if (viewModel?.isLoading == true || viewModel?.isSaving == true || settling) {
                EditorLoadingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            } else if (!noDocuments) {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    @Suppress("UsePropertyAccessSyntax") clipboard.setPrimaryClip(
        ClipData.newPlainText(
            label, text
        )
    )
}

@Preview(
    showBackground = true,
    showSystemUi = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun TsPreview() {
    AppTheme {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                TopBarActions(
                    title = "Main.kt",
                    documents = listOf(
                        OpenDocument(1L, "/src/Main.kt", "Main.kt", null, false),
                        OpenDocument(2L, "/src/Theme.kt", "Theme.kt", null, true),
                        OpenDocument(3L, null, null, null, false),
                    ),
                    showTabs = true,
                    activeDocumentId = 1L,
                )
            }
        }
    }
}
