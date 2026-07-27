package one.larkin.ilotoki.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun platformColorScheme(useDarkTheme: Boolean): ColorScheme =
    if (useDarkTheme) FallbackDarkColors else FallbackLightColors
