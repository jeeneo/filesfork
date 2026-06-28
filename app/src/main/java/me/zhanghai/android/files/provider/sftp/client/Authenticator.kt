/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.provider.sftp.client

interface Authenticator {
    fun getAuthentication(authority: Authority): Authentication?
}
