package texteditor.server

// SPDX-License-Identifier: GPL-3.0-or-later

import android.app.Application
import android.content.Context
import texteditor.server.data.NetworkInfoProvider
import texteditor.server.data.Registry
import texteditor.server.data.ServerSettingsRepository
import texteditor.server.http.PathStore
import java.io.File

data class RouteContext(
    val appContext: Context,
    val registry: Registry,
    val appName: String,
    val uiStrings: Map<String, String>,
    val templateHtml: String,
    val stylesheetCss: String,
    val javascriptJS: String,
    val editorWebAssets: Map<String, String>,
    val pathStore: PathStore,
)

class ServerGraph(
    appContext: Context,
    rootProvider: () -> File = { appContext.filesDir },
) {
    private val appContext: Application = appContext.applicationContext as Application
    val registry = Registry()
    val settingsRepository = ServerSettingsRepository(appContext)
    val networkInfoProvider = NetworkInfoProvider()
    val pathStore = PathStore(rootProvider)
    val uiStrings: Map<String, String> by lazy { UiStrings.all(appContext) }
    val templateHtml = readAsset("web/template.html")
    val stylesheetCss = readAsset("web/stylesheet.css")
    val javascriptJS = readAsset("web/script.js")
    val editorWebAssets: Map<String, String> =
        listOf("markdown.js", "highlight.min.js", "highlight.css", "highlight-plugin.js")
            .associateWith { readAsset("editor/$it") }

    fun buildRouteContext(context: Context): RouteContext = RouteContext(
        appContext = context,
        registry = registry,
        appName = context.applicationInfo.loadLabel(context.packageManager).toString(),
        uiStrings = uiStrings,
        templateHtml = templateHtml,
        stylesheetCss = stylesheetCss,
        javascriptJS = javascriptJS,
        editorWebAssets = editorWebAssets,
        pathStore = pathStore,
    )

    private fun readAsset(path: String): String =
        appContext.assets.open(path).bufferedReader().use { it.readText() }
}
