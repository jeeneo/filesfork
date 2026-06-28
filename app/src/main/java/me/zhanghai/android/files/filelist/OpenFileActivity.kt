/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.filelist

import android.content.Intent
import android.os.Bundle
import java8.nio.file.Path
import me.zhanghai.android.filesfork.app.AppActivity
import me.zhanghai.android.filesfork.app.application
import me.zhanghai.android.filesfork.file.MimeType
import me.zhanghai.android.filesfork.file.asMimeTypeOrNull
import me.zhanghai.android.filesfork.file.fileProviderUri
import me.zhanghai.android.filesfork.filejob.FileJobService
import me.zhanghai.android.filesfork.provider.archive.isArchivePath
import me.zhanghai.android.filesfork.util.createViewIntent
import me.zhanghai.android.filesfork.util.extraPath
import me.zhanghai.android.filesfork.util.startActivitySafe

class OpenFileActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val path = intent.extraPath
        val mimeType = intent.type?.asMimeTypeOrNull()
        if (path != null && mimeType != null) {
            openFile(path, mimeType)
        }
        finish()
    }

    private fun openFile(path: Path, mimeType: MimeType) {
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, false, this)
        } else {
            val intent = path.fileProviderUri.createViewIntent(mimeType)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .apply { extraPath = path }
            startActivitySafe(intent)
        }
    }

    companion object {
        private const val ACTION_OPEN_FILE = "me.zhanghai.android.filesfork.intent.action.OPEN_FILE"

        fun createIntent(path: Path, mimeType: MimeType): Intent =
            Intent(ACTION_OPEN_FILE)
                .setPackage(application.packageName)
                .setType(mimeType.value)
                .apply { extraPath = path }
    }
}
