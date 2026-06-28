/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.storage

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.filesfork.R
import me.zhanghai.android.filesfork.provider.smb.client.Authority
import me.zhanghai.android.filesfork.provider.smb.createSmbRootPath
import me.zhanghai.android.filesfork.util.createIntent
import me.zhanghai.android.filesfork.util.putArgs
import kotlin.random.Random

@Parcelize
class SmbServer(
    override val id: Long,
    override val customName: String?,
    val authority: Authority,
    val password: String,
    val relativePath: String
) : Storage() {
    constructor(
        id: Long?,
        customName: String?,
        authority: Authority,
        password: String,
        relativePath: String
    ) : this(id ?: Random.nextLong(), customName, authority, password, relativePath)

    override val iconRes: Int
        @DrawableRes
        get() = R.drawable.computer_icon_white_24dp

    override fun getDefaultName(context: Context): String =
        if (relativePath.isNotEmpty()) "$authority/$relativePath" else authority.toString()

    override val description: String
        get() = authority.toString()

    override val path: Path
        get() = authority.createSmbRootPath().resolve(relativePath)

    override fun createEditIntent(): Intent =
        EditSmbServerActivity::class.createIntent().putArgs(EditSmbServerFragment.Args(this))
}
