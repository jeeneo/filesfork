package texteditor.ui.screens.preview

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import texteditor.TextEditorHost
import texteditor.isSafeRelativePath
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale

internal const val PREVIEW_ORIGIN = "https://preview.local"
private const val PREVIEW_HOST = "preview.local"
private const val HTTPS_PORT = 443

internal class PreviewClient(
    private val host: TextEditorHost,
    private val path: String?,
    private val remoteContentEnabled: Boolean,
    private val onPageFinished: ((WebView, String?) -> Unit)? = null,
) : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String?) {
        onPageFinished?.invoke(view, url)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val uri = request.url
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "data", "blob", "about", "javascript" -> null
            "file" -> if (isViewerAsset(uri)) null else blocked()
            "http", "https" -> when {
                isPreviewOrigin(uri) -> serve(uri)
                remoteContentEnabled -> null
                else -> blocked()
            }
            else -> blocked()
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return true
        return !isPreviewOrigin(uri) && !isViewerAsset(uri)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !isPreviewOrigin(request.url) && !isViewerAsset(request.url)

    private fun serve(uri: Uri): WebResourceResponse {
        val documentPath = path ?: return notFound()
        val relative = relativePathOf(uri)?.takeIf(::isSafeRelativePath) ?: return notFound()
        val stream = openSibling(documentPath, relative) ?: return notFound()
        return WebResourceResponse(
            mimeTypeOf(relative),
            "utf-8",
            200,
            "OK",
            mapOf("Cache-Control" to "no-store"),
            stream,
        )
    }

    private fun openSibling(path: String, relative: String): InputStream? = runCatching {
        host.openSibling(path, relative)
    }.getOrNull()

    private fun relativePathOf(uri: Uri): String? {
        val segments = uri.pathSegments.orEmpty().filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        if (segments.any { it == "." || it == ".." }) return null
        if (segments.any { segment -> segment.any { it == '/' || it == '\\' || it == '\u0000' } }) {
            return null
        }
        return segments.joinToString("/")
    }

    private fun isPreviewOrigin(uri: Uri): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
                uri.port.let { it == -1 || it == HTTPS_PORT } &&
                uri.host.equals(PREVIEW_HOST, ignoreCase = true)

    private fun isViewerAsset(uri: Uri): Boolean {
        if (!uri.scheme.equals("file", ignoreCase = true)) return false
        val segments = uri.pathSegments.orEmpty()
        if (segments.size < 2) return false
        if (segments[0] != "android_asset" || segments[1] != "viewer") return false
        return segments.none { segment ->
            segment == ".." || segment.any { it == '\\' || it == '\u0000' }
        }
    }

    private fun blocked(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        403,
        "Forbidden",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
    )

    private fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        404,
        "Not Found",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
    )
}

private val PREVIEW_MIME_TYPES = mapOf(
    "css" to "text/css",
    "html" to "text/html",
    "htm" to "text/html",
    "js" to "application/javascript",
    "mjs" to "application/javascript",
    "json" to "application/json",
    "txt" to "text/plain",
    "xml" to "application/xml",
    "svg" to "image/svg+xml",
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "ico" to "image/x-icon",
    "woff" to "font/woff",
    "woff2" to "font/woff2",
    "ttf" to "font/ttf",
    "otf" to "font/otf",
    "mp4" to "video/mp4",
    "webm" to "video/webm",
    "mp3" to "audio/mpeg",
    "wasm" to "application/wasm",
)

private fun mimeTypeOf(relative: String): String {
    val name = relative.substringAfterLast('/')
    val extension = if ('.' in name) name.substringAfterLast('.').lowercase(Locale.ROOT) else ""
    return PREVIEW_MIME_TYPES[extension] ?: "application/octet-stream"
}
