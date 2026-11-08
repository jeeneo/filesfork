package texteditor.ui.bars.bottombar

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import texteditor.R
import ui.theme.AppTheme
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

@Composable
fun CustomSearchBar(
    editor: CodeEditor?,
    initialQuery: String? = null,
    initialShowReplace: Boolean = false,
    onClose: () -> Unit = {},
) {
    var queryValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val initial = initialQuery ?: ""
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    val searchQuery = queryValue.text
    val focusRequester = remember { FocusRequester() }
    var hasOpened by rememberSaveable { mutableStateOf(false) }
    var replaceText by rememberSaveable { mutableStateOf("") }
    var useRegex by rememberSaveable { mutableStateOf(false) }
    var wholeWord by rememberSaveable { mutableStateOf(false) }
    var ignoreCase by rememberSaveable { mutableStateOf(true) }
    var regexError by remember { mutableStateOf<String?>(null) }
    var showReplaceField by rememberSaveable { mutableStateOf(initialShowReplace) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var matchCount by remember { mutableIntStateOf(0) }

    fun performSearch(
        query: String = searchQuery,
        isIgnoreCase: Boolean = ignoreCase,
        isUseRegex: Boolean = useRegex,
        isWholeWord: Boolean = wholeWord,
    ) {
        if (query.isEmpty()) {
            editor?.searcher?.stopSearch()
            regexError = null
            matchCount = 0
            return
        }
        val type = when {
            isUseRegex -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
            isWholeWord -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
            else -> EditorSearcher.SearchOptions.TYPE_NORMAL
        }
        try {
            editor?.searcher?.search(query, EditorSearcher.SearchOptions(type, isIgnoreCase))
            regexError = null
            matchCount = countMatches(
                haystack = editor?.text?.toString() ?: "",
                query = query,
                ignoreCase = isIgnoreCase,
                useRegex = isUseRegex,
                wholeWord = isWholeWord,
            )
        } catch (e: PatternSyntaxException) {
            regexError = e.message
            matchCount = 0
        }
    }

    LaunchedEffect(Unit) {
        if (hasOpened) return@LaunchedEffect
        hasOpened = true
        if (editor != null && initialQuery.isNullOrBlank()) {
            val cursor = editor.cursor
            if (cursor.isSelected) {
                val selected = editor.text
                    .subSequence(cursor.left, cursor.right).toString()
                if (selected.isNotBlank() && !selected.contains('\n')) {
                    editor.searcher.isEnsureOccurrenceVisible = true
                    queryValue = TextFieldValue(selected, TextRange(0, selected.length))
                    performSearch(selected)
                }
            }
        }
        if (queryValue.text.isNotEmpty()) {
            queryValue = queryValue.copy(selection = TextRange(0, queryValue.text.length))
        }
        focusRequester.requestFocus()
    }

    LaunchedEffect(editor, initialQuery) {
        val query = initialQuery ?: return@LaunchedEffect
        if (query.isNotBlank()) {
            editor?.searcher?.isEnsureOccurrenceVisible = true
            performSearch(query)
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (showReplaceField) 0f else -180f,
        label = "chevron",
    )

    Surface(tonalElevation = 3.dp, color = BarColor) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 0.4.dp)
            Text(
                text = if (regexError != null) {
                    "Error"
                } else if (matchCount == 0 && searchQuery.isNotEmpty()) {
                    "No results"
                } else if (searchQuery.isNotEmpty()) {
                    "$matchCount result${if (matchCount == 1) "" else "s"}"
                } else {
                    "No search"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (regexError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            )
            HorizontalDivider(thickness = 0.4.dp)
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showReplaceField = !showReplaceField }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (showReplaceField) "Hide replace" else "Show replace",
                        modifier = Modifier.rotate(chevronRotation),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        value = queryValue,
                        onValueChange = { new ->
                            val textChanged = new.text != queryValue.text
                            queryValue = new
                            if (textChanged) performSearch(new.text)
                        },
                        placeholder = { Text("Find") },
                        singleLine = true,
                        isError = regexError != null,
                        supportingText = regexError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (searchQuery.isNotEmpty()) editor?.searcher?.gotoNext()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF888888),
                            unfocusedBorderColor = Color(0xFF525252),
                        ),
                        trailingIcon = {
                            Box {
                                IconButton(onClick = { showOptionsMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                                }
                                DropdownMenu(
                                    containerColor = MaterialTheme.colorScheme.onSecondary,
                                    expanded = showOptionsMenu,
                                    onDismissRequest = { showOptionsMenu = false },
                                    properties = PopupProperties(
                                        focusable = false,
                                        dismissOnBackPress = false,
                                    ),
                                ) {
                                    SearchOptionItem(
                                        iconRes = R.drawable.text_icon_altercase,
                                        label = "Match case",
                                        checked = !ignoreCase,
                                        onToggle = {
                                            ignoreCase = !ignoreCase
                                            performSearch(isIgnoreCase = ignoreCase)
                                        },
                                    )
                                    SearchOptionItem(
                                        iconRes = R.drawable.text_icon_match_whole_word,
                                        label = "Match whole word",
                                        checked = wholeWord,
                                        onToggle = {
                                            wholeWord = !wholeWord
                                            if (wholeWord) useRegex = false
                                            performSearch(
                                                isUseRegex = useRegex,
                                                isWholeWord = wholeWord,
                                            )
                                        },
                                    )
                                    SearchOptionItem(
                                        iconRes = R.drawable.text_icon_regularexpression,
                                        label = "Regular expression",
                                        checked = useRegex,
                                        onToggle = {
                                            useRegex = !useRegex
                                            if (useRegex) wholeWord = false
                                            performSearch(
                                                isUseRegex = useRegex,
                                                isWholeWord = wholeWord,
                                            )
                                        },
                                    )
                                    SearchOptionItem(
                                        icon = Icons.Filled.Close,
                                        label = "Close search",
                                        onToggle = {
                                            showOptionsMenu = false
                                            onClose()
                                        },
                                    )
                                }
                            }
                        },
                    )
                    if (showReplaceField) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            placeholder = { Text("Replace") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF888888),
                                unfocusedBorderColor = Color(0xFF525252),
                            ),
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, bottom = 4.dp, end = 8.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { if (searchQuery.isNotEmpty()) editor?.searcher?.gotoPrevious() },
                    enabled = searchQuery.isNotEmpty(),
                ) { Text("Prev") }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { if (searchQuery.isNotEmpty()) editor?.searcher?.gotoNext() },
                    enabled = searchQuery.isNotEmpty(),
                ) { Text("Next") }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (searchQuery.isNotEmpty()) {
                            editor?.searcher?.replaceCurrentMatch(replaceText)
                            editor?.searcher?.gotoNext()
                        }
                    },
                    enabled = searchQuery.isNotEmpty() && showReplaceField,
                ) { Text("Replace") }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (searchQuery.isNotEmpty()) editor?.searcher?.replaceAll(replaceText)
                    },
                    enabled = searchQuery.isNotEmpty() && showReplaceField,
                ) { Text("Replace all") }
            }
        }
    }
}

private fun countMatches(
    haystack: String,
    query: String,
    ignoreCase: Boolean,
    useRegex: Boolean,
    wholeWord: Boolean,
): Int {
    if (query.isEmpty()) return 0
    if (useRegex || wholeWord) {
        val pattern = if (wholeWord) "\\b${Pattern.quote(query)}\\b" else query
        val flags = (if (ignoreCase) Pattern.CASE_INSENSITIVE else 0) or Pattern.MULTILINE
        val matcher = Pattern.compile(pattern, flags).matcher(haystack)
        var count = 0
        while (matcher.find()) count++
        return count
    }
    var count = 0
    var index = 0
    val lastStart = haystack.length - query.length
    search@ while (index <= lastStart) {
        for (offset in query.indices) {
            val h = haystack[index + offset]
            val q = query[offset]
            if (!(h == q || (ignoreCase && Character.toLowerCase(h) == Character.toLowerCase(q)))) {
                index++
                continue@search
            }
        }
        count++
        index += query.length
    }
    return count
}

@Composable
private fun SearchOptionItem(
    label: String,
    @DrawableRes iconRes: Int? = null,
    icon: ImageVector? = null,
    checked: Boolean? = null,
    onToggle: () -> Unit,
) {
    require(iconRes != null || icon != null) {
        "SearchOptionItem requires either iconRes or icon"
    }

    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(label)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                if (checked != null) {
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
        },
        onClick = onToggle,
    )
}

@Preview(
    showBackground = true,
    showSystemUi = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun CustomSearchBarPreview() {
    AppTheme {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.BottomCenter
            ) {
                CustomSearchBar(editor = null)
            }
        }
    }
}

@Preview(
    name = "Search bar with replace",
    showBackground = true,
    showSystemUi = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun CustomSearchBarReplacePreview() {
    AppTheme {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.BottomCenter
            ) {
                CustomSearchBar(
                    editor = null,
                    initialQuery = "SEARCH",
                    initialShowReplace = true,
                )
            }
        }
    }
}
