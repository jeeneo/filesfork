package me.zhanghai.android.filesfork.filejob

import java8.nio.file.Path
import me.zhanghai.android.filesfork.provider.common.exists
import me.zhanghai.android.filesfork.provider.common.newInputStream
import java.io.IOException
import java.security.MessageDigest

object ArchiveOpenCacheRepository {
    private val lock = Any()
    private val pendingFiles = mutableListOf<PendingFile>()

    @Throws(IOException::class)
    fun add(archiveFile: Path, archiveEntry: Path, cacheFile: Path, passwords: List<String>) {
        val hash = cacheFile.sha256()
        synchronized(lock) {
            pendingFiles.removeAll { it.cacheFile == cacheFile }
            pendingFiles += PendingFile(archiveFile, archiveEntry, cacheFile, hash, passwords)
        }
    }

    @Throws(IOException::class)
    fun takeModifiedFilesForArchive(archiveFile: Path): List<PendingFile> {
        val files = synchronized(lock) {
            pendingFiles.filter { it.archiveFile == archiveFile }
        }
        val modifiedFiles = mutableListOf<PendingFile>()
        for (file in files) {
            val isModified = file.cacheFile.exists() && !file.cacheFile.sha256()
                .contentEquals(file.sha256)
            synchronized(lock) {
                pendingFiles.remove(file)
            }
            if (isModified) {
                modifiedFiles += file
            }
        }
        return modifiedFiles
    }

    private fun Path.sha256(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        newInputStream().use { inputStream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val size = inputStream.read(buffer)
                if (size == -1) {
                    break
                }
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest()
    }

    data class PendingFile(
        val archiveFile: Path,
        val archiveEntry: Path,
        val cacheFile: Path,
        val sha256: ByteArray,
        val passwords: List<String>
    )
}
