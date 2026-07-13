@file:Suppress("SpellCheckingInspection")

package me.zhanghai.android.filesfork.viewer.text

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.More
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFormat
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.graphics.inlayHint.ColorInlayHintRenderer
import io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.util.IntPair
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.ext.EditorSpanInteractionHandler
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.minimap.MinimapConfig
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java8.nio.file.Path
import kotlinx.coroutines.launch
import me.zhanghai.android.filesfork.viewer.text.ThemeRegistry as EditorThemeRegistry

@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    path: Path, onNavigateUp: () -> Unit, viewModel: TextEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var showSearchPanel by rememberSaveable { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val showUnsavedDialog = remember { mutableStateOf(false) }
    val showReloadDialog = remember { mutableStateOf(false) }
    val canUndo = remember { mutableStateOf(false) }
    val canRedo = remember { mutableStateOf(false) }
    val showSettingsDialog = rememberSaveable { mutableStateOf(false) }
    val typefaceToLoad = remember(viewModel.selectedFont) {
        FontRegistry.loadTypeface(context, viewModel.selectedFont)
    }
    var saveButtonState by remember { mutableStateOf(SaveButtonState.IDLE) }

    LaunchedEffect(saveButtonState) {
        if (saveButtonState == SaveButtonState.SAVED) {
            kotlinx.coroutines.delay(1500)
            saveButtonState = SaveButtonState.IDLE
        } else if (saveButtonState == SaveButtonState.ERROR) {
            kotlinx.coroutines.delay(2500)
            saveButtonState = SaveButtonState.IDLE
        }
    }
    LaunchedEffect(viewModel.isModified) {
        if (viewModel.isModified && saveButtonState != SaveButtonState.IDLE) {
            saveButtonState = SaveButtonState.IDLE
        }
    }

    LaunchedEffect(typefaceToLoad) {
        editorRef?.typefaceText = typefaceToLoad
        editorRef?.typefaceLineNumber = typefaceToLoad
        editorRef?.invalidate()
    }
    fun refreshUndoRedo() {
        canUndo.value = editorRef?.canUndo() ?: false
        canRedo.value = editorRef?.canRedo() ?: false
    }

    var layoutReady by remember { mutableStateOf(false) }
    var forceLanguage by rememberSaveable { mutableStateOf("auto") }
    val resolvedTargetScope = remember(path, forceLanguage, viewModel.grammarsReady) {
        if (!viewModel.grammarsReady) return@remember null
        if (forceLanguage == "none") return@remember null
        if (forceLanguage != "auto") return@remember LanguageRegistry.scopeForLanguage(forceLanguage)
        val ext = path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase()
        ext?.let { LanguageRegistry.scopeForExtension(it) }
    }
    val activeLanguage =
        remember(resolvedTargetScope, viewModel.syntaxHighlight, viewModel.grammarsReady) {
            if (viewModel.syntaxHighlight && resolvedTargetScope != null && viewModel.grammarsReady) {
                TextMateLanguage.create(resolvedTargetScope, true)
            } else null
        }
    LaunchedEffect(editorRef, viewModel.selectedTheme) {
        editorRef?.let { applyTheme(it, viewModel.selectedTheme) }
    }
    LaunchedEffect(editorRef, viewModel.miniMap, viewModel.miniMapBlocks) {
        editorRef?.let { applyMinimap(it, viewModel.miniMap, viewModel.miniMapBlocks) }
    }
    LaunchedEffect(editorRef, viewModel.invisibleChars) {
        editorRef?.let { applyInvisibleChars(it, viewModel.invisibleChars) }
    }
    LaunchedEffect(editorRef, activeLanguage) {
        editorRef?.setEditorLanguage(activeLanguage)
    }
    LaunchedEffect(editorRef, viewModel.wordWrap) {
        if (editorRef?.isWordwrap != viewModel.wordWrap) {
            editorRef?.isWordwrap = viewModel.wordWrap
        }
    }
    LaunchedEffect(editorRef, viewModel.lineNumbers) {
        editorRef?.isLineNumberEnabled = viewModel.lineNumbers
    }
    LaunchedEffect(editorRef, typefaceToLoad) {
        editorRef?.let {
            it.typefaceText = typefaceToLoad
            it.typefaceLineNumber = typefaceToLoad
            it.invalidate()
        }
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
            selectedFont = viewModel.selectedFont,
            fontOptions = viewModel.fontOptions,
            onFontSelected = { viewModel.setFont(it) },
            onFontImported = { imported ->
                viewModel.refreshFontOptions()
                viewModel.setFont(imported.id)
            },
            onFontDeleted = { deleted ->
                val newFontId =
                    FontRegistry.deleteImportedFont(context, deleted.id, viewModel.selectedFont)
                if (newFontId != viewModel.selectedFont) {
                    viewModel.setFont(newFontId)
                }
                viewModel.refreshFontOptions()
            },
            onDismiss = { showSettingsDialog.value = false })
    }
    val title = buildString {
        if (viewModel.isModified) append("*")
        append(path.fileName?.toString() ?: "")
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
                                        saveButtonState = SaveButtonState.SAVED
                                    },
                                    onError = { msg ->
                                        saveButtonState = SaveButtonState.ERROR
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Error: $msg")
                                        }
                                    })
                            }
                        }, enabled = viewModel.isModified && saveButtonState == SaveButtonState.IDLE
                    ) {
                        Crossfade(
                            targetState = saveButtonState,
                            animationSpec = tween(durationMillis = 300),
                            label = "saveButtonMorph"
                        ) { state ->
                            when (state) {
                                SaveButtonState.IDLE -> Icon(
                                    Icons.Filled.Save, contentDescription = "Save"
                                )

                                SaveButtonState.SAVED -> Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Saved",
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                SaveButtonState.ERROR -> Icon(
                                    Icons.Filled.Error,
                                    contentDescription = "Save error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { editorRef?.undo(); refreshUndoRedo() }, enabled = canUndo.value
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = { editorRef?.redo(); refreshUndoRedo() }, enabled = canRedo.value
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    val flexibleActions = listOf(
                        TopBarAction(
                            "Reload", Icons.Filled.Refresh
                        ) {
                            if (viewModel.isModified) showReloadDialog.value = true
                            else viewModel.load(path)
                        },
                        TopBarAction(
                            "Settings", Icons.Filled.Settings
                        ) {
                            showSettingsDialog.value = true
                        },
                        TopBarAction("Search", Icons.Filled.Search) {
                            showSearchPanel = !showSearchPanel
                        },
                        TopBarAction(
                            "Word wrap",
                            Icons.AutoMirrored.Filled.WrapText,
                            checked = viewModel.wordWrap
                        ) { viewModel.toggleWordWrap() },
                        TopBarAction(
                            "Symbols", Icons.AutoMirrored.Filled.More, checked = viewModel.symbolBar
                        ) { viewModel.toggleSymbolBar() },
                        TopBarAction(
                            "Syntax highlighting",
                            Icons.Filled.Palette,
                            checked = viewModel.syntaxHighlight
                        ) { viewModel.toggleSyntaxHighlight() },
                        TopBarAction(
                            "Show line numbers",
                            Icons.Filled.FormatListNumbered,
                            checked = viewModel.lineNumbers
                        ) { viewModel.toggleLineNumbers(); },
                        TopBarAction(
                            "Show invisible chars",
                            Icons.Filled.RemoveRedEye,
                            checked = viewModel.invisibleChars
                        ) { viewModel.toggleInvisibleChars(); },
                        TopBarAction(
                            "Show minimap", Icons.Filled.Map, checked = viewModel.miniMap
                        ) { viewModel.toggleMinimap() },
                    ) + if (viewModel.miniMap) {
                        listOf(
                            TopBarAction(
                                "Render characters",
                                Icons.Filled.TextFormat,
                                checked = !viewModel.miniMapBlocks
                            ) {
                                viewModel.toggleMinimapBlocks()
                            })
                    } else emptyList()
                    val maxVisibleActions = with(LocalDensity.current) {
                        when (LocalWindowInfo.current.containerSize.width.toDp()) {
                            in 0.dp..460.dp -> 0
                            in 461.dp..840.dp -> 2
                            else -> 4
                        }
                    }
                    val visibleActions = flexibleActions.take(maxVisibleActions)
                    val overflowActions = flexibleActions.drop(maxVisibleActions)
                    visibleActions.forEach { action ->
                        IconButton(onClick = action.onClick, enabled = action.enabled) {
                            Icon(action.icon, contentDescription = action.label)
                        }
                    }
                    if (overflowActions.isNotEmpty()) {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                properties = PopupProperties(
                                    focusable = false, dismissOnBackPress = false
                                )
                            ) {
                                overflowActions.forEach { action ->
                                    DropdownMenuItem(text = { Text(action.label) }, leadingIcon = {
                                        Icon(
                                            action.icon, contentDescription = null
                                        )
                                    }, trailingIcon = action.checked?.let {
                                        { Checkbox(checked = it, onCheckedChange = null) }
                                    }, onClick = {
                                        action.onClick()
                                    })
                                }
                            }
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
                                    applyTheme(this, viewModel.selectedTheme)
                                    isWordwrap = viewModel.wordWrap
                                    isLineNumberEnabled = viewModel.lineNumbers
                                    typefaceText = typefaceToLoad
                                    typefaceLineNumber = typefaceToLoad
                                    setEditorLanguage(activeLanguage)
                                    applyMinimap(this, viewModel.miniMap, viewModel.miniMapBlocks)
                                    applyInvisibleChars(this, viewModel.invisibleChars)
                                    isWordwrap = viewModel.wordWrap
                                    setText(viewModel.content)
                                    subscribeEvent(LayoutStateChangeEvent::class.java) { event, _ ->
                                        if (!event.isLayoutBusy) {
                                            layoutReady = true
                                        }
                                    }
                                    if (viewModel.textSizePx > 0f) setTextSizePx(viewModel.textSizePx)
                                    registerInlayHintRenderers(
                                        TextInlayHintRenderer.DefaultInstance,
                                        ColorInlayHintRenderer.DefaultInstance
                                    )
                                    setLineSpacing(2f, 1.1f)
                                    searcher.replaceOptions = EditorSearcher.ReplaceOptions(true)
                                    EditorSpanInteractionHandler(this)
                                    getComponent<EditorAutoCompletion>().setEnabledAnimation(true)
                                    subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                                        canUndo.value = canUndo()
                                        canRedo.value = canRedo()
                                        viewModel.onContentChanged(text.toString())
                                    }
                                    viewModel.restoreCursorState(this)
                                    fun isImeVisible(): Boolean {
                                        val insets =
                                            ViewCompat.getRootWindowInsets(this) ?: return false
                                        return insets.isVisible(WindowInsetsCompat.Type.ime())
                                    }

                                    var selectionTap = false
                                    var forwardingToEditor = false
                                    var pendingDown: MotionEvent? = null
                                    var downX = 0f
                                    var downY = 0f
                                    val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop

                                    setOnTouchListener { view, event ->
                                        if (isImeVisible()) return@setOnTouchListener false
                                        when (event.actionMasked) {
                                            MotionEvent.ACTION_DOWN -> {
                                                val cur = cursor
                                                val onHandle =
                                                    leftHandleDescriptor.position.contains(
                                                        event.x, event.y
                                                    ) || rightHandleDescriptor.position.contains(
                                                        event.x, event.y
                                                    ) || insertHandleDescriptor.position.contains(
                                                        event.x, event.y
                                                    )
                                                forwardingToEditor = false
                                                selectionTap = false
                                                if (!onHandle && cur.isSelected && isScreenPointOnText(
                                                        event.x, event.y
                                                    )
                                                ) {
                                                    val pos =
                                                        getPointPositionOnScreen(event.x, event.y)
                                                    val line = IntPair.getFirst(pos)
                                                    val column = IntPair.getSecond(pos)
                                                    val tapIndex = text.getCharIndex(line, column)
                                                    val withinLines =
                                                        line in cur.leftLine..cur.rightLine
                                                    if (withinLines && tapIndex in cur.left..cur.right) {
                                                        selectionTap = true
                                                        downX = event.x
                                                        downY = event.y
                                                        pendingDown = MotionEvent.obtain(event)
                                                        return@setOnTouchListener true
                                                    }
                                                }
                                                false
                                            }

                                            MotionEvent.ACTION_MOVE -> {
                                                if (selectionTap && !forwardingToEditor) {
                                                    val dx = event.x - downX
                                                    val dy = event.y - downY
                                                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                                        forwardingToEditor = true
                                                        selectionTap = false
                                                        pendingDown?.let { view.onTouchEvent(it) }
                                                        pendingDown?.recycle()
                                                        pendingDown = null
                                                    } else {
                                                        return@setOnTouchListener true
                                                    }
                                                }
                                                if (forwardingToEditor) {
                                                    view.onTouchEvent(event)
                                                    true
                                                } else {
                                                    false
                                                }
                                            }

                                            MotionEvent.ACTION_UP -> {
                                                val consumed = when {
                                                    forwardingToEditor -> {
                                                        view.onTouchEvent(event)
                                                        true
                                                    }

                                                    selectionTap -> {
                                                        selectionTap = false
                                                        view.performClick()
                                                        showSoftInput()
                                                        true
                                                    }

                                                    else -> false
                                                }
                                                forwardingToEditor = false
                                                pendingDown?.recycle()
                                                pendingDown = null
                                                consumed
                                            }

                                            MotionEvent.ACTION_CANCEL -> {
                                                if (forwardingToEditor) view.onTouchEvent(event)
                                                selectionTap = false
                                                forwardingToEditor = false
                                                pendingDown?.recycle()
                                                pendingDown = null
                                                false
                                            }

                                            else -> forwardingToEditor
                                        }
                                    }
                                }.also { editorRef = it }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .alpha(if (layoutReady) 1f else 0f),
                            onRelease = { editor ->
                                viewModel.saveCursorState(editor)
                                editor.release()
                            })
                    }
                }
            }
            AnimatedVisibility(
                visible = (viewModel.symbolBar && layoutReady), enter = expandVertically(
                    animationSpec = tween(durationMillis = 220), expandFrom = Alignment.Bottom
                ), exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 220), shrinkTowards = Alignment.Bottom
                )
            ) {
                SymbolInputBar(
                    editor = editorRef,
                    typeface = typefaceToLoad,
                    applyNavigationBarPadding = !showSearchPanel
                )
            }
            AnimatedVisibility(
                visible = (showSearchPanel && layoutReady), enter = expandVertically(
                    animationSpec = tween(durationMillis = 220), expandFrom = Alignment.Top
                ), exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 220), shrinkTowards = Alignment.Top
                )
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
    selectedFont: String,
    fontOptions: List<FontRegistry.FontOption>,
    onFontSelected: (String) -> Unit,
    onFontImported: (FontRegistry.FontOption) -> Unit,
    onFontDeleted: (FontRegistry.FontOption) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf("auto", "none") + LanguageRegistry.supportedLanguages()
    val showThemePicker = remember { mutableStateOf(false) }
    val showLanguagePicker = remember { mutableStateOf(false) }
    val showFontPicker = remember { mutableStateOf(false) }
    if (showThemePicker.value) {
        SingleChoiceDialog(
            title = "Theme",
            options = EditorThemeRegistry.availableThemes,
            selected = EditorThemeRegistry.availableThemes.find { it.id == selectedTheme }
                ?: EditorThemeRegistry.availableThemes.firstOrNull(),
            label = { it?.displayName ?: "" },
            onSelect = { onThemeSelected(it?.id ?: "") },
            onDismiss = { showThemePicker.value = false })
    }
    if (showLanguagePicker.value) {
        SingleChoiceDialog(
            title = "Language",
            options = languages,
            selected = forceLanguage,
            label = { it },
            onSelect = onLanguageSelected,
            onDismiss = { showLanguagePicker.value = false })
    }
    if (showFontPicker.value) {
        FontChoiceDialog(
            fontOptions = fontOptions,
            selected = selectedFont,
            onSelect = onFontSelected,
            onFontImported = onFontImported,
            onFontDeleted = onFontDeleted,
            onDismiss = { showFontPicker.value = false })
    }
    val selectedThemeDisplayName =
        EditorThemeRegistry.availableThemes.find { it.id == selectedTheme }?.displayName
            ?: selectedTheme
    val selectedFontDisplayName = fontOptions.find { it.id == selectedFont }?.displayName ?: ""
    val context = LocalContext.current
    val selectedFontFamily = remember(selectedFont) {
        FontFamily(FontRegistry.loadTypeface(context, selectedFont))
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editor settings") }, text = {
        Column {
            SettingsClickRow(
                label = "Theme",
                value = selectedThemeDisplayName,
                onClick = { showThemePicker.value = true })
            SettingsClickRow(
                label = "Language",
                value = forceLanguage,
                onClick = { showLanguagePicker.value = true })
            SettingsClickRow(
                label = "Font",
                value = selectedFontDisplayName,
                valueFontFamily = selectedFontFamily,
                onClick = { showFontPicker.value = true })
        }
    }, confirmButton = {
        TextButton(onClick = onDismiss) { Text("Done") }
    })
}

private fun applyTheme(editor: CodeEditor, themeName: String) {
    ThemeRegistry.getInstance().setTheme(themeName)
    val newScheme = buildColorScheme()
    editor.colorScheme = newScheme
    editor.colorScheme.let { scheme ->
        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, "#e3e3e3".toColorInt())
        scheme.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, "#33e3e3e3".toColorInt())
        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, "#33e3e3e3".toColorInt())
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, "#33e3e3e3".toColorInt())
    }
    val (track, thumb) = deriveScrollbarDrawables()
    editor.renderer.verticalScrollbarTrackDrawable = track
    editor.renderer.verticalScrollbarThumbDrawable = thumb
    editor.renderer.horizontalScrollbarTrackDrawable = track
    editor.renderer.horizontalScrollbarThumbDrawable = thumb
}

private fun applyMinimap(editor: CodeEditor, showMinimap: Boolean, blocks: Boolean) {
    editor.props.showMinimap = showMinimap
    editor.props.minimapConfig = MinimapConfig(minimapDrawTextAsBlocks = blocks)
    editor.invalidate()
}

private fun applyInvisibleChars(editor: CodeEditor, enabled: Boolean) {
    editor.nonPrintablePaintingFlags = if (enabled) {
        CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR or CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or CodeEditor.FLAG_DRAW_SOFT_WRAP
    } else {
        0
    }
}

@Composable
private fun SettingsClickRow(
    label: String, value: String, valueFontFamily: FontFamily? = null, onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Row(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = " (${value})",
                fontFamily = valueFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
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
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
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
                    Text(
                        text = label(option),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = onDismiss) { Text("Cancel") }
    })
}

@Suppress("AssignedValueIsNeverRead")
@Composable
private fun FontChoiceDialog(
    fontOptions: List<FontRegistry.FontOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onFontImported: (FontRegistry.FontOption) -> Unit,
    onFontDeleted: (FontRegistry.FontOption) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var fontPendingDelete by remember { mutableStateOf<FontRegistry.FontOption?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
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

    val filteredOptions = remember(fontOptions, query) {
        if (query.isBlank()) fontOptions
        else fontOptions.filter { it.displayName.contains(query, ignoreCase = true) }
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
            })
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Font") }, text = {
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search fonts") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
            TextButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "*/*")
                    )
                }, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import font")
            }
            if (importError != null) {
                Text(
                    text = importError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (filteredOptions.isEmpty()) {
                Text(
                    text = if (query.isBlank()) "No fonts available" else "No fonts match \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
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
                                    }, onLongClick = if (isImported) {
                                        { fontPendingDelete = option }
                                    } else null)
                                .padding(vertical = 4.dp)) {
                            RadioButton(selected = option.id == selected, onClick = {
                                onSelect(option.id)
                                onDismiss()
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = option.displayName,
                                fontFamily = FontFamily(typeface),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
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
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        .exclude(WindowInsets.ime)
                )
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
                                onDismissRequest = { showOptionsMenu = false },
                                properties = PopupProperties(
                                    focusable = false, dismissOnBackPress = false
                                )
                            ) {
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

@Composable
private fun SymbolInputBar(
    editor: CodeEditor?,
    typeface: Typeface,
    applyNavigationBarPadding: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (applyNavigationBarPadding) {
                        Modifier.windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                                .exclude(WindowInsets.ime)
                        )
                    } else Modifier
                ), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            item {
                TextButton(
                    onClick = { editor?.unindentSelection() },
                    modifier = Modifier
                        .height(40.dp)
                        .widthIn(min = 1.dp),
                ) {
                    Text("<-", fontFamily = FontFamily(typeface), fontSize = 14.sp)
                }
            }
            item {
                TextButton(
                    modifier = Modifier
                        .height(40.dp)
                        .widthIn(min = 1.dp),
                    onClick = { editor?.indentLines(false) }) {
                    Text("->", fontFamily = FontFamily(typeface), fontSize = 14.sp)
                }
            }
            items(SYMBOLS) { (label, insert) ->
                Text(
                    text = label,
                    fontFamily = FontFamily(typeface),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
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
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp))
            }
        }
    }
}
