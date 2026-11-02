/*
 * Copyright (c)
 */

package me.zhanghai.android.files.viewer

import androidx.activity.result.ActivityResultLauncher
import java.io.InputStream
import java8.nio.file.Files
import java8.nio.file.Path
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.filelist.FileListActivity
import texteditor.TextEditorHost

object TextEditorHostBridge : TextEditorHost {
    private var createFileLauncher:
        ActivityResultLauncher<Triple<MimeType, String?, Path?>>? = null
    private var pendingSaveAs: CancellableContinuation<Path?>? = null

    override fun save(path: Path, text: String) {
        Files.newBufferedWriter(path, Charsets.UTF_8).use { it.write(text) }
    }

    override fun openRead(path: Path): InputStream = Files.newInputStream(path)

    /**
     * Called by the text editor activity so that a new document is saved through the app's
     * built-in "Save as" file picker.
     */
    fun setCreateFileLauncher(
        launcher: ActivityResultLauncher<Triple<MimeType, String?, Path?>>,
    ) {
        createFileLauncher = launcher
    }

    /** Delivers the picked destination, or `null` when the user cancelled the picker. */
    fun onCreateFileResult(path: Path?) {
        val continuation = pendingSaveAs ?: return
        pendingSaveAs = null
        if (continuation.isActive) continuation.resume(path)
    }

    override suspend fun saveAs(suggestedName: String, initialDirectory: Path?): Path? =
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
            launcher.launch(Triple(MimeType.ANY, suggestedName, initialDirectory))
        }
}
