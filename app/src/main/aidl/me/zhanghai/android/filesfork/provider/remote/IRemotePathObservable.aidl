package me.zhanghai.android.filesfork.provider.remote;

import me.zhanghai.android.filesfork.provider.remote.ParcelableException;
import me.zhanghai.android.filesfork.util.RemoteCallback;

interface IRemotePathObservable {
    void addObserver(in RemoteCallback observer);

    void close(out ParcelableException exception);
}
