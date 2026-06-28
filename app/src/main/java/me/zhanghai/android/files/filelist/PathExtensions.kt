/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.filelist

import java8.nio.file.Path
import me.zhanghai.android.filesfork.file.MimeType
import me.zhanghai.android.filesfork.file.isSupportedArchive
import me.zhanghai.android.filesfork.provider.archive.archiveFile
import me.zhanghai.android.filesfork.provider.archive.isArchivePath
import me.zhanghai.android.filesfork.provider.document.isDocumentPath
import me.zhanghai.android.filesfork.provider.document.resolver.DocumentResolver
import me.zhanghai.android.filesfork.provider.linux.isLinuxPath

val Path.name: String
    get() = fileName?.toString() ?: if (isArchivePath) archiveFile.fileName.toString() else "/"

fun Path.toUserFriendlyString(): String = if (isLinuxPath) toFile().path else toUri().toString()

fun Path.isArchiveFile(mimeType: MimeType): Boolean = !isArchivePath && mimeType.isSupportedArchive

val Path.isLocalPath: Boolean
    get() =
        isLinuxPath || (isDocumentPath && DocumentResolver.isLocal(this as DocumentResolver.Path))

val Path.isRemotePath: Boolean
    get() = !isLocalPath
