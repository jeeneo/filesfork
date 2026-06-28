/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import me.zhanghai.android.filesfork.util.Stateful

class AddLanSmbServerViewModel : ViewModel() {
    private val _lanSmbServerListLiveData = LanSmbServerListLiveData()
    val lanSmbServerListLiveData: LiveData<Stateful<List<LanSmbServer>>> = _lanSmbServerListLiveData

    fun reload() {
        _lanSmbServerListLiveData.loadValue()
    }

    override fun onCleared() {
        _lanSmbServerListLiveData.close()
    }
}
