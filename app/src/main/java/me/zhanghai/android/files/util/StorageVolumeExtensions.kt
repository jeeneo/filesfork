package me.zhanghai.android.filesfork.util

import android.os.storage.StorageVolume
import me.zhanghai.android.filesfork.compat.directoryCompat

val StorageVolume.isMounted: Boolean
    get() = directoryCompat != null
