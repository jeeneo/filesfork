package me.zhanghai.android.filesfork.provider.ftp

import java8.nio.file.FileSystemException
import me.zhanghai.android.filesfork.provider.ftp.client.NegativeReplyCodeException
import java.io.IOException

fun IOException.toFileSystemExceptionForFtp(
    file: String?,
    other: String? = null
): FileSystemException =
    when (this) {
        is NegativeReplyCodeException -> toFileSystemException(file, other)
        else ->
            FileSystemException(file, other, message)
                .apply { initCause(this@toFileSystemExceptionForFtp) }
    }
