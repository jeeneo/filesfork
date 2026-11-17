package texteditor

import java.io.IOException
import java.io.InputStream

fun interface TextEditorHost {
    fun save(path: String, bytes: ByteArray)

    fun openRead(path: String): InputStream =
        throw IOException("No read host installed for ${fileNameOf(path)}")

    fun openSibling(path: String, relative: String): InputStream {
        val parent = parentOf(path) ?: throw IOException("No directory for ${fileNameOf(path)}")
        if (!isSafeRelativePath(relative)) throw IOException("Unsafe relative path")
        return openRead("$parent/$relative")
    }

    suspend fun saveAs(suggestedName: String, initialDirectory: String?): String? = null
}

fun fileNameOf(path: String): String = path.trimEnd('/').substringAfterLast('/')

fun parentOf(path: String): String? =
    path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { null }

fun isSafeRelativePath(relative: String): Boolean {
    if (relative.isEmpty() || relative.startsWith('/')) return false
    return relative.split('/').all { segment ->
        segment.isNotEmpty() && segment != "." && segment != ".." &&
                segment.none { it == '/' || it == '\\' || it == '\u0000' }
    }
}
