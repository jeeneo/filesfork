package me.zhanghai.android.filesfork.viewer.text

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.filesfork.app.AppActivity
import me.zhanghai.android.filesfork.app.application
import me.zhanghai.android.filesfork.theme.AppTheme
import me.zhanghai.android.filesfork.util.extraPath

object TextEditorInitializer {
    @Volatile
    private var done = false

    suspend fun initThemeAndPrefs(context: Context) = withContext(Dispatchers.IO) {
        if (done) return@withContext
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.assets))
        ThemeRegistry.initialize(context)
        done = true
    }

    suspend fun initGrammars(context: Context) = withContext(Dispatchers.IO) {
        GrammarRegistry.getInstance().loadGrammars("textmate/languages/languages.json")
        LanguageRegistry.initialize(context)
    }
}

class TextEditorActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val path = intent.extraPath ?: run { finish(); return }
        val isBlankFile = intent.getBooleanExtra(EXTRA_BLANK_FILE, false)
        setContent {
            AppTheme {
                val viewModel: TextEditorViewModel = viewModel()
                if (isBlankFile) {
                    LaunchedEffect(path) { viewModel.initializeNewFile(path) }
                    TextEditorScreen(
                        path = null,
                        newFileDirectory = path,
                        onNavigateUp = { finish() }
                    )
                } else {
                    LaunchedEffect(path) { viewModel.initialize(path) }
                    TextEditorScreen(path = path, onNavigateUp = { finish() })
                }
            }
        }
    }

    companion object {
        private const val EXTRA_BLANK_FILE =
            "me.zhanghai.android.filesfork.extra.BLANK_FILE"

        fun createBlankFileIntent(directory: Path): Intent =
            Intent(application, TextEditorActivity::class.java)
                .apply {
                    extraPath = directory
                    putExtra(EXTRA_BLANK_FILE, true)
                }
    }
}
