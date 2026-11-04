package me.zhanghai.android.files.viewer.text

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java8.nio.file.Path
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.theme.AppTheme
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.viewer.TextEditorHostBridge
import me.zhanghai.android.files.viewer.toEditorPathString
import texteditor.SoraEditorViewModel
import texteditor.TextEditorScreen

class TextEditorActivity : AppActivity() {
    private val viewModel: SoraEditorViewModel by viewModels {
        SoraEditorViewModel.factory(TextEditorHostBridge)
    }
    private var initialSearchQuery by mutableStateOf<String?>(null)

    private val createFileLauncher =
        registerForActivityResult(FileListActivity.CreateFileContract()) { path ->
            TextEditorHostBridge.onCreateFileResult(path)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TextEditorHostBridge.setCreateFileLauncher(createFileLauncher)
        enableEdgeToEdge()
        if (savedInstanceState == null || viewModel.openDocuments.isEmpty()) {
            if (!handleIntent(intent)) {
                finish()
                return
            }
        } else {
            initialSearchQuery = intent.getStringExtra(EXTRA_INITIAL_SEARCH_QUERY)
        }
        setContent {
            AppTheme {
                TextEditorScreen(
                    onNavigateUp = { finish() },
                    onMinimize = { finish() },
                    viewModel = viewModel,
                    initialSearchQuery = initialSearchQuery,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent): Boolean {
        val path = intent.extraPath ?: return false
        val editorPath = path.toEditorPathString()
        initialSearchQuery = intent.getStringExtra(EXTRA_INITIAL_SEARCH_QUERY)
        if (intent.getBooleanExtra(EXTRA_BLANK_FILE, false)) {
            viewModel.openNew(editorPath)
        } else {
            viewModel.open(
                editorPath,
                intent.getStringExtra(EXTRA_OPEN_NAME) ?: path.fileName?.toString()
                ?: editorPath,
            )
        }
        return true
    }

    companion object {
        private const val EXTRA_BLANK_FILE = "me.zhanghai.android.files.extra.BLANK_FILE"
        private const val EXTRA_OPEN_NAME = "me.zhanghai.android.files.extra.OPEN_NAME"
        private const val EXTRA_INITIAL_SEARCH_QUERY =
            "me.zhanghai.android.files.extra.INITIAL_SEARCH_QUERY"

        fun createIntent(
            context: Context,
            directory: Path? = null,
            openPath: Path? = null,
            openName: String? = null,
            initialSearchQuery: String? = null,
        ): Intent =
            Intent(context, TextEditorActivity::class.java).apply {
                if (openPath != null) {
                    extraPath = openPath
                    openName?.let { putExtra(EXTRA_OPEN_NAME, it) }
                    initialSearchQuery?.let { putExtra(EXTRA_INITIAL_SEARCH_QUERY, it) }
                } else if (directory != null) {
                    extraPath = directory
                    putExtra(EXTRA_BLANK_FILE, true)
                }
            }
    }
}
