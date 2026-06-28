/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.provider.remote

import java.io.IOException

class RemoteFileSystemException : IOException {

    constructor(message: String?) : super(message)

    constructor(cause: Throwable?) : super(cause)
}
