package texteditor.server

// SPDX-License-Identifier: GPL-3.0-or-later

import android.content.Context
import java.io.File
import texteditor.server.data.ServerSettingsRepository

object ServerGraphProvider {
    private val lock = Any()

    @Volatile
    private var instance: ServerGraph? = null

    fun get(context: Context): ServerGraph = get(context, defaultRootProvider(context))

    private fun defaultRootProvider(context: Context): () -> File {
        val appContext = context.applicationContext
        return {
            val rootPath = ServerSettingsRepository(appContext).rootPath()
            File(rootPath ?: appContext.filesDir.absolutePath)
        }
    }

    private fun get(
        context: Context,
        rootProvider: () -> File,
    ): ServerGraph {
        instance?.let { return it }
        synchronized(lock) {
            instance?.let { return it }
            return ServerGraph(context.applicationContext, rootProvider).also { instance = it }
        }
    }
}
