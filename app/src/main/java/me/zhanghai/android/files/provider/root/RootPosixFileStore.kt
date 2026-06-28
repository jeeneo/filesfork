/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.provider.root

import me.zhanghai.android.filesfork.provider.common.PosixFileStore
import me.zhanghai.android.filesfork.provider.remote.RemoteInterface
import me.zhanghai.android.filesfork.provider.remote.RemotePosixFileStore

class RootPosixFileStore(fileStore: PosixFileStore) : RemotePosixFileStore(
    RemoteInterface { RootFileService.getRemotePosixFileStoreInterface(fileStore) }
)
