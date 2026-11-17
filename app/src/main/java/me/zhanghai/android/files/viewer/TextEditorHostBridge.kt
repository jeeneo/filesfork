/*
 * Copyright (c)
 */

package me.zhanghai.android.files.viewer

import android.content.ContentResolver
import androidx.activity.result.ActivityResultLauncher
import java.io.IOException
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
import texteditor.isSafeRelativePath

object TextEditorHostBridge : TextEditorHost {
    private var createFileLauncher:
        ActivityResultLauncher<Triple<MimeType, String?, Path?>>? = null
    private var pendingSaveAs: CancellableContinuation<String?>? = null

    override fun save(path: String, bytes: ByteArray) {
        Files.newOutputStream(path.toEditorPath()).use { it.write(bytes) }
    }

    override fun openRead(path: String): InputStream = Files.newInputStream(path.toEditorPath())

    override fun openSibling(path: String, relative: String): InputStream {
        val directory = path.toEditorPath().parent
            ?: throw IOException("No directory for ${path.substringAfterLast('/')}")
        if (!isSafeRelativePath(relative)) throw IOException("Unsafe relative path")
        val target = directory.resolve(relative).normalize()
        if (!target.startsWith(directory.normalize())) {
            throw IOException("Relative path escapes its directory")
        }
        val realDirectory = runCatching { directory.toRealPath() }.getOrNull()
        if (realDirectory != null) {
            val realTarget = runCatching { target.toRealPath() }.getOrNull()
                ?: throw IOException("No such file: $relative")
            if (!realTarget.startsWith(realDirectory)) {
                throw IOException("Relative path escapes its directory")
            }
            return Files.newInputStream(realTarget)
        }
        return Files.newInputStream(target)
    }

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
