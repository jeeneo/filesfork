package texteditor.ui.screens.preview

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
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
import texteditor.data.PRELOAD_BG_DARK
import texteditor.data.registry.FontRegistry
import ui.SimpleShadow

data class PreviewState(
    val markdown: String,
    val title: String,
    val baseUrl: String?,
    val monoFontId: String,
)

@Composable
fun MarkdownPreviewScreen(
    state: PreviewState,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    // Injected after the page loads; null while the font file is still being read.
    val monoFontCss by produceState<String?>(initialValue = null, state.monoFontId) {
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
        color = Color(PRELOAD_BG_DARK),
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
            Box(modifier = Modifier.fillMaxSize()) {
                monoFontCss?.let { css ->
                    AndroidView(
                        factory = { context ->
                            createPreviewWebView(
                                context,
                                state.markdown,
                                state.title,
                                state.baseUrl,
                                css,
                            )
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
    markdown: String,
    title: String,
    baseUrl: String?,
    monoFontCss: String,
): WebView {
    val webView = WebView(context)
    val viewer = viewerUrlFor(webView.settings.userAgentString)
    webView.setBackgroundColor(PREVIEW_BACKGROUND)
    webView.settings.javaScriptEnabled = true
    webView.settings.allowFileAccess = true
    webView.settings.allowContentAccess = false
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            if (monoFontCss.isNotEmpty()) {
                view.evaluateJavascript(
                    "var s=document.createElement('style');" +
                            "s.textContent=${jsString(monoFontCss)};" +
                            "document.head.appendChild(s);",
                    null,
                )
            }
            view.evaluateJavascript(
                "window.previewMarkdown(${jsString(markdown)}, ${jsString(title)}, " +
                        "${jsString(baseUrl)})",
                null,
            )
        }
    }
    webView.loadUrl(viewer)
    return webView
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
private const val COMPAT_BELOW_MAJOR = 66
private val CHROME_VERSION = Regex("""Chrome/(\d+)""")
