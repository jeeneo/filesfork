/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.filelist

import me.zhanghai.android.filesfork.file.MimeType

class PickOptions(
    val mode: Mode,
    val fileName: String?,
    val readOnly: Boolean,
    val mimeTypes: List<MimeType>,
    val localOnly: Boolean,
    val allowMultiple: Boolean
) {
    enum class Mode {
        OPEN_FILE,
        CREATE_FILE,
        OPEN_DIRECTORY
    }
}
