/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.provider.archive

import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException
import me.zhanghai.android.libarchive.Archive
import java.util.Locale

fun Path.archiveAddPassword(password: String) {
    this as? ArchivePath ?: throw ProviderMismatchException(toString())
    fileSystem.addPassword(password)
}

val Path.archiveFile: Path
    get() {
        this as? ArchivePath ?: throw ProviderMismatchException(toString())
        return fileSystem.archiveFile
    }

val Path.canModifyArchiveEntries: Boolean
    get() = archiveWriteOptions != null

data class ArchiveWriteOptions(val format: Int, val filter: Int)

val Path.archiveWriteOptions: ArchiveWriteOptions?
    get() {
        val archiveFile = if (isArchivePath) archiveFile else this
        val fileName = archiveFile.fileName?.toString()?.lowercase(Locale.ROOT) ?: return null
        return when {
            fileName.endsWith(".tar.xz") -> ArchiveWriteOptions(
                Archive.FORMAT_TAR, Archive.FILTER_XZ
            )

            fileName.endsWith(".tar.gz") -> ArchiveWriteOptions(
                Archive.FORMAT_TAR, Archive.FILTER_GZIP
            )

            fileName.endsWith(".tar.zst") -> ArchiveWriteOptions(
                Archive.FORMAT_TAR, Archive.FILTER_ZSTD
            )

            fileName.endsWith(".tar") -> ArchiveWriteOptions(
                Archive.FORMAT_TAR, Archive.FILTER_NONE
            )

            fileName.endsWith(".zip") -> ArchiveWriteOptions(
                Archive.FORMAT_ZIP, Archive.FILTER_NONE
            )

            fileName.endsWith(".7z") -> ArchiveWriteOptions(
                Archive.FORMAT_7ZIP, Archive.FILTER_NONE
            )

            else -> null
        }
    }

fun Path.archivePasswords(): List<String> {
    this as? ArchivePath ?: throw ProviderMismatchException(toString())
    return fileSystem.getPasswords()
}

fun Path.archiveRefresh() {
    this as? ArchivePath ?: throw ProviderMismatchException(toString())
    fileSystem.refresh()
}

fun Path.createArchiveRootPath(): Path =
    ArchiveFileSystemProvider.getOrNewFileSystem(this).rootDirectory
