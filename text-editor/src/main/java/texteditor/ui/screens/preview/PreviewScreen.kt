package texteditor.ui.screens.preview

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import texteditor.TextEditorHost
import texteditor.data.PRELOAD_BG_DARK
import texteditor.data.registry.FontRegistry
import ui.SimpleShadow

data class PreviewState(
    val document: PreviewDocument,
    val path: String?,
    val title: String,
    val monoFontId: String,
    val javaScriptEnabled: Boolean,
    val remoteContentEnabled: Boolean,
)

@Composable
fun PreviewScreen(
    state: PreviewState,
    host: TextEditorHost,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val isMarkdown = state.document is PreviewDocument.Markdown
    val monoFontCss by produceState<String?>(initialValue = null, state.monoFontId, isMarkdown) {
        if (!isMarkdown) {
            value = ""
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            FontRegistry.readFontData(context, state.monoFontId)?.let { font ->
                val base64 = Base64.encodeToString(font.bytes, Base64.NO_WRAP)
                "@font-face{font-family:'EditorCode';src:url(data:${font.mimeType};base64,$base64)" +
                        " format('${font.format}');font-display:swap}" +
                        "#preview code{font-family:'EditorCode',monospace}"
            } ?: ""
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isMarkdown) Color(PRELOAD_BG_DARK) else Color.White,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                }
            }
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {
                monoFontCss?.let { css ->
                    AndroidView(
                        factory = { context ->
                            createPreviewWebView(context, host, state, css)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        onRelease = { webView ->
                            webView.stopLoading()
                            webView.destroy()
                        },
                    )
                }
                SimpleShadow()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createPreviewWebView(
    context: Context,
    host: TextEditorHost,
    state: PreviewState,
    monoFontCss: String,
): WebView {
    val webView = WebView(context)
    webView.settings.allowContentAccess = false
    when (val document = state.document) {
        is PreviewDocument.Markdown -> {
            webView.setBackgroundColor(PREVIEW_BACKGROUND)
            webView.settings.javaScriptEnabled = true
            webView.settings.allowFileAccess = true
            webView.webViewClient = PreviewClient(
                host = host,
                path = state.path,
                remoteContentEnabled = state.remoteContentEnabled,
            ) { view, _ ->
                injectMarkdown(view, document, state, monoFontCss)
            }
            webView.loadUrl(viewerUrlFor(webView.settings.userAgentString))
        }

        is PreviewDocument.Html -> {
            webView.setBackgroundColor(HTML_BACKGROUND)
            webView.settings.javaScriptEnabled = state.javaScriptEnabled
            webView.settings.allowFileAccess = false
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
            webView.settings.setSupportZoom(true)
            webView.settings.builtInZoomControls = true
            webView.settings.displayZoomControls = false
            webView.webViewClient = PreviewClient(
                host = host,
                path = state.path,
                remoteContentEnabled = state.remoteContentEnabled,
            )
            webView.loadDataWithBaseURL(
                "$PREVIEW_ORIGIN/",
                document.source,
                "text/html",
                "utf-8",
                null,
            )
        }
    }
    return webView
}

private fun injectMarkdown(
    view: WebView,
    document: PreviewDocument.Markdown,
    state: PreviewState,
    monoFontCss: String,
) {
    if (monoFontCss.isNotEmpty()) {
        view.evaluateJavascript(
            "var s=document.createElement('style');" +
                    "s.textContent=${jsString(monoFontCss)};" +
                    "document.head.appendChild(s);",
            null,
        )
    }
    val baseUrl = state.path?.let { "$PREVIEW_ORIGIN/" }
    view.evaluateJavascript(
        "window.previewMarkdown(${jsString(document.source)}, ${jsString(state.title)}, " +
                "${jsString(baseUrl)})",
        null,
    )
}

private fun viewerUrlFor(userAgent: String?): String {
    val major = userAgent?.let { CHROME_VERSION.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        ?: return COMPAT_VIEWER_URL
    return if (major < COMPAT_BELOW_MAJOR) COMPAT_VIEWER_URL else VIEWER_URL
}

private fun jsString(value: String?): String =
    JSONObject.quote(value ?: "")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")

private const val VIEWER_URL = "file:///android_asset/viewer/index.html"
private const val COMPAT_VIEWER_URL = "file:///android_asset/viewer/compat/index.html"
private const val PREVIEW_BACKGROUND = 0xFF1E1E1E.toInt()

/** A document without its own background should look like it does in a browser, not like us. */
private const val HTML_BACKGROUND = 0xFFFFFFFF.toInt()
private const val COMPAT_BELOW_MAJOR = 66
private val CHROME_VERSION = Regex("""Chrome/(\d+)""")
