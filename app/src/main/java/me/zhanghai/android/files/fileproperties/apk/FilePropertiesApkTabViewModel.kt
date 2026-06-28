/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.fileproperties.apk

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import java8.nio.file.Path
import me.zhanghai.android.filesfork.util.Stateful

class FilePropertiesApkTabViewModel(path: Path) : ViewModel() {
    private val _apkInfoLiveData = ApkInfoLiveData(path)
    val apkInfoLiveData: LiveData<Stateful<ApkInfo>>
        get() = _apkInfoLiveData

    fun reload() {
        _apkInfoLiveData.loadValue()
    }

    override fun onCleared() {
        _apkInfoLiveData.close()
    }
}
