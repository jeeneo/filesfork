package me.zhanghai.android.files.viewer.text

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import java8.nio.file.Path
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.theme.AppTheme
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.viewer.TextEditorHostBridge
import texteditor.EditorPreloader
import texteditor.SoraEditorViewModel
import texteditor.TextEditorScreen

class TextEditorActivity : AppActivity() {
    private val createFileLauncher =
        registerForActivityResult(FileListActivity.CreateFileContract()) { path ->
            TextEditorHostBridge.onCreateFileResult(path)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TextEditorHostBridge.setCreateFileLauncher(createFileLauncher)
        enableEdgeToEdge()
        val path = intent.extraPath ?: run {
            finish()
            return
        }
        val isBlankFile = intent.getBooleanExtra(EXTRA_BLANK_FILE, false)
        val openName = intent.getStringExtra(EXTRA_OPEN_NAME)
        val initialSearchQuery = intent.getStringExtra(EXTRA_INITIAL_SEARCH_QUERY)
        EditorPreloader.warm(applicationContext)
        setContent {
            AppTheme {
                val viewModel: SoraEditorViewModel = viewModel(
                    factory = SoraEditorViewModel.factory(TextEditorHostBridge),
                )
                LaunchedEffect(path, isBlankFile) {
                    if (isBlankFile) {
                        viewModel.openNew(path)
                    } else {
                        viewModel.open(
                            path,
                            openName ?: path.fileName?.toString() ?: path.toString(),
                        )
                    }
                }
                TextEditorScreen(
                    onNavigateUp = { finish() },
                    viewModel = viewModel,
                    initialSearchQuery = initialSearchQuery,
                )
            }
        }
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
