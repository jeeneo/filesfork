package me.zhanghai.android.filesfork.terminal

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import me.zhanghai.android.filesfork.app.packageManager
import me.zhanghai.android.filesfork.util.startActivitySafe

@SuppressLint("SdCardPath")
object Terminal {

    private const val TERMUX_PACKAGE = "com.termux"
    private const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    fun open(path: String, context: Context) {
        if (tryOpenInTermux(path, context)) return
        tryOpenInLegacyTerminal(path, context)
    }

    const val TERMUX_LOGIN = "/data/data/com.termux/files/usr/bin/login"
    const val TERMUX_SU = "/data/data/com.termux/files/usr/bin/su"
    const val TERMUX_HOME = "/data/data/com.termux/files/home"

    private fun tryOpenInTermux(path: String, context: Context): Boolean {
        if (path.isEmpty() || path.length > 4096 || path.contains('\u0000')) return false
        if (!path.startsWith("/")) return false
        val isTermuxInstalled = try {
            packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
        if (!isTermuxInstalled) return false
        return try {
            val (commandPath, arguments) = if (isRestrictedPath(path)) {
                val shellPath = path.replace("'", "'\\''")
                TERMUX_SU to arrayOf(
                    "-c", "cd -- '$shellPath' && exec $TERMUX_LOGIN"
                )
            } else {
                TERMUX_LOGIN to emptyArray()
            }
            val intent = Intent(ACTION_RUN_COMMAND).apply {
                component = ComponentName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                putExtra("com.termux.RUN_COMMAND_PATH", commandPath)
                if (arguments.isNotEmpty()) {
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments)
                }
                putExtra(
                    "com.termux.RUN_COMMAND_WORKDIR",
                    if (isRestrictedPath(path)) TERMUX_HOME else path
                )
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "1")
            }
            context.startService(intent)
            packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            true
        } catch (e: SecurityException) {
            android.util.Log.e("Terminal", "Termux permission denied", e)
            false
        } catch (e: Exception) {
            android.util.Log.e("Terminal", "Failed to start Termux", e)
            false
        }
    }

    private fun isRestrictedPath(path: String): Boolean {
        val realPath = try {
            java.io.File(path).canonicalPath.lowercase().let {
                if (it.endsWith("/")) it else "$it/"
            }
        } catch (_: Exception) {
            return true
        }
        val normalizedPath = path.lowercase().let {
            if (it.endsWith("/")) it else "$it/"
        }
        val normalPrefixes = listOf(
            "/storage/emulated/0/",
            "/sdcard/",
        )
        val restrictedSubPaths = listOf(
            "/storage/emulated/0/android/data/",
            "/storage/emulated/0/android/obb/",
            "/sdcard/android/data/",
            "/sdcard/android/obb/",
            "/mnt/sdcard/android/data/",
            "/mnt/sdcard/android/obb/",
            "/data/media/"
        )
        return listOf(normalizedPath, realPath).any { p ->
            val isUnderNormalStorage = normalPrefixes.any { p.startsWith(it) }
            val isRestrictedSubPath = restrictedSubPaths.any { p.startsWith(it) }
            !isUnderNormalStorage || isRestrictedSubPath
        }
    }

    private fun tryOpenInLegacyTerminal(path: String, context: Context) {
        val componentName =
            packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("*/*"), 0)
                .firstOrNull { it.activityInfo.name.endsWith(".TermHere") }?.activityInfo?.let {
                    ComponentName(
                        it.packageName, it.name
                    )
                } ?: ComponentName("jackpal.androidterm", "jackpal.androidterm.TermHere")
        val intent = Intent().setComponent(componentName).setAction(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, path.toUri())
        context.startActivitySafe(intent)
    }
}
