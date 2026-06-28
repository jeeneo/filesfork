/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.filelist

import android.os.Bundle
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.filesfork.app.AppActivity
import me.zhanghai.android.filesfork.file.MimeType
import me.zhanghai.android.filesfork.file.fileProviderUri
import me.zhanghai.android.filesfork.util.ParcelableArgs
import me.zhanghai.android.filesfork.util.ParcelableParceler
import me.zhanghai.android.filesfork.util.args
import me.zhanghai.android.filesfork.util.createEditIntent
import me.zhanghai.android.filesfork.util.startActivitySafe

// Use a trampoline activity so that we can have a proper icon and title.
class EditFileActivity : AppActivity() {
    private val args by args<Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivitySafe(args.path.fileProviderUri.createEditIntent(args.mimeType))
        finish()
    }

    @Parcelize
    class Args(
        val path: @WriteWith<ParcelableParceler> Path,
        val mimeType: MimeType
    ) : ParcelableArgs
}
