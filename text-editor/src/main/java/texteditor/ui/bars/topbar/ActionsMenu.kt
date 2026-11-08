package texteditor.ui.bars.topbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults.contentColor
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.github.rosemoe.sora.widget.CodeEditor
import texteditor.SoraEditorViewModel
import texteditor.data.AppToasts

@Composable
fun ActionsMenu(
    viewModel: SoraEditorViewModel?,
    editorRef: CodeEditor?,
    cursorLine: Int,
    lineCount: Int,
    positionHistory: List<Int>,
    positionIndex: Int,
    onPositionIndexChange: (Int) -> Unit,
    showSearchPanel: Boolean,
    onShowSearchPanelChange: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onShowLanguagePicker: () -> Unit,
    onShowSettingsChange: (Boolean) -> Unit,
    onCloseFile: () -> Unit,
    isPinned: Boolean,
    onShowFileMenu: () -> Unit,
) {
    val context = LocalContext.current
    var actionsMenuExpanded by remember { mutableStateOf(false) }

    val isReadOnly = viewModel?.readOnly == true
    val wordWrap = viewModel?.wordWrap == true
    val canGoPrevious = positionIndex > 0
    val canGoNext = positionIndex < positionHistory.lastIndex
    fun showPosition(offset: Int) {
        val editor = editorRef ?: return
        val position = editor.text.indexer.getCharPosition(offset.coerceIn(0, editor.text.length))
        editor.setSelection(position.line, position.column, true)
    }

    val actions = listOf(
        TopBarAction(
            label = "File",
            icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
            closeMenu = false,
            trailing = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropUp,
                    contentDescription = null,
                    modifier = Modifier.rotate(90f),
                    tint = contentColor.copy(alpha = 0.5f),
                )
            },
            onClick = onShowFileMenu,
        ),
        TopBarAction(
            label = "Search",
            icon = Icons.Outlined.Search,
            enabled = !isReadOnly,
            onClick = { onShowSearchPanelChange(!showSearchPanel) },
        ),
        TopBarAction(
            label = "Preview",
            icon = Icons.Outlined.Language,
            onClick = onPreview,
        ),
        TopBarAction(
            label = "Syntax",
            icon = Icons.Outlined.Code,
            onClick = onShowLanguagePicker,
        ),
        TopBarAction(
            label = "Previous position",
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            enabled = canGoPrevious,
            onClick = {
                if (canGoPrevious) {
                    val newIndex = positionIndex - 1
                    onPositionIndexChange(newIndex)
                    showPosition(positionHistory[newIndex])
                } else {
                    AppToasts.show(context, "Nowhere to go")
                }
            },
        ),
        TopBarAction(
            label = "Next position",
            icon = Icons.AutoMirrored.Outlined.ArrowForward,
            enabled = canGoNext,
            onClick = {
                if (canGoNext) {
                    val newIndex = positionIndex + 1
                    onPositionIndexChange(newIndex)
                    showPosition(positionHistory[newIndex])
                } else {
                    AppToasts.show(context, "Nowhere to go")
                }
            },
        ),
        TopBarAction(
            label = "Jump to line",
            icon = Icons.AutoMirrored.Outlined.ShortText,
            onClick = { viewModel?.openJumpToLineDialog(cursorLine, lineCount) },
        ),
        TopBarAction(
            label = "Word wrap",
            icon = Icons.AutoMirrored.Outlined.ShortText,
            checked = wordWrap,
            closeMenu = false,
            trailing = { Checkbox(checked = wordWrap, onCheckedChange = null) },
            onClick = { viewModel?.toggleWordWrap() },
        ),
        TopBarAction(
            label = "Read-only mode",
            icon = Icons.Filled.RemoveRedEye,
            checked = isReadOnly,
            closeMenu = false,
            trailing = { Checkbox(checked = isReadOnly, onCheckedChange = null) },
            onClick = { viewModel?.toggleReadOnly() },
        ),
        TopBarAction(
            label = "Settings",
            icon = Icons.Filled.Settings,
            onClick = { onShowSettingsChange(true) },
        ),
        TopBarAction(
            label = "Close file",
            icon = Icons.Outlined.Close,
            onClick = {
                if (isPinned) viewModel?.togglePin()
                onCloseFile()
            },
        ),
    )
    val windowWidth = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val barActionCount = visibleActionCount(windowWidth)
    val barActions = actions.take(barActionCount)
    val overflowActions = actions.drop(barActionCount)
    barActions.forEach { action ->
        TopBarActionView(action = action, inBar = true)
    }
    if (overflowActions.isNotEmpty()) {
        IconButton(onClick = { actionsMenuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More")
            DropdownMenu(
                containerColor = MaterialTheme.colorScheme.onSecondary,
                expanded = actionsMenuExpanded,
                onDismissRequest = { actionsMenuExpanded = false },
                properties = PopupProperties(
                    focusable = false
                ),
            ) {
                overflowActions.forEach { action ->
                    TopBarActionView(
                        action = action,
                        inBar = false,
                        onDismissMenu = { actionsMenuExpanded = false },
                    )
                }
            }
        }
    }
}

private val CompactWidth = 460.dp
private val MediumWidth = 840.dp

private fun visibleActionCount(width: Dp): Int = when {
    width <= CompactWidth -> 0
    width <= MediumWidth -> 2
    else -> 4
}

private data class TopBarAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val checked: Boolean = false,
    val closeMenu: Boolean = true,
    val trailing: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
)

@Composable
private fun TopBarActionView(
    action: TopBarAction,
    inBar: Boolean,
    onDismissMenu: () -> Unit = {},
) {
    val itemTextColor = contentColor.copy(alpha = 0.8f)
    val itemIconTint = contentColor.copy(alpha = 0.5f)
    if (inBar) {
        IconButton(onClick = action.onClick, enabled = action.enabled) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = if (action.checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalContentColor.current
                },
            )
        }
    } else {
        DropdownMenuItem(
            text = { Text(action.label, color = itemTextColor) },
            leadingIcon = {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = itemIconTint,
                )
            },
            trailingIcon = action.trailing,
            enabled = action.enabled,
            onClick = {
                if (action.closeMenu) onDismissMenu()
                action.onClick()
            },
        )
    }
}
