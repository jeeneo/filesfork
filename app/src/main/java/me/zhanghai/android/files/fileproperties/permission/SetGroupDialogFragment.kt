/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.fileproperties.permission

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import java8.nio.file.Path
import me.zhanghai.android.filesfork.R
import me.zhanghai.android.filesfork.file.FileItem
import me.zhanghai.android.filesfork.filejob.FileJobService
import me.zhanghai.android.filesfork.provider.common.PosixFileAttributes
import me.zhanghai.android.filesfork.provider.common.PosixGroup
import me.zhanghai.android.filesfork.provider.common.toByteString
import me.zhanghai.android.filesfork.util.SelectionLiveData
import me.zhanghai.android.filesfork.util.putArgs
import me.zhanghai.android.filesfork.util.show
import me.zhanghai.android.filesfork.util.viewModels

class SetGroupDialogFragment : SetPrincipalDialogFragment() {
    override val viewModel: SetPrincipalViewModel by viewModels { { SetGroupViewModel() } }

    @StringRes
    override val titleRes: Int = R.string.file_properties_permission_set_group_title

    override fun createAdapter(selectionLiveData: SelectionLiveData<Int>): PrincipalListAdapter =
        GroupListAdapter(selectionLiveData)

    override val PosixFileAttributes.principal
        get() = group()!!

    override fun setPrincipal(path: Path, principal: PrincipalItem, recursive: Boolean) {
        val group = PosixGroup(principal.id, principal.name?.toByteString())
        FileJobService.setGroup(path, group, recursive, requireContext())
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            SetGroupDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }
}
