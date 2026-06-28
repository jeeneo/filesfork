/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.provider.archive

import android.content.Context
import java8.nio.file.Path
import me.zhanghai.android.filesfork.fileaction.ArchivePasswordDialogActivity
import me.zhanghai.android.filesfork.fileaction.ArchivePasswordDialogFragment
import me.zhanghai.android.filesfork.provider.common.UserAction
import me.zhanghai.android.filesfork.provider.common.UserActionRequiredException
import me.zhanghai.android.filesfork.util.createIntent
import me.zhanghai.android.filesfork.util.putArgs
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class ArchivePasswordRequiredException(
    private val file: Path,
    reason: String?
) :
    UserActionRequiredException(file.toString(), null, reason) {

    override fun getUserAction(continuation: Continuation<Boolean>, context: Context): UserAction {
        return UserAction(
            ArchivePasswordDialogActivity::class.createIntent().putArgs(
                ArchivePasswordDialogFragment.Args(file) { continuation.resume(it) }
            ), ArchivePasswordDialogFragment.getTitle(context),
            ArchivePasswordDialogFragment.getMessage(file, context)
        )
    }
}
