package one.larkin.ilotoki.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun platformColorScheme(useDarkTheme: Boolean): ColorScheme {
    // Material You wallpaper colours only exist from Android 12 (API 31).
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return if (useDarkTheme) FallbackDarkColors else FallbackLightColors
    }
    val context = LocalContext.current
    return if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
