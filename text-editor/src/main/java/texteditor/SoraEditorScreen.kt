@file:Suppress("SpellCheckingInspection")

package texteditor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import texteditor.data.AppToasts
import texteditor.data.EditorLanguages
import texteditor.data.EmptyEditor
import texteditor.data.PreloadSurface
import texteditor.data.SlideInOverlay
import texteditor.data.registry.FontRegistry
import texteditor.editor.EditorEffects
import texteditor.editor.EditorSettings
import texteditor.editor.createCodeEditor
import texteditor.editor.linesToScan
import texteditor.ui.bars.bottombar.BarColor
import texteditor.ui.bars.bottombar.CustomSearchBar
import texteditor.ui.bars.bottombar.SymbolInputBar
import texteditor.ui.bars.topbar.TopBarActions
import texteditor.ui.dialogs.data.DialogHost
import texteditor.ui.drawer.EditorDrawerSheet
import texteditor.ui.screens.preview.MarkdownPreviewScreen
import texteditor.ui.screens.preview.PreviewState
import texteditor.ui.screens.settings.EditorSettingsScreen
import kotlin.time.Duration.Companion.milliseconds

private const val HIGHLIGHT_MAX_CHARS = 15_000_000

@Composable
private fun bottomBarInsets(): WindowInsets =
    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).exclude(WindowInsets.ime)

@Composable
fun TextEditorScreen(
    onNavigateUp: () -> Unit,
    onMinimize: () -> Unit = {},
    viewModel: SoraEditorViewModel,
    initialSearchQuery: String? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var showSearchPanel by rememberSaveable { mutableStateOf(false) }
    val canUndo = remember { mutableStateOf(false) }
    val canRedo = remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var shownPreview by remember { mutableStateOf<PreviewState?>(null) }
    var cursorLine by remember { mutableIntStateOf(1) }
    var cursorColumn by remember { mutableIntStateOf(1) }
    var lineCount by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var shownDocumentId by remember { mutableLongStateOf(viewModel.activeDocumentId) }
    var editorReadyId by remember { mutableLongStateOf(-1L) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val displayPath = viewModel.savedPath
    val typefaceToLoad = remember(viewModel.selectedFont) {
        FontRegistry.loadTypeface(context, viewModel.selectedFont)
    }
    val saveButtonState = remember { mutableStateOf(SaveButtonState.IDLE) }
    val scope = rememberCoroutineScope()

    var leaving by remember { mutableStateOf(false) }
    val noDocuments = viewModel.openDocuments.isEmpty() && !leaving

    var leaveAfterClose by remember { mutableStateOf(false) }
    val pendingCloseIds = remember { mutableStateListOf<Long>() }

    LaunchedEffect(saveButtonState.value) {
        if (saveButtonState.value == SaveButtonState.SAVED) {
            delay(1500.milliseconds)
            saveButtonState.value = SaveButtonState.IDLE
        } else if (saveButtonState.value == SaveButtonState.ERROR) {
            delay(2500.milliseconds)
            saveButtonState.value = SaveButtonState.IDLE
        }
    }
    LaunchedEffect(viewModel.isModified) {
        if (viewModel.isModified && saveButtonState.value != SaveButtonState.IDLE) {
            saveButtonState.value = SaveButtonState.IDLE
        }
    }

    fun currentText(): String = if (editorReadyId == viewModel.activeDocumentId) {
        editorRef?.text?.toString() ?: viewModel.content.toString()
    } else {
        viewModel.content.toString()
    }

    fun refreshUndoRedo() {
        canUndo.value = editorRef?.canUndo() ?: false
        canRedo.value = editorRef?.canRedo() ?: false
    }

    fun reportSaveError(message: String) {
        saveButtonState.value = SaveButtonState.ERROR
        AppToasts.show(context, message)
    }

    fun closeSearch() {
        showSearchPanel = false
        searchQuery = null
        editorRef?.searcher?.stopSearch()
    }

    fun saveCopyAsFile() {
        viewModel.saveCopyAs(
            getText = ::currentText,
            onSuccess = {
                AppToasts.show(context, "Saved copy")
            },
            onError = { message ->
                AppToasts.show(context, message)
            },
        )
    }

    fun leaveEditor() {
        leaving = true
        viewModel.openDocuments.map { it.id }.forEach { viewModel.close(it) }
        onNavigateUp()
    }

    fun saveDocument(id: Long, onSaved: () -> Unit, onSaveError: () -> Unit = {}) {
        viewModel.save(
            getText = ::currentText,
            onSuccess = {
                saveButtonState.value = SaveButtonState.SAVED
                onSaved()
            },
            onError = { message ->
                reportSaveError(message)
                onSaveError()
            },
            id = id,
        )
    }

    fun switchToDocument(id: Long) {
        if (id == viewModel.activeDocumentId) return
        viewModel.activate(id)
    }

    fun advanceCloseQueue() {
        while (pendingCloseIds.isNotEmpty()) {
            val id = pendingCloseIds.removeAt(0)
            val document = viewModel.openDocuments.firstOrNull { it.id == id } ?: continue
            if (document.isModified) {
                viewModel.requestClose(id)
                return
            }
            viewModel.close(id)
        }
    }

    fun closeDocuments(ids: List<Long>) {
        leaveAfterClose = false
        pendingCloseIds.clear()
        pendingCloseIds.addAll(ids)
        advanceCloseQueue()
    }

    fun saveAllDocuments() {
        val pending = viewModel.openDocuments.filter { it.isModified }.map { it.id }
        fun saveNext(index: Int) {
            if (index >= pending.size) return
            viewModel.save(
                getText = ::currentText,
                onSuccess = {
                    saveButtonState.value = SaveButtonState.SAVED
                    saveNext(index + 1)
                },
                onError = { message ->
                    reportSaveError(message)
                    saveNext(index + 1)
                },
                id = pending[index],
            )
        }
        saveNext(0)
    }

    fun minimize() {
        scope.launch { drawerState.close() }
        onMinimize()
    }

    val detectedScope = remember(viewModel.openedName, viewModel.grammarsReady) {
        EditorLanguages.scopeFor(viewModel.openedName)
    }
    val highlightActive =
        viewModel.syntaxHighlight && viewModel.grammarsReady && viewModel.loadState is LoadState.Success && viewModel.content.length <= HIGHLIGHT_MAX_CHARS
    val activeLanguage = remember(
        viewModel.activeDocumentId,
        viewModel.languageOverride,
        detectedScope,
        highlightActive,
        viewModel.selectedTheme,
    ) {
        if (highlightActive) {
            EditorLanguages.languageForScope(viewModel.languageOverride ?: detectedScope)
        } else {
            null
        }
    }
    val settings = remember(
        viewModel.selectedTheme,
        viewModel.wordWrap,
        viewModel.lineNumbers,
        viewModel.miniMap,
        viewModel.miniMapBlocks,
        viewModel.invisibleChars,
        viewModel.readOnly,
        viewModel.autoCompletion,
        viewModel.textSizePx,
    ) {
        EditorSettings(
            theme = viewModel.selectedTheme,
            wordWrap = viewModel.wordWrap,
            lineNumbers = viewModel.lineNumbers,
            miniMap = viewModel.miniMap,
            miniMapBlocks = viewModel.miniMapBlocks,
            invisibleChars = viewModel.invisibleChars,
            readOnly = viewModel.readOnly,
            autoCompletion = viewModel.autoCompletion,
            textSizePx = viewModel.textSizePx,
        )
    }
    EditorEffects(
        viewModel = viewModel,
        editorRef = editorRef,
        settings = settings,
        typeface = typefaceToLoad,
        activeLanguage = activeLanguage,
        onReloaded = ::refreshUndoRedo,
    )
    LaunchedEffect(viewModel.activeDocumentId) {
        if (shownDocumentId != viewModel.activeDocumentId) {
            shownDocumentId = viewModel.activeDocumentId
            viewModel.closePreview()
            closeSearch()
        }
    }
    LaunchedEffect(initialSearchQuery) {
        if (initialSearchQuery != null) searchQuery = initialSearchQuery
    }
    LaunchedEffect(searchQuery, viewModel.editorLaidOut) {
        if (viewModel.editorLaidOut && !searchQuery.isNullOrBlank() && !viewModel.readOnly) {
            showSearchPanel = true
        }
    }
    LaunchedEffect(viewModel.readOnly) {
        if (viewModel.readOnly) {
            showSearchPanel = false
            editorRef?.searcher?.stopSearch()
        }
    }
    val shouldFocusEditor =
        editorReadyId == viewModel.activeDocumentId && drawerState.currentValue == DrawerValue.Closed && !showSearchPanel && !showSettings && viewModel.preview == null && searchQuery == null
    val isAutofocusEnabled = viewModel.autoFocus && shouldFocusEditor
    LaunchedEffect(editorRef, isAutofocusEnabled) {
        if (!isAutofocusEnabled) return@LaunchedEffect
        val editor = editorRef ?: return@LaunchedEffect
        fun focus() {
            focusManager.clearFocus(force = true)
            editor.requestFocus()
            if (!viewModel.readOnly) editor.showSoftInput()
        }
        delay(120.milliseconds)
        focus()
        if (!editor.hasFocus()) {
            delay(120.milliseconds)
            focus()
        }
    }
    LaunchedEffect(viewModel.preview) {
        if (viewModel.preview != null) shownPreview = viewModel.preview
    }
    LaunchedEffect(showSettings, viewModel.preview) {
        if (showSettings || viewModel.preview != null) editorRef?.hideSoftInput()
    }
    BackHandler(enabled = !showSettings) {
        when {
            viewModel.preview != null -> viewModel.closePreview()
            !drawerState.isClosed -> scope.launch { drawerState.close() }
            showSearchPanel -> closeSearch()
            viewModel.openDocuments.isEmpty() -> onNavigateUp()
            viewModel.isModified -> {
                leaveAfterClose = true
                viewModel.requestClose(viewModel.activeDocumentId)
            }

            else -> leaveEditor()
        }
    }
    DisposableEffect(editorRef) {
        val editor = editorRef ?: return@DisposableEffect onDispose { }
        fun refreshPosition() {
            val caret = editor.cursor
            cursorLine = caret.leftLine + 1
            cursorColumn = caret.leftColumn + 1
            lineCount = editor.text.lineCount
        }
        refreshPosition()
        val selectionReceipt =
            editor.subscribeEvent(SelectionChangeEvent::class.java) { _, _ -> refreshPosition() }
        val contentReceipt = editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
            refreshPosition()
        }
        onDispose {
            selectionReceipt.unsubscribe()
            contentReceipt.unsubscribe()
        }
    }
    DisposableEffect(editorRef) {
        val editor = editorRef
        viewModel.beforeDocumentSwitch = {
            if (editor != null) {
                viewModel.saveCursorState(viewModel.activeDocumentId, editor)
            }
        }
        onDispose { viewModel.beforeDocumentSwitch = null }
    }
    DialogHost(
        viewModel = viewModel,
        onSaveAndClose = { id ->
            saveDocument(
                id = id,
                onSaved = {
                    if (leaveAfterClose) {
                        leaveEditor()
                    } else {
                        viewModel.close(id)
                        advanceCloseQueue()
                    }
                },
                onSaveError = {
                    leaveAfterClose = false
                    pendingCloseIds.clear()
                },
            )
        },
        onMinimize = ::minimize,
        onDocumentClosed = {
            if (leaveAfterClose) leaveEditor()
            advanceCloseQueue()
        },
        onCloseCancelled = {
            leaveAfterClose = false
            pendingCloseIds.clear()
        },
        onJumpToLine = { line ->
            val editor = editorRef
            if (editor != null) {
                val target = (line - 1).coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
                editor.setSelection(target, 0, true)
            }
        },
    )

    val title = if (noDocuments) "" else buildString {
        if (viewModel.isModified) append("*")
        when {
            viewModel.isNewFile -> append("untitled")
            viewModel.openedName != null -> append(viewModel.openedName)
            else -> append(displayPath?.let(::fileNameOf) ?: "")
        }
    }
    val editorOffset by animateDpAsState(
        targetValue = if (showSettings) (-48).dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "editorPush",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !drawerState.isClosed,
            drawerContent = {
                EditorDrawerSheet(
                    documents = viewModel.openDocuments,
                    pinnedPaths = viewModel.pinnedPaths,
                    activeDocumentId = viewModel.activeDocumentId,
                    onSelect = { id ->
                        scope.launch { drawerState.close() }
                        switchToDocument(id)
                    },
                    onOpenPath = { path ->
                        scope.launch { drawerState.close() }
                        viewModel.open(path, fileNameOf(path))
                    },
                    onNewFile = {
                        scope.launch { drawerState.close() }
                        viewModel.openNewFile()
                    },
                    onSaveAll = {
                        scope.launch { drawerState.close() }
                        saveAllDocuments()
                    },
                    onCloseDocuments = { ids ->
                        scope.launch { drawerState.close() }
                        closeDocuments(ids)
                    },
                    onClose = { id ->
                        leaveAfterClose = false
                        viewModel.requestClose(id)
                    },
                    onMinimize = ::minimize,
                    width = 270.dp,
                )
            },
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.offset(x = editorOffset),
                contentWindowInsets = WindowInsets.ime,
                topBar = {
                    TopBarActions(
                        title = title,
                        cursorLine = cursorLine,
                        cursorColumn = cursorColumn,
                        lineCount = lineCount,
                        editorRef = editorRef,
                        viewModel = viewModel,
                        saveButtonState = saveButtonState,
                        onSave = {
                            saveDocument(viewModel.activeDocumentId, onSaved = {})
                        },
                        onSaveAs = ::saveCopyAsFile,
                        onReload = { viewModel.openReloadDialog() },
                        onFileInfo = {
                            viewModel.openFileInfo(currentText())
                        },
                        onDocumentOptions = viewModel::openDocumentOptions,
                        onPreview = {
                            viewModel.openPreview(currentText())
                        },
                        canUndo = canUndo,
                        canRedo = canRedo,
                        refreshUndoRedo = ::refreshUndoRedo,
                        showSearchPanel = showSearchPanel,
                        onShowSearchPanelChange = { showSearchPanel = it },
                        onShowSettingsChange = { showSettings = it },
                        onOpenDrawer = {
                            editorRef?.hideSoftInput()
                            scope.launch { drawerState.open() }
                        },
                        onCloseFile = {
                            leaveAfterClose = false
                            viewModel.requestClose(viewModel.activeDocumentId)
                        },
                        onShowLanguagePicker = { viewModel.openLanguageDialog(detectedScope) },
                        documents = viewModel.openDocuments,
                        activeDocumentId = viewModel.activeDocumentId,
                        onSelectDocument = ::switchToDocument,
                        onCloseDocument = { id ->
                            leaveAfterClose = false
                            viewModel.requestClose(id)
                        },
                        noDocuments = noDocuments,
                    )
                },
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                ) {
                    val state = viewModel.loadState
                    when {
                        noDocuments -> {
                            EmptyEditor(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { viewModel.openNewFile() })
                            )
                        }

                        state is LoadState.Loading -> {
                            PreloadSurface()
                        }

                        state is LoadState.Error -> {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .weight(1f)
                                    .align(Alignment.CenterHorizontally)
                                    .padding(16.dp),
                            )
                        }

                        state is LoadState.Success -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                if (viewModel.prefsLoaded && viewModel.grammarsSettled) {
                                    AnimatedContent(
                                        targetState = viewModel.activeDocumentId,
                                        transitionSpec = {
                                            val documents = viewModel.openDocuments
                                            val from = documents.indexOfFirst {
                                                it.id == initialState
                                            }
                                            val to = documents.indexOfFirst {
                                                it.id == targetState
                                            }
                                            val direction = if (from in 0..to) 1 else -1
                                            slideInHorizontally(tween(240)) { width ->
                                                direction * width
                                            } togetherWith slideOutHorizontally(
                                                tween(240)
                                            ) { width -> -direction * width }
                                        },
                                        label = "documentSwitch",
                                    ) { shownId ->
                                        AndroidView(
                                            factory = { ctx ->
                                                createCodeEditor(
                                                    context = ctx,
                                                    settings = settings,
                                                    typeface = typefaceToLoad,
                                                    language = activeLanguage,
                                                    initialText = viewModel.content,
                                                    onFirstLayout = viewModel::onEditorLaidOut,
                                                    onContentChange = { editor, event ->
                                                        canUndo.value = editor.canUndo()
                                                        canRedo.value = editor.canRedo()
                                                        viewModel.onContentChanged(event.action)
                                                        linesToScan(editor.text, event)?.let(
                                                            viewModel::onLinesEdited
                                                        )
                                                    },
                                                ).also { editor ->
                                                    viewModel.restoreCursorState(shownId, editor)
                                                    editorRef = editor
                                                    editorReadyId = shownId
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            onRelease = { editor ->
                                                viewModel.saveCursorState(shownId, editor)
                                                if (editorRef === editor) editorRef = null
                                                editor.release()
                                            },
                                        )
                                    }
                                } else {
                                    PreloadSurface()
                                }
                            }
                        }
                    }
                    val symbolBarVisible =
                        viewModel.symbolBar && viewModel.editorLaidOut && !viewModel.readOnly && !noDocuments
                    val searchBarVisible =
                        showSearchPanel && viewModel.editorLaidOut && !viewModel.readOnly
                    val symbolBarState = remember { MutableTransitionState(false) }
                    symbolBarState.targetState = symbolBarVisible
                    val searchBarState = remember { MutableTransitionState(false) }
                    searchBarState.targetState = searchBarVisible
                    val bottomBarsPresent =
                        symbolBarState.currentState || symbolBarState.targetState || searchBarState.currentState || searchBarState.targetState
                    val bottomInsets = bottomBarInsets()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = BarColor,
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (bottomBarsPresent) {
                                        Modifier.windowInsetsPadding(bottomInsets)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            AnimatedVisibility(
                                visibleState = symbolBarState,
                                enter = expandVertically(
                                    animationSpec = tween(durationMillis = 220),
                                    expandFrom = Alignment.Bottom,
                                ),
                                exit = shrinkVertically(
                                    animationSpec = tween(durationMillis = 220),
                                    shrinkTowards = Alignment.Bottom,
                                ),
                            ) {
                                SymbolInputBar(
                                    editor = editorRef,
                                    typeface = typefaceToLoad,
                                )
                            }
                            AnimatedVisibility(
                                visibleState = searchBarState,
                                enter = expandVertically(
                                    animationSpec = tween(durationMillis = 220),
                                    expandFrom = Alignment.Top,
                                ),
                                exit = shrinkVertically(
                                    animationSpec = tween(durationMillis = 220),
                                    shrinkTowards = Alignment.Top,
                                ),
                            ) {
                                CustomSearchBar(
                                    editor = editorRef,
                                    initialQuery = searchQuery,
                                    onClose = ::closeSearch,
                                )
                            }
                        }
                    }
                }
            }
        }

        SlideInOverlay(visible = showSettings) {
            EditorSettingsScreen(
                viewModel = viewModel,
                onNavigateUp = { showSettings = false },
            )
        }

        SlideInOverlay(visible = viewModel.preview != null) {
            shownPreview?.let { preview ->
                MarkdownPreviewScreen(
                    state = preview,
                    onNavigateUp = viewModel::closePreview,
                )
            }
        }
    }
}
