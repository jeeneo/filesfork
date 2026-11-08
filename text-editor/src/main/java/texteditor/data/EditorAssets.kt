package texteditor.data

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver

internal object EditorAssets {
    private var registered = false

    @Synchronized
    fun ensure(context: Context) {
        if (registered) return
        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(context.applicationContext.assets),
        )
        registered = true
    }
}
