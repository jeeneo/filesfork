package me.zhanghai.android.filesfork.provider.remote;

import me.zhanghai.android.filesfork.provider.remote.ParcelableException;

interface IRemoteFileSystem {
    void close(out ParcelableException exception);
}
