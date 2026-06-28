/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.app

import android.os.AsyncTask
import android.os.Build
import android.webkit.WebView
import jcifs.context.SingletonContext
import me.zhanghai.android.filesfork.BuildConfig
import me.zhanghai.android.filesfork.coil.initializeCoil
import me.zhanghai.android.filesfork.filejob.fileJobNotificationTemplate
import me.zhanghai.android.filesfork.ftpserver.ftpServerServiceNotificationTemplate
import me.zhanghai.android.filesfork.hiddenapi.HiddenApi
import me.zhanghai.android.filesfork.provider.FileSystemProviders
import me.zhanghai.android.filesfork.settings.Settings
import me.zhanghai.android.filesfork.storage.FtpServerAuthenticator
import me.zhanghai.android.filesfork.storage.SftpServerAuthenticator
import me.zhanghai.android.filesfork.storage.SmbServerAuthenticator
import me.zhanghai.android.filesfork.storage.StorageVolumeListLiveData
import me.zhanghai.android.filesfork.storage.WebDavServerAuthenticator
import me.zhanghai.android.filesfork.theme.custom.CustomThemeHelper
import me.zhanghai.android.filesfork.theme.night.NightModeHelper
import java.util.Properties
import me.zhanghai.android.filesfork.provider.ftp.client.Client as FtpClient
import me.zhanghai.android.filesfork.provider.sftp.client.Client as SftpClient
import me.zhanghai.android.filesfork.provider.smb.client.Client as SmbClient
import me.zhanghai.android.filesfork.provider.webdav.client.Client as WebDavClient

val appInitializers = listOf(
    // ::initializeCrashlytics,
    ::disableHiddenApiChecks,
    ::initializeWebViewDebugging,
    ::initializeCoil,
    ::initializeFileSystemProviders,
    ::upgradeApp,
    ::initializeLiveDataObjects,
    ::initializeCustomTheme,
    ::initializeNightMode,
    ::createNotificationChannels
)

// private fun initializeCrashlytics() {
// //#ifdef NONFREE
//     // me.zhanghai.android.filesfork.nonfree.CrashlyticsInitializer.initialize()
// //#endif
// }

private fun disableHiddenApiChecks() {
    HiddenApi.disableHiddenApiChecks()
}

private fun initializeWebViewDebugging() {
    if (BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(true)
    }
}

private fun initializeFileSystemProviders() {
    FileSystemProviders.install()
    FileSystemProviders.overflowWatchEvents = true
    // SingletonContext.init() calls NameServiceClientImpl.initCache() which connects to network.
    AsyncTask.THREAD_POOL_EXECUTOR.execute {
        SingletonContext.init(
            Properties().apply {
                setProperty("jcifs.netbios.cachePolicy", "0")
                setProperty("jcifs.smb.client.maxVersion", "SMB1")
            }
        )
    }
    FtpClient.authenticator = FtpServerAuthenticator
    SftpClient.authenticator = SftpServerAuthenticator
    SmbClient.authenticator = SmbServerAuthenticator
    WebDavClient.authenticator = WebDavServerAuthenticator
}

private fun initializeLiveDataObjects() {
    // Force initialization of LiveData objects so that it won't happen on a background thread.
    StorageVolumeListLiveData.value
    Settings.FILE_LIST_DEFAULT_DIRECTORY.value
}

private fun initializeCustomTheme() {
    CustomThemeHelper.initialize(application)
}

private fun initializeNightMode() {
    NightModeHelper.initialize(application)
}

private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationManager.createNotificationChannels(
            listOf(
                backgroundActivityStartNotificationTemplate.channelTemplate,
                fileJobNotificationTemplate.channelTemplate,
                ftpServerServiceNotificationTemplate.channelTemplate
            ).map { it.create(application) }
        )
    }
}
