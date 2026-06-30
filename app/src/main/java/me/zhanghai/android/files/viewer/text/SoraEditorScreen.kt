package me.zhanghai.android.filesfork.viewer.text

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.graphics.inlayHint.ColorInlayHintRenderer
import io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.ext.EditorSpanInteractionHandler
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.minimap.MinimapConfig
import java8.nio.file.Path
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    path: Path, onNavigateUp: () -> Unit, viewModel: TextEditorViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var showSearchPanel by rememberSaveable { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val showUnsavedDialog = remember { mutableStateOf(false) }
    val showReloadDialog = remember { mutableStateOf(false) }
    val canUndo = remember { mutableStateOf(false) }
    val canRedo = remember { mutableStateOf(false) }
    val showSettingsDialog = rememberSaveable { mutableStateOf(false) }
    val typefaceToLoad = remember {
        try {
            Typeface.createFromAsset(context.assets, "fonts/JetBrainsMono-Regular.ttf")
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
    }

    fun refreshUndoRedo() {
        canUndo.value = editorRef?.canUndo() ?: false
        canRedo.value = editorRef?.canRedo() ?: false
    }

    var forceLanguage by rememberSaveable { mutableStateOf("auto") }
    val resolvedTargetScope = remember(path, forceLanguage, viewModel.appLoadState) {
        if (viewModel.appLoadState != AppLoadState.GrammarReady) return@remember null
        if (forceLanguage == "none") return@remember null
        if (forceLanguage != "auto") {
            return@remember LanguageRegistry.scopeForLanguage(forceLanguage)
        }
        val ext = path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase()
        ext?.let { LanguageRegistry.scopeForExtension(it) }
    }
    val language = remember(resolvedTargetScope) {
        resolvedTargetScope?.let {
            TextMateLanguage.create(it, true)
        }
    }
    LaunchedEffect(viewModel.loadState) {
        if (viewModel.loadState is LoadState.Success) {
            editorRef?.setText(viewModel.content)
        }
    }
    LaunchedEffect(viewModel.wordWrap) { editorRef?.isWordwrap = viewModel.wordWrap }

    LaunchedEffect(viewModel.miniMap) {
        editorRef?.props?.showMinimap = viewModel.miniMap
        editorRef?.invalidate()
    }
    LaunchedEffect(viewModel.miniMapBlocks) {
        editorRef?.props?.minimapConfig =
            MinimapConfig(minimapDrawTextAsBlocks = viewModel.miniMapBlocks)
        editorRef?.invalidate()
    }
    LaunchedEffect(language, viewModel.syntaxHighlight) {
        editorRef?.setEditorLanguage(
            if (viewModel.syntaxHighlight) language else null
        )
    }
    LaunchedEffect(viewModel.selectedTheme) {
        ThemeRegistry.getInstance().setTheme(viewModel.selectedTheme)
        val newScheme = buildColorScheme()
        editorRef?.colorScheme = newScheme
        editorRef?.let { editor ->
            val (track, thumb) = deriveScrollbarDrawables(newScheme)
            editor.renderer.verticalScrollbarTrackDrawable = track
            editor.renderer.verticalScrollbarThumbDrawable = thumb
            editor.invalidate()
        }
    }
    LaunchedEffect(resolvedTargetScope, viewModel.syntaxHighlight, viewModel.appLoadState) {
        if (viewModel.syntaxHighlight && resolvedTargetScope != null && viewModel.appLoadState == AppLoadState.GrammarReady) {
            editorRef?.setEditorLanguage(TextMateLanguage.create(resolvedTargetScope, true))
        } else {
            editorRef?.setEditorLanguage(null)
        }
    }
    LaunchedEffect(viewModel.selectedTheme) {
        ThemeRegistry.getInstance().setTheme(viewModel.selectedTheme)
        editorRef?.colorScheme = buildColorScheme()
        editorRef?.invalidate()
    }
    val title = buildString {
        if (viewModel.isModified) append("*")
        append(path.fileName?.toString() ?: "")
    }
    BackHandler(enabled = showSearchPanel || viewModel.isModified) {
        when {
            showSearchPanel -> {
                showSearchPanel = false
                editorRef?.searcher?.stopSearch()
            }

            viewModel.isModified -> showUnsavedDialog.value = true
        }
    }
    DisposableEffect(editorRef) {
        val editor = editorRef ?: return@DisposableEffect onDispose {}
        if (viewModel.textSizePx > 0f) editor.setTextSizePx(viewModel.textSizePx)
        val receipt = editor.subscribeEvent(ScrollEvent::class.java) { _, _ ->
            viewModel.saveTextSize(editor.textSizePx)
        }
        onDispose {
            receipt.unsubscribe()
            viewModel.saveTextSize(editor.textSizePx)
        }
    }

    if (showUnsavedDialog.value) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog.value = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Discard and close?") },
            confirmButton = {
                TextButton(onClick = { showUnsavedDialog.value = false; onNavigateUp() }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog.value = false }) {
                    Text("Keep editing")
                }
            })
    }

    if (showReloadDialog.value) {
        AlertDialog(
            onDismissRequest = { showReloadDialog.value = false },
            title = { Text("Reload file?") },
            text = { Text("Unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showReloadDialog.value = false
                    viewModel.load(path)
                }) { Text("Reload") }
            },
            dismissButton = {
                TextButton(onClick = { showReloadDialog.value = false }) { Text("Cancel") }
            })
    }

    if (showSettingsDialog.value) {
        EditorSettingsDialog(
            selectedTheme = viewModel.selectedTheme,
            onThemeSelected = { viewModel.setTheme(it) },
            forceLanguage = forceLanguage,
            onLanguageSelected = { forceLanguage = it },
            onDismiss = { showSettingsDialog.value = false })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.ime,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) }, navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.isModified) showReloadDialog.value = true
                        else onNavigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }, actions = {
                    IconButton(
                        onClick = {
                            editorRef?.let { editor ->
                                viewModel.save(
                                    path = path,
                                    getText = { editor.text.toString() },
                                    onSuccess = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Saved")
                                        }
                                    },
                                    onError = { msg ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Error: $msg")
                                        }
                                    })
                            }
                        }, enabled = viewModel.isModified
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                    IconButton(
                        onClick = {
                            editorRef?.undo()
                            refreshUndoRedo()
                        }, enabled = canUndo.value
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = {
                            editorRef?.redo()
                            refreshUndoRedo()
                        }, enabled = canRedo.value
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { showSearchPanel = !showSearchPanel }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(text = { Text("Word wrap") }, trailingIcon = {
                                Checkbox(checked = viewModel.wordWrap, onCheckedChange = null)
                            }, onClick = {
                                viewModel.toggleWordWrap()
                                showOverflowMenu = false
                            })
                            DropdownMenuItem(
                                text = { Text("Syntax highlighting") },
                                trailingIcon = {
                                    Checkbox(
                                        checked = viewModel.syntaxHighlight, onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    viewModel.toggleSyntaxHighlight()
                                    showOverflowMenu = false
                                })
                            DropdownMenuItem(
                                text = { Text("Show minimap") },
                                trailingIcon = {
                                    Checkbox(
                                        checked = viewModel.miniMap, onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    viewModel.toggleMinimap()
                                    showOverflowMenu = false
                                })
                            if (viewModel.miniMap) {
                                DropdownMenuItem(
                                    text = { Text("Render characters") },
                                    trailingIcon = {
                                        Checkbox(
                                            checked = !viewModel.miniMapBlocks,
                                            onCheckedChange = null
                                        )
                                    },
                                    onClick = {
                                        viewModel.toggleMinimapBlocks()
                                        showOverflowMenu = false
                                    })
                            }
                            DropdownMenuItem(text = { Text("Reload") }, leadingIcon = {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                            }, onClick = {
                                showOverflowMenu = false
                                if (viewModel.isModified) showReloadDialog.value = true
                                else viewModel.load(path)
                            })
                            DropdownMenuItem(text = { Text("Settings") }, leadingIcon = {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            }, onClick = {
                                showOverflowMenu = false
                                showSettingsDialog.value = true
                            })
                        }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (val state = viewModel.loadState) {
                is LoadState.Loading -> {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                is LoadState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(16.dp)
                    )
                }

                is LoadState.Success -> {
                    if (viewModel.prefsLoaded) {
                        AndroidView(
                            factory = { ctx ->
                                CodeEditor(ctx).apply {
                                    isWordwrap = viewModel.wordWrap
                                    val initialScheme = buildColorScheme()
                                    colorScheme = initialScheme
                                    val (track, thumb) = deriveScrollbarDrawables(initialScheme)
                                    renderer.verticalScrollbarTrackDrawable = track
                                    renderer.verticalScrollbarThumbDrawable = thumb
                                    renderer.horizontalScrollbarTrackDrawable = track
                                    renderer.horizontalScrollbarThumbDrawable = thumb
                                    typefaceText = typefaceToLoad
                                    typefaceLineNumber = typefaceToLoad
                                    setEditorLanguage(
                                        if (viewModel.syntaxHighlight && resolvedTargetScope != null) TextMateLanguage.create(
                                            resolvedTargetScope, true
                                        )
                                        else null
                                    )
                                    if (viewModel.textSizePx > 0f) setTextSizePx(viewModel.textSizePx)
                                    setText(viewModel.content)
                                    registerInlayHintRenderers(
                                        TextInlayHintRenderer.DefaultInstance,
                                        ColorInlayHintRenderer.DefaultInstance
                                    )
                                    props.showMinimap = viewModel.miniMap
                                    props.minimapConfig =
                                        MinimapConfig(minimapDrawTextAsBlocks = viewModel.miniMapBlocks)

                                    setLineSpacing(2f, 1.1f)
                                    // nonPrintablePaintingFlags = CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR or CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or CodeEditor.FLAG_DRAW_SOFT_WRAP
                                    searcher.replaceOptions = EditorSearcher.ReplaceOptions(true)
                                    EditorSpanInteractionHandler(this)
                                    getComponent<EditorAutoCompletion>().setEnabledAnimation(true)
                                    subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                                        canUndo.value = canUndo()
                                        canRedo.value = canRedo()
                                        viewModel.onContentChanged(text.toString())
                                    }
                                    viewModel.restoreCursorState(this)
                                }.also { editorRef = it }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            onRelease = { editor ->
                                viewModel.saveCursorState(editor)
                                editor.release()
                            },
                        )
                    }
                }
            }
            SymbolInputBar(
                editor = editorRef,
                typeface = typefaceToLoad,
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.navigationBars.exclude(WindowInsets.ime)
                )
            )
            AnimatedVisibility(
                visible = showSearchPanel,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                SearchReplacePanel(editor = editorRef)
            }
        }
    }
}

@Composable
private fun EditorSettingsDialog(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit,
    forceLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = ThemeManager.availableThemes
    val languages = listOf("auto", "none") + LanguageRegistry.supportedLanguages()
    val showThemePicker = remember { mutableStateOf(false) }
    val showLanguagePicker = remember { mutableStateOf(false) }
    if (showThemePicker.value) {
        SingleChoiceDialog(
            title = "Theme",
            options = themes,
            selected = selectedTheme,
            onSelect = onThemeSelected,
            onDismiss = { showThemePicker.value = false })
    }
    if (showLanguagePicker.value) {
        SingleChoiceDialog(
            title = "Language",
            options = languages,
            selected = forceLanguage,
            onSelect = onLanguageSelected,
            onDismiss = { showLanguagePicker.value = false })
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editor settings") }, text = {
        Column {
            SettingsClickRow(
                label = "Theme", value = selectedTheme, onClick = { showThemePicker.value = true })
            SettingsClickRow(
                label = "Language",
                value = forceLanguage,
                onClick = { showLanguagePicker.value = true })
        }
    }, confirmButton = {
        TextButton(onClick = onDismiss) { Text("Done") }
    })
}

@Composable
private fun SettingsClickRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Row(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(" (${value})")
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
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
                        .padding(vertical = 4.dp)) {
                    RadioButton(selected = option == selected, onClick = {
                        onSelect(option)
                        onDismiss()
                    })
                    Spacer(Modifier.width(8.dp))
                    Text(option)
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = onDismiss) { Text("Cancel") }
    })
}

@Composable
private fun SearchReplacePanel(editor: CodeEditor?) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var replaceText by rememberSaveable { mutableStateOf("") }
    var useRegex by rememberSaveable { mutableStateOf(false) }
    var ignoreCase by rememberSaveable { mutableStateOf(true) }
    var regexError by remember { mutableStateOf<String?>(null) }
    var showReplaceField by rememberSaveable { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var matchCount by remember { mutableIntStateOf(0) }

    fun performSearch(
        query: String = searchQuery,
        isIgnoreCase: Boolean = ignoreCase,
        isUseRegex: Boolean = useRegex
    ) {
        if (query.isEmpty()) {
            editor?.searcher?.stopSearch()
            matchCount = 0
            return
        }
        try {
            editor?.searcher?.search(query, EditorSearcher.SearchOptions(isIgnoreCase, isUseRegex))
            regexError = null
            matchCount = if (isUseRegex) {
                try {
                    Regex(if (isIgnoreCase) "(?i)$query" else query).findAll(
                        editor?.text?.toString() ?: ""
                    ).count()
                } catch (_: Exception) {
                    0
                }
            } else {
                val haystack = editor?.text?.toString() ?: ""
                val needle = if (isIgnoreCase) query.lowercase() else query
                val source = if (isIgnoreCase) haystack.lowercase() else haystack
                var count = 0
                var idx = 0
                while (true) {
                    idx = source.indexOf(needle, idx)
                    if (idx == -1) break
                    count++; idx += needle.length
                }
                count
            }
        } catch (e: java.util.regex.PatternSyntaxException) {
            regexError = e.message
            matchCount = 0
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (showReplaceField) 0f else 180f, label = "chevron"
    )

    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { showReplaceField = !showReplaceField }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (showReplaceField) "Hide replace" else "Show replace",
                        modifier = Modifier.rotate(chevronRotation)
                    )
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; performSearch(it) },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    isError = regexError != null,
                    supportingText = regexError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (searchQuery.isNotEmpty()) editor?.searcher?.gotoNext()
                    }),
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        Box {
                            IconButton(onClick = { showOptionsMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false }) {
                                DropdownMenuItem(text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = ignoreCase, onCheckedChange = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Ignore case")
                                    }
                                }, onClick = {
                                    val newValue = !ignoreCase
                                    ignoreCase = newValue
                                    performSearch(isIgnoreCase = newValue)
                                })
                                DropdownMenuItem(text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = useRegex, onCheckedChange = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Regex")
                                    }
                                }, onClick = {
                                    val newValue = !useRegex
                                    useRegex = newValue
                                    showOptionsMenu = false
                                    performSearch(isUseRegex = newValue)
                                })
                            }
                        }
                    })
            }
            Spacer(Modifier.height(4.dp))
            AnimatedVisibility(visible = showReplaceField) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp)
                ) {
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        placeholder = { Text("Replace") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, bottom = 4.dp)
            ) {
                TextButton(
                    onClick = { if (searchQuery.isNotEmpty()) editor?.searcher?.gotoPrevious() },
                    enabled = searchQuery.isNotEmpty()
                ) { Text("Prev") }
                TextButton(
                    onClick = { if (searchQuery.isNotEmpty()) editor?.searcher?.gotoNext() },
                    enabled = searchQuery.isNotEmpty()
                ) { Text("Next") }
                if (showReplaceField) {
                    TextButton(
                        onClick = {
                            if (searchQuery.isNotEmpty()) {
                                editor?.searcher?.replaceCurrentMatch(replaceText)
                                editor?.searcher?.gotoNext()
                            }
                        }, enabled = searchQuery.isNotEmpty()
                    ) { Text("Replace") }
                    TextButton(
                        onClick = {
                            if (searchQuery.isNotEmpty()) editor?.searcher?.replaceAll(replaceText)
                        }, enabled = searchQuery.isNotEmpty()
                    ) { Text("Replace all") }
                }
                Spacer(Modifier.weight(1f))
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = if (regexError != null) "Error"
                        else if (matchCount == 0) "No results"
                        else "$matchCount result${if (matchCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (regexError != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

private val SYMBOLS = listOf(
    "->" to "\t",
    "{" to "{}",
    "}" to "}",
    "(" to "(",
    ")" to ")",
    "," to ",",
    "." to ".",
    ";" to ";",
    "\"" to "\"",
    "?" to "?",
    "+" to "+",
    "-" to "-",
    "*" to "*",
    "/" to "/",
    "<" to "<",
    ">" to ">",
    "[" to "[",
    "]" to "]",
    ":" to ":"
)

private fun buildColorScheme(): TextMateColorScheme =
    TextMateColorScheme.create(ThemeRegistry.getInstance())

@Composable
private fun SymbolInputBar(
    editor: CodeEditor?,
    typeface: Typeface,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.exclude(WindowInsets.ime)
                ),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            items(SYMBOLS) { (label, insert) ->
                TextButton(
                    onClick = {
                        editor?.let {
                            val cursor = it.cursor
                            it.text.replace(
                                cursor.leftLine,
                                cursor.leftColumn,
                                cursor.rightLine,
                                cursor.rightColumn,
                                insert
                            )
                        }
                    }, modifier = Modifier.height(40.dp)
                ) {
                    Text(label, fontFamily = androidx.compose.ui.text.font.FontFamily(typeface))
                }
            }
        }
    }
}
