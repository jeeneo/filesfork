package me.zhanghai.android.filesfork.provider.remote;

import me.zhanghai.android.filesfork.provider.remote.IRemoteFileSystem;
import me.zhanghai.android.filesfork.provider.remote.IRemoteFileSystemProvider;
import me.zhanghai.android.filesfork.provider.remote.IRemotePosixFileAttributeView;
import me.zhanghai.android.filesfork.provider.remote.IRemotePosixFileStore;
import me.zhanghai.android.filesfork.provider.remote.ParcelableObject;

interface IRemoteFileService {
    IRemoteFileSystemProvider getRemoteFileSystemProviderInterface(String scheme);

    IRemoteFileSystem getRemoteFileSystemInterface(in ParcelableObject fileSystem);

    IRemotePosixFileStore getRemotePosixFileStoreInterface(in ParcelableObject fileStore);

    IRemotePosixFileAttributeView getRemotePosixFileAttributeViewInterface(
        in ParcelableObject attributeView
    );
}
