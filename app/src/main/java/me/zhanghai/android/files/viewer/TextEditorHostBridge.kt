/*
 * Copyright (c)
 */

package me.zhanghai.android.files.viewer

import android.content.ContentResolver
import androidx.activity.result.ActivityResultLauncher
import java.io.InputStream
import java.net.URI
import java8.nio.file.Files
import java8.nio.file.Path
import java8.nio.file.Paths
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import me.zhanghai.android.files.file.MimeType
import texteditor.TextEditorHost

object TextEditorHostBridge : TextEditorHost {
    private var createFileLauncher:
        ActivityResultLauncher<Triple<MimeType, String?, Path?>>? = null
    private var pendingSaveAs: CancellableContinuation<String?>? = null

    override fun save(path: String, text: String) {
        Files.newBufferedWriter(path.toEditorPath(), Charsets.UTF_8).use { it.write(text) }
    }

    override fun openRead(path: String): InputStream = Files.newInputStream(path.toEditorPath())

    fun setCreateFileLauncher(
        launcher: ActivityResultLauncher<Triple<MimeType, String?, Path?>>,
    ) {
        createFileLauncher = launcher
    }

    fun onCreateFileResult(path: Path?) {
        val continuation = pendingSaveAs ?: return
        pendingSaveAs = null
        if (continuation.isActive) continuation.resume(path?.toEditorPathString())
    }

    override suspend fun saveAs(suggestedName: String, initialDirectory: String?): String? =
        suspendCancellableCoroutine { continuation ->
            val launcher = createFileLauncher
            if (launcher == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            pendingSaveAs = continuation
            continuation.invokeOnCancellation {
                if (pendingSaveAs === continuation) pendingSaveAs = null
            }
            launcher.launch(Triple(MimeType.ANY, suggestedName, initialDirectory?.toEditorPath()))
        }
}

internal fun Path.toEditorPathString(): String {
    val uri = toUri()
    return if (uri.scheme == ContentResolver.SCHEME_FILE) toString() else uri.toString()
}

internal fun String.toEditorPath(): Path =
    runCatching { URI.create(this) }.getOrNull()
        ?.takeIf { it.scheme != null }
        ?.let { Paths.get(it) }
        ?: Paths.get(this)
