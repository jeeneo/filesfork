package me.zhanghai.android.filesfork.viewer.text

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zhanghai.android.filesfork.app.AppActivity
import me.zhanghai.android.filesfork.theme.AppTheme
import me.zhanghai.android.filesfork.util.extraPath

object TextEditorInitializer {
    @Volatile
    var themeReady = false
        private set

    var grammarReady by mutableStateOf(false)
        private set

    fun initializeThemes(context: Context) {
        if (themeReady) return
        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(context.assets)
        )
        ThemeManager.initialize(context)
        themeReady = true
    }

    fun initializeGrammars(context: Context) {
        if (grammarReady) return
        GrammarRegistry.getInstance().loadGrammars("textmate/languages/languages.json")
        LanguageRegistry.initialize(context)
        grammarReady = true
    }
}

class TextEditorActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val path = intent.extraPath ?: run { finish(); return }
        TextEditorInitializer.initializeThemes(applicationContext)
        if (!TextEditorInitializer.grammarReady) {
            lifecycleScope.launch(Dispatchers.IO) {
                TextEditorInitializer.initializeGrammars(applicationContext)
            }
        }
        setContent {
            AppTheme {
                TextEditorScreen(path = path, onNavigateUp = { finish() })
            }
        }
    }
}
