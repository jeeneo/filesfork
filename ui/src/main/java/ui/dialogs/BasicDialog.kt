package ui.dialogs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import ui.theme.AppTheme

data class DialogAction(
    val text: String,
    val onClick: (String) -> Unit,
    val enabled: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    usePlatformDefaults: Boolean = true,
    useContentPadding: Boolean = true,
    resizeForIme: Boolean = false,
    searchIcon: ImageVector? = null,
    helpIcon: ImageVector? = null,
    topTrailingComposable: (@Composable () -> Unit)? = null,
    leadingAction: DialogAction? = null,
    trailingActions: List<DialogAction> = emptyList(),
    content: @Composable RowScope.() -> Unit = {}
) {
    BasicAlertDialog(
        modifier = modifier, onDismissRequest = onDismissRequest, properties = DialogProperties(
            usePlatformDefaultWidth = usePlatformDefaults,
            decorFitsSystemWindows = !resizeForIme,
        )
    ) {
        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = AlertDialogDefaults.shape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.wrapContentHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Pin the row to one height whether the title or the search slot is
                        // shown, so toggling search cannot push the content below it down.
                        .then(
                            if (topTrailingComposable != null) Modifier.heightIn(min = 48.dp)
                            else Modifier
                        )
                        .padding(horizontal = 21.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                        fontSize = 20.sp,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (topTrailingComposable != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(max = 48.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            topTrailingComposable()
                        }
                    }
                    if (topTrailingComposable == null) {
                        Spacer(Modifier.weight(1f))
                        if (searchIcon != null) {
                            Icon(
                                imageVector = searchIcon,
                                contentDescription = "Search",
                                tint = LocalContentColor.current.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    if (helpIcon != null) {
                        Spacer(Modifier.width(20.dp))
                        Icon(
                            imageVector = helpIcon,
                            contentDescription = "Help",
                            tint = LocalContentColor.current.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                ) {
                    if (useContentPadding) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 21.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            content()
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            content()
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    leadingAction?.let { ActionButton(it) }
                    Spacer(Modifier.weight(1f))
                    trailingActions.forEach { ActionButton(it) }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(action: DialogAction) {
    TextButton(onClick = { action.onClick("") }, enabled = action.enabled) {
        Text(action.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@Composable
fun TextInputDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialValue: String = "",
    initialSelection: TextRange? = null,
    placeholder: String? = null,
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    leadingAction: DialogAction? = null,
    trailingActions: List<DialogAction>,
) {
    var internalText by rememberSaveable { mutableStateOf(initialValue) }
    val text = value ?: internalText
    val focusRequester = remember { FocusRequester() }

    fun updateText(newText: String) {
        if (value == null) {
            internalText = newText
        }
        onValueChange?.invoke(newText)
    }

    BaseDialog(
        title = title,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        usePlatformDefaults = false,
        onDismissRequest = onDismissRequest,
        leadingAction = leadingAction,
        trailingActions = trailingActions.map { action ->
            action.copy(onClick = { if (text.isNotEmpty()) action.onClick(text.trim()) })
        },
    ) {
        DialogTextField(
            value = text,
            onValueChange = ::updateText,
            placeholder = placeholder,
            focusRequester = focusRequester,
            initialSelection = initialSelection,
        )
    }
}

@Composable
fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    autoFocus: Boolean = true,
    initialSelection: TextRange? = null,
    placeholder: String? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val length = value.length
        val selection = initialSelection?.let {
            TextRange(
                it.start.coerceIn(0, length), it.end.coerceIn(0, length)
            )
        } ?: TextRange(length)
        mutableStateOf(TextFieldValue(text = value, selection = selection))
    }
    LaunchedEffect(value) {
        if (field.text != value) {
            field = field.copy(text = value, selection = TextRange(value.length))
        }
    }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    BasicTextField(
        value = field,
        onValueChange = { updated ->
            field = updated
            onValueChange(updated.text)
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = field.text,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = placeholder?.let {
                    {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = LocalContentColor.current.copy(alpha = 0.5f),
                        )
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                ),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            )
        },
    )
}

@Preview(
    showBackground = true,
    showSystemUi = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)

@Composable
private fun CreateItemDialogPreview() {
    AppTheme {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                TextInputDialog(
                    title = "title",
                    placeholder = "placeholder",
                    onDismissRequest = {},
                    leadingAction = DialogAction(text = "leading", onClick = {}),
                    trailingActions = listOf(
                        DialogAction(text = "trailing-1", onClick = {}),
                        DialogAction(text = "trailing-2", onClick = {}),
                    ),
                )
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
fun TsPreview() {
    AppTheme {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                BaseDialog(
                    title = "dialog",
                    onDismissRequest = {},
                    leadingAction = DialogAction(text = "START", onClick = {}),
                    trailingActions = listOf(
                        DialogAction(text = "FIRST-END", onClick = {}),
                        DialogAction(text = "SECOND-END", onClick = {}),
                    )
                )
            }
        }
    }
}
