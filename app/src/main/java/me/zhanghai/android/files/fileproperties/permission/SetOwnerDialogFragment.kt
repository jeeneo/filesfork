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
import me.zhanghai.android.filesfork.provider.common.PosixPrincipal
import me.zhanghai.android.filesfork.provider.common.PosixUser
import me.zhanghai.android.filesfork.provider.common.toByteString
import me.zhanghai.android.filesfork.util.SelectionLiveData
import me.zhanghai.android.filesfork.util.putArgs
import me.zhanghai.android.filesfork.util.show
import me.zhanghai.android.filesfork.util.viewModels

class SetOwnerDialogFragment : SetPrincipalDialogFragment() {
    override val viewModel: SetPrincipalViewModel by viewModels { { SetOwnerViewModel() } }

    @StringRes
    override val titleRes: Int = R.string.file_properties_permission_set_owner_title

    override fun createAdapter(selectionLiveData: SelectionLiveData<Int>): PrincipalListAdapter =
        UserListAdapter(selectionLiveData)

    override val PosixFileAttributes.principal: PosixPrincipal
        get() = owner()!!

    override fun setPrincipal(path: Path, principal: PrincipalItem, recursive: Boolean) {
        val owner = PosixUser(principal.id, principal.name?.toByteString())
        FileJobService.setOwner(path, owner, recursive, requireContext())
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            SetOwnerDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }
}
