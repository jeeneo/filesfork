package texteditor.ui.bars.topbar

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults.contentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import texteditor.SoraEditorViewModel
import texteditor.data.encoding.EncodingPickerMode

@Composable
fun ActionsSubmenu(
    viewModel: SoraEditorViewModel?,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSaveAs: () -> Unit,
    onFileInfo: () -> Unit,
    onReload: () -> Unit
) {
    val itemTextColor = contentColor.copy(alpha = 0.8f)
    val itemIconTint = contentColor.copy(alpha = 0.5f)

    DropdownMenu(
        expanded = expanded,
        containerColor = MaterialTheme.colorScheme.onSecondary,
        properties = PopupProperties(
            focusable = false
        ),
        onDismissRequest = onDismiss
    ) {
        Text(
            modifier = Modifier.padding(
                start = 12.dp, top = 8.dp, bottom = 8.dp
            ),
            color = itemTextColor,
            text = "File",
            style = MaterialTheme.typography.labelMedium,
        )
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "New file", color = itemTextColor
            )
        }, onClick = {
            onDismiss()
            viewModel?.openNewFile()
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = { Text("Save as", color = itemTextColor) }, onClick = {
            onDismiss()
            onSaveAs()
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = { Text("Reload", color = itemTextColor) }, onClick = {
            onDismiss()
            onReload()
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.RotateRight,
                modifier = Modifier.rotate(45f),
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Reopen with encoding",
                color = itemTextColor,
            )
        }, onClick = {
            onDismiss()
            viewModel?.openEncodingPicker(EncodingPickerMode.REOPEN)
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.Filled.FormatSize,
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Save with encoding",
                color = itemTextColor,
            )
        }, onClick = {
            onDismiss()
            viewModel?.openEncodingPicker(EncodingPickerMode.SAVE)
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Line break",
                color = itemTextColor,
            )
        }, onClick = {
            onDismiss()
            viewModel?.openLineEndingPicker()
        })
        DropdownMenuItem(leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = itemIconTint,
            )
        }, text = {
            Text(
                "Statistics",
                color = itemTextColor,
            )
        }, onClick = {
            onDismiss()
            onFileInfo()
        })
    }
}
