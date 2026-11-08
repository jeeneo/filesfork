package ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5E5E5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF1B1B1B),
    inversePrimary = Color(0xFFC6C6C6),
    secondary = Color(0xFF5E5E5E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E2E2),
    onSecondaryContainer = Color(0xFF1B1B1B),
    tertiary = Color(0xFF5E5E5E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E2E2),
    onTertiaryContainer = Color(0xFF1B1B1B),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surface = Color(0xFFF9F9F9),
    onSurface = Color(0xFF303030),
    surfaceVariant = Color(0xFFF1F1F1),
    onSurfaceVariant = Color(0xFF303030),
    surfaceTint = Color(0xFF5E5E5E),
    surfaceBright = Color(0xFFF6F6F6),
    surfaceDim = Color(0xFFE2E3E3),
    surfaceContainer = Color(0xFFECECEC),
    surfaceContainerHigh = Color(0xFFF0F1F1),
    surfaceContainerHighest = Color(0xFFEEEEEE),
    surfaceContainerLow = Color(0xFFF4F4F4),
    surfaceContainerLowest = Color(0xFFF9F9F9),
    inverseSurface = Color(0xFF000000),
    inverseOnSurface = Color(0xFFF1F1F1),
    outline = Color(0xFF767676),
    outlineVariant = Color(0xFFC6C6C6),
    scrim = Color(0xFF000000)
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC6C6C6),
    onPrimary = Color(0xFF303030),
    primaryContainer = Color(0xFF474747),
    onPrimaryContainer = Color(0xFFE2E2E2),
    inversePrimary = Color(0xFF5E5E5E),
    secondary = Color(0xFFC6C6C6),
    onSecondary = Color(0xFF303030),
    secondaryContainer = Color(0xFF474747),
    onSecondaryContainer = Color(0xFFE2E2E2),
    tertiary = Color(0xFFC6C6C6),
    onTertiary = Color(0xFF303030),
    tertiaryContainer = Color(0xFF474747),
    onTertiaryContainer = Color(0xFFE2E2E2),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF303030),
    onSurfaceVariant = Color(0xFFF1F1F1),
    surfaceTint = Color(0xFFC6C6C6),
    surfaceBright = Color(0xFF2E2E2E),
    surfaceDim = Color(0xFF000000),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF252526),
    surfaceContainerHighest = Color(0xFF2A2A2A),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainerLowest = Color(0xFF101010),
    inverseSurface = Color(0xFFF9F9F9),
    inverseOnSurface = Color(0xFF303030),
    outline = Color(0xFF919191),
    outlineVariant = Color(0xFF474747),
    scrim = Color(0xFF000000)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
//    val colorScheme = when {
//        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//            val context = LocalContext.current
//            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//        }
//
//        darkTheme -> DarkColorScheme
//        else -> LightColorScheme
//    }
    val activity = LocalActivity.current
    SideEffect {
        val style = if (darkTheme) {
            SystemBarStyle.dark(
                scrim = android.graphics.Color.TRANSPARENT
            )
        } else {
            SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        }
        (activity as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = style, navigationBarStyle = style
        )
    }
    MaterialTheme(
        colorScheme = DarkColorScheme, content = content
    )
}
