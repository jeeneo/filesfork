package texteditor

import java.io.IOException
import java.io.InputStream

fun interface TextEditorHost {
    fun save(path: String, bytes: ByteArray)

    fun openRead(path: String): InputStream =
        throw IOException("No read host installed for ${fileNameOf(path)}")

    suspend fun saveAs(suggestedName: String, initialDirectory: String?): String? = null
}

fun fileNameOf(path: String): String = path.trimEnd('/').substringAfterLast('/')

fun parentOf(path: String): String? =
    path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { null }
