/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.filelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import java8.nio.file.Path
import me.zhanghai.android.filesfork.filelist.FileSortOptions.By
import me.zhanghai.android.filesfork.filelist.FileSortOptions.Order
import me.zhanghai.android.filesfork.settings.PathSettings
import me.zhanghai.android.filesfork.settings.SettingLiveData
import me.zhanghai.android.filesfork.settings.Settings
import me.zhanghai.android.filesfork.util.valueCompat

class FileSortOptionsLiveData(pathLiveData: LiveData<Path>) : MediatorLiveData<FileSortOptions>() {
    private lateinit var pathSortOptionsLiveData: SettingLiveData<FileSortOptions?>

    private fun loadValue() {
        if (!this::pathSortOptionsLiveData.isInitialized) {
            // Not yet initialized.
            return
        }
        val value = pathSortOptionsLiveData.value ?: Settings.FILE_LIST_SORT_OPTIONS.valueCompat
        if (this.value != value) {
            this.value = value
        }
    }

    fun putBy(by: By) {
        putValue(valueCompat.copy(by = by))
    }

    fun putOrder(order: Order) {
        putValue(valueCompat.copy(order = order))
    }

    fun putIsDirectoriesFirst(isDirectoriesFirst: Boolean) {
        putValue(valueCompat.copy(isDirectoriesFirst = isDirectoriesFirst))
    }

    private fun putValue(value: FileSortOptions) {
        if (pathSortOptionsLiveData.value != null) {
            pathSortOptionsLiveData.putValue(value)
        } else {
            Settings.FILE_LIST_SORT_OPTIONS.putValue(value)
        }
    }

    init {
        addSource(Settings.FILE_LIST_SORT_OPTIONS) { loadValue() }
        addSource(pathLiveData) { path: Path ->
            if (this::pathSortOptionsLiveData.isInitialized) {
                removeSource(pathSortOptionsLiveData)
            }
            pathSortOptionsLiveData = PathSettings.getFileListSortOptions(path)
            addSource(pathSortOptionsLiveData) { loadValue() }
        }
    }
}
