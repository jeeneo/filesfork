package me.zhanghai.android.filesfork.provider.remote;

import me.zhanghai.android.filesfork.provider.common.ParcelableFileTime;
import me.zhanghai.android.filesfork.provider.common.ParcelablePosixFileMode;
import me.zhanghai.android.filesfork.provider.common.PosixGroup;
import me.zhanghai.android.filesfork.provider.common.PosixUser;
import me.zhanghai.android.filesfork.provider.remote.ParcelableException;
import me.zhanghai.android.filesfork.provider.remote.ParcelableObject;

interface IRemotePosixFileAttributeView {
    ParcelableObject readAttributes(out ParcelableException exception);

    void setTimes(
        in ParcelableFileTime lastModifiedTime,
        in ParcelableFileTime lastAccessTime,
        in ParcelableFileTime createTime,
        out ParcelableException exception
    );

    void setOwner(in PosixUser owner, out ParcelableException exception);

    void setGroup(in PosixGroup group, out ParcelableException exception);

    void setMode(in ParcelablePosixFileMode mode, out ParcelableException exception);

    void setSeLinuxContext(in ParcelableObject context, out ParcelableException exception);

    void restoreSeLinuxContext(out ParcelableException exception);
}
