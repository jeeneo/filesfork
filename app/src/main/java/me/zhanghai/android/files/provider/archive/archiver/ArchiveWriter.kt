/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.provider.archive.archiver

import java8.nio.channels.SeekableByteChannel
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import me.zhanghai.android.filesfork.provider.common.PosixFileAttributes
import me.zhanghai.android.filesfork.provider.common.PosixFileMode
import me.zhanghai.android.filesfork.provider.common.PosixFileType
import me.zhanghai.android.filesfork.provider.common.copyTo
import me.zhanghai.android.filesfork.provider.common.getLastModifiedTime
import me.zhanghai.android.filesfork.provider.common.newInputStream
import me.zhanghai.android.filesfork.provider.common.readAttributes
import me.zhanghai.android.filesfork.provider.common.readSymbolicLinkByteString
import me.zhanghai.android.filesfork.provider.common.size
import me.zhanghai.android.filesfork.filelist.CompressionTarget
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

class ArchiveWriter @Throws(IOException::class) constructor(
    channel: SeekableByteChannel,
    format: Int,
    filter: Int,
    compressionTarget: CompressionTarget,
    password: String?,
    compressionLevel: Int? = null
) : Closeable {
    private val archive =
        WriteArchive(channel, format, filter, compressionTarget, password, compressionLevel)

    @Throws(IOException::class)
    fun write(file: Path, entryName: Path, intervalMillis: Long, listener: ((Long) -> Unit)?) {
        val name = entryName.toString()
        val lastModifiedTime = file.getLastModifiedTime(LinkOption.NOFOLLOW_LINKS)
        val lastAccessTime = null
        val creationTime = null
        val attributes = file.readAttributes(
            BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS
        )
        val type = when {
            attributes is PosixFileAttributes -> attributes.type()
            attributes.isDirectory -> PosixFileType.DIRECTORY
            attributes.isSymbolicLink -> PosixFileType.SYMBOLIC_LINK
            else -> PosixFileType.REGULAR_FILE
        }
        val size = file.size(LinkOption.NOFOLLOW_LINKS)
        val posixAttributes = attributes as? PosixFileAttributes
        val owner = posixAttributes?.owner()
        val group = posixAttributes?.group()
        val mode = posixAttributes?.mode() ?: when {
            attributes.isDirectory -> PosixFileMode.DIRECTORY_DEFAULT
            attributes.isSymbolicLink -> PosixFileMode.SYMBOLIC_LINK_DEFAULT
            else -> PosixFileMode.FILE_DEFAULT
        }
        val symbolicLinkTarget = if (attributes.isSymbolicLink) {
            file.readSymbolicLinkByteString().toString()
        } else {
            null
        }
        archive.Entry(
            name, lastModifiedTime, lastAccessTime, creationTime, type, size, owner, group, mode,
            symbolicLinkTarget
        ).use { archive.writeEntry(it) }
        if (type == PosixFileType.REGULAR_FILE) {
            file.newInputStream(LinkOption.NOFOLLOW_LINKS).use { inputStream ->
                inputStream.copyTo(archive.newDataOutputStream(), intervalMillis, listener)
            }
        } else {
            listener?.invoke(attributes.size())
        }
    }

    @Throws(IOException::class)
    fun write(entry: ReadArchive.Entry, inputStream: InputStream?, listener: ((Long) -> Unit)?) {
        archive.Entry(
            entry.name, entry.lastModifiedTime, entry.lastAccessTime, entry.creationTime,
            entry.type, entry.size, entry.owner, entry.group, entry.mode, entry.symbolicLinkTarget
        ).use { archive.writeEntry(it) }
        if (entry.type == PosixFileType.REGULAR_FILE) {
            check(inputStream != null) { "inputStream == null" }
            inputStream.copyTo(archive.newDataOutputStream(), 0, listener)
        } else {
            listener?.invoke(entry.size)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        archive.close()
    }
}
