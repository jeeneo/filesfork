/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.fileproperties

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import me.zhanghai.android.filesfork.file.FileItem
import me.zhanghai.android.filesfork.util.Stateful

class FilePropertiesFileViewModel(file: FileItem) : ViewModel() {
    private val _fileLiveData = FileLiveData(file)
    val fileLiveData: LiveData<Stateful<FileItem>>
        get() = _fileLiveData

    fun reload() {
        _fileLiveData.loadValue()
    }

    override fun onCleared() {
        _fileLiveData.close()
    }
}
