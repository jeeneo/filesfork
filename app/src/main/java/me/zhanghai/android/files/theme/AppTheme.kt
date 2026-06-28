package me.zhanghai.android.filesfork.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import me.zhanghai.android.filesfork.theme.night.NightModeHelper

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = NightModeHelper.isInNightMode(
        context as androidx.appcompat.app.AppCompatActivity
    )
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme, content = content
    )
}
