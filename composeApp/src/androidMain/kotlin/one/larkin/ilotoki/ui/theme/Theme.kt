package one.larkin.ilotoki.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import android.os.Build
import androidx.compose.ui.platform.LocalContext

// --- Цвета ---
private val LightColors = lightColorScheme(
    primary = Color(0xFF006C47),
    onPrimary = Color.White,
    secondary = Color(0xFF4CAF50),
    onSecondary = Color.White,
    background = Color(0xFFFDFDFD),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80E6A2),
    onPrimary = Color(0xFF003921),
    secondary = Color(0xFF9DE89F),
    onSecondary = Color(0xFF003100),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE3E3E3)
)

// --- Формы ---
private val AppShapes = Shapes(
    small = RoundedCornerShape(8),
    medium = RoundedCornerShape(16),
    large = RoundedCornerShape(24)
)

// --- Типографика ---
private val AppTypography = Typography(
    titleLarge = Typography().titleLarge,
    bodyLarge = Typography().bodyLarge.copy(
        lineHeight = Typography().bodyLarge.lineHeight * 1.1f
    )
)

@Composable
fun IloTokiTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        useDarkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
