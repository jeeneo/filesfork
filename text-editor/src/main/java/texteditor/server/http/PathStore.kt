package texteditor.server.http

import android.webkit.MimeTypeMap
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

enum class PathFailure {
    INVALID,
    ACCESS,
    NOT_FOUND,
    CONFLICT,
    IO,
}

class PathStoreException(
    val failure: PathFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class PathEntry(
    val name: String,
    val type: String,
    val size: Long = 0,
    val mtime: Long = 0,
    val editable: Boolean = false,
    val markdown: Boolean = false,
)

data class PathUpload(
    val path: String,
    val bytes: Long,
)

class PathStore(
    private val rootProvider: () -> File,
) {
    private val mutationMutex = Mutex()
    private val markdownExtensions = setOf(
        "md", "markdown", "mdx", "mkd", "mkdn", "mdown", "markdn",
    )
    private val markdownMimeTypes = setOf("text/markdown", "text/x-markdown")

    private fun resolveRoot(): File {
        val configured = rootProvider()
        val root =
            try {
                configured.canonicalFile
            } catch (e: IOException) {
                throw ioFailure("Unable to resolve storage root", e)
            } catch (e: SecurityException) {
                throw accessFailure("Unable to resolve storage root", e)
            }
        if (!root.exists() && !root.mkdirs()) {
            throw ioFailure("Unable to create storage root")
        }
        if (!root.isDirectory) throw notFound("Storage root is not a directory")
        return root
    }

    private fun root(): File = resolveRoot()

    fun relativePath(absolute: File): String {
        val root = root()
        val rootPath = root.path
        val targetPath = absolute.canonicalFile.path
        if (targetPath == rootPath) return ""
        val prefix = if (rootPath.endsWith(File.separator)) rootPath else rootPath + File.separator
        if (!targetPath.startsWith(prefix)) throw accessFailure("Path escapes storage root")
        return targetPath.substring(prefix.length)
    }

    fun absolutePath(relativePath: String): File =
        resolve(root(), requireNonEmptyPath(relativePath))

    suspend fun list(relativePath: String): List<PathEntry> =
        withContext(Dispatchers.IO) {
            val root = root()
            val directory = resolve(root, parseRelativePath(relativePath))
            if (!directory.isDirectory) {
                throw notFound("Directory not found")
            }
            val children = directory.listFiles() ?: throw ioFailure("Unable to list directory")
            children
                .mapNotNull { child ->
                    if (child.name.startsWith(".")) return@mapNotNull null
                    if (!isWithin(root, child)) return@mapNotNull null
                    val isDirectory = runCatching { child.isDirectory }.getOrDefault(false)
                    val isFile = runCatching { child.isFile }.getOrDefault(false)
                    if (!isDirectory && !isFile) return@mapNotNull null
                    if (isDirectory) {
                        PathEntry(name = child.name, type = "dir")
                    } else {
                        PathEntry(
                            name = child.name,
                            type = "file",
                            size = child.length(),
                            mtime = child.lastModified() / 1000,
                            editable = true,
                            markdown = isMarkdownFileName(child.name),
                        )
                    }
                }.sortedWith(
                    compareBy<PathEntry> { it.type != "dir" }.thenBy { it.name.lowercase(Locale.ROOT) },
                )
        }

    fun supportsMarkdownPreview(relativePath: String): Boolean =
        isMarkdownFileName(requireNonEmptyPath(relativePath).last())

    suspend fun read(relativePath: String): String =
        withContext(Dispatchers.IO) {
            val root = root()
            val file = resolve(root, requireNonEmptyPath(relativePath))
            if (!file.isFile) throw notFound("File not found")
            try {
                file.reader(Charsets.UTF_8).use { it.readText() }
            } catch (e: SecurityException) {
                throw accessFailure("Unable to read file", e)
            } catch (e: IOException) {
                throw ioFailure("Unable to read file", e)
            }
        }

    suspend fun write(
        relativePath: String,
        content: String,
    ): Long =
        mutate {
            val root = root()
            val file = resolve(root, requireNonEmptyPath(relativePath))
            if (file.exists() && file.isDirectory) {
                throw conflict("A folder already exists at that path")
            }
            val parent = file.parentFile ?: throw ioFailure("Unable to resolve parent directory")
            if (!parent.exists() && !parent.mkdirs()) {
                throw ioFailure("Unable to create parent directory")
            }
            if (!parent.isDirectory) throw notFound("Parent directory not found")
            val temporary = File.createTempFile(".mdserver-", ".tmp", parent)
            try {
                temporary.writeText(content, Charsets.UTF_8)
                replaceFile(temporary, file)
            } catch (e: SecurityException) {
                throw accessFailure("Unable to write file", e)
            } catch (e: IOException) {
                throw ioFailure("Unable to write file", e)
            } finally {
                if (temporary.exists()) temporary.delete()
            }
            content.toByteArray(Charsets.UTF_8).size.toLong()
        }

    suspend fun upload(
        relativeDirectory: String,
        rawName: String,
        channel: ByteReadChannel,
    ): PathUpload =
        mutate {
            val root = root()
            val parentSegments = parseRelativePath(relativeDirectory)
            val parent = resolve(root, parentSegments)
            if (!parent.isDirectory) throw notFound("Parent directory not found")
            val name = sanitizeName(rawName)
            val targetSegments = parentSegments + name
            val target = resolve(root, targetSegments)
            if (target.exists()) throw conflict("A file or folder with that name already exists")
            var temporary: File? = null
            try {
                temporary = File.createTempFile(".mdserver-", ".upload", parent)
                val bytes = channel.copyAndClose(temporary.writeChannel())
                if (target.exists()) throw conflict("A file or folder with that name already exists")
                replaceFile(temporary, target)
                PathUpload(targetSegments.joinToString("/"), bytes)
            } catch (e: SecurityException) {
                throw accessFailure("Unable to upload file", e)
            } catch (e: IOException) {
                throw ioFailure("Unable to upload file", e)
            } finally {
                temporary?.takeIf { it.exists() }?.delete()
            }
        }

    suspend fun createFile(
        relativeDirectory: String,
        rawName: String,
    ) = mutate {
        val root = root()
        val parentSegments = parseRelativePath(relativeDirectory)
        val parent = resolve(root, parentSegments)
        if (!parent.isDirectory) throw notFound("Parent directory not found")
        val name = sanitizeName(rawName)
        val target = resolve(root, parentSegments + name)
        if (target.exists()) throw conflict("A file or folder with that name already exists")
        try {
            if (!target.createNewFile()) {
                throw conflict("A file or folder with that name already exists")
            }
        } catch (e: SecurityException) {
            throw accessFailure("Unable to create file", e)
        } catch (e: IOException) {
            throw ioFailure("Unable to create file", e)
        }
    }

    suspend fun createDirectory(
        relativeDirectory: String,
        rawName: String,
    ) = mutate {
        val root = root()
        val parentSegments = parseRelativePath(relativeDirectory)
        val parent = resolve(root, parentSegments)
        if (!parent.isDirectory) throw notFound("Parent directory not found")
        val name = sanitizeName(rawName)
        val target = resolve(root, parentSegments + name)
        if (target.exists()) throw conflict("A file or folder with that name already exists")
        try {
            if (!target.mkdir()) {
                throw conflict("A file or folder with that name already exists")
            }
        } catch (e: SecurityException) {
            throw accessFailure("Unable to create folder", e)
        } catch (e: IOException) {
            throw ioFailure("Unable to create folder", e)
        }
    }

    suspend fun delete(relativePath: String) =
        mutate {
            val root = root()
            val segments = requireNonEmptyPath(relativePath)
            val target = resolve(root, segments)
            if (!target.exists()) throw notFound("Not found")
            try {
                deleteRecursively(root, target, mutableSetOf(root.path))
            } catch (e: SecurityException) {
                throw accessFailure("Unable to delete path", e)
            } catch (e: IOException) {
                throw ioFailure("Unable to delete path", e)
            }
        }

    suspend fun rename(
        relativePath: String,
        rawName: String,
    ): String =
        mutate {
            val root = root()
            val segments = requireNonEmptyPath(relativePath)
            val source = resolve(root, segments)
            if (!source.exists()) throw notFound("Not found")
            val name = sanitizeName(rawName)
            val targetSegments = segments.dropLast(1) + name
            val target = resolve(root, targetSegments)
            if (target.exists()) throw conflict("A file or folder with that name already exists")
            try {
                if (!source.renameTo(target)) throw ioFailure("Unable to rename path")
            } catch (e: SecurityException) {
                throw accessFailure("Unable to rename path", e)
            }
            targetSegments.joinToString("/")
        }

    private suspend fun <T> mutate(block: suspend () -> T): T =
        mutationMutex.withLock {
            withContext(Dispatchers.IO) { block() }
        }

    private fun resolve(
        root: File,
        segments: List<String>,
    ): File {
        var candidate = root
        for (segment in segments) candidate = File(candidate, segment)
        if (!isWithin(root, candidate)) throw accessFailure("Path escapes storage root")
        return candidate
    }

    private fun parseRelativePath(value: String): List<String> {
        val normalized = value.trim().replace('\\', '/')
        val stripped = normalized.trim('/')
        if (stripped.isEmpty()) return emptyList()
        val segments = stripped.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." || it.contains('\u0000') }) {
            throw invalidFailure("Invalid path")
        }
        return segments
    }

    private fun requireNonEmptyPath(value: String): List<String> {
        val segments = parseRelativePath(value)
        if (segments.isEmpty()) throw invalidFailure("Missing path")
        return segments
    }

    private fun sanitizeName(value: String): String {
        val name =
            value
                .trim()
                .replace("/", "")
                .replace("\\", "")
                .filter { it.code > 0x1f }
        if (name.isEmpty() || name == "." || name == "..") {
            throw invalidFailure("Invalid name")
        }
        return name
    }

    private fun isMarkdownFileName(name: String): Boolean {
        val extension = extensionOf(name)
        return extension in markdownExtensions || mimeTypeFor(extension) in markdownMimeTypes
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun mimeTypeFor(extension: String): String? =
        runCatching {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }.getOrNull()

    private fun isSymbolicLink(file: File): Boolean =
        file.canonicalFile.path != file.absoluteFile.path

    private fun isWithin(
        root: File,
        candidate: File,
    ): Boolean {
        val rootPath = root.path
        val candidatePath = candidate.path
        val prefix = if (rootPath.endsWith(File.separator)) rootPath else rootPath + File.separator
        return candidatePath == rootPath || candidatePath.startsWith(prefix)
    }

    private fun replaceFile(
        source: File,
        target: File,
    ) {
        if (source.renameTo(target)) return
        if (target.exists() && !target.delete()) {
            throw IOException("Unable to replace file")
        }
        if (!source.renameTo(target)) throw IOException("Unable to replace file")
    }

    private fun deleteRecursively(
        root: File,
        file: File,
        seen: MutableSet<String>,
    ) {
        val canonical =
            try {
                file.canonicalFile
            } catch (e: IOException) {
                throw ioFailure("Unable to resolve path", e)
            }
        if (!isWithin(root, canonical)) throw accessFailure("Path escapes storage root")
        if (seen.add(canonical.path) && file.isDirectory && !isSymbolicLink(file)) {
            val children = file.listFiles() ?: throw ioFailure("Unable to list folder")
            children.forEach { deleteRecursively(root, it, seen) }
        }
        if (!file.delete()) throw ioFailure("Unable to delete path")
    }

    private fun invalidFailure(message: String) = PathStoreException(PathFailure.INVALID, message)

    private fun accessFailure(
        message: String,
        cause: Throwable? = null,
    ) = PathStoreException(PathFailure.ACCESS, message, cause)

    private fun notFound(message: String) = PathStoreException(PathFailure.NOT_FOUND, message)

    private fun conflict(message: String) = PathStoreException(PathFailure.CONFLICT, message)

    private fun ioFailure(
        message: String,
        cause: Throwable? = null,
    ) = PathStoreException(PathFailure.IO, message, cause)
}
