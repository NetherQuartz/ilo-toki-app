package one.larkin.ilotoki.ui.theme

import android.os.Build
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Material You wallpaper colours only exist from Android 12 (API 31). */
actual val systemColoursAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The wallpaper accent, always taken from the *light* dynamic scheme.
 *
 * The target plate keeps dark text in both themes, so the accent has to stay a
 * light tone whatever the system theme is; `primaryContainer` is the dynamic role
 * that is guaranteed to be one.
 */
@Composable
actual fun systemAccentOrNull(): Color? {
    if (!systemColoursAvailable) return null
    return dynamicLightColorScheme(LocalContext.current).primaryContainer
}
