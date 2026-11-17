package texteditor.server

// SPDX-License-Identifier: GPL-3.0-or-later

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import texteditor.server.data.RefreshSource
import texteditor.server.data.RefreshType
import java.io.File

/** Keeps files opened in the editor and the web editor served by [ServerService] in sync. */
object ServerFileSync {
    fun externalFileChanges(context: Context): Flow<String> {
        val graph = ServerGraphProvider.get(context)
        return graph.registry.refresh
            .filter { signal ->
                signal.type == RefreshType.FILE && signal.sourceId == RefreshSource.WEB_UI
            }
            .mapNotNull { signal ->
                runCatching { graph.pathStore.absolutePath(signal.path).path }.getOrNull()
            }
            .flowOn(Dispatchers.IO)
    }

    /** Tells connected web editors that [path] changed; ignores files outside the served folder. */
    fun notifySaved(
        context: Context,
        path: String,
    ) {
        runCatching {
            val graph = ServerGraphProvider.get(context)
            val relativePath = graph.pathStore.relativePath(File(path))
            graph.registry.emitRefresh(RefreshType.FILE, relativePath, RefreshSource.APP)
        }
    }
}
