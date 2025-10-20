package one.larkin.ilotoki.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val AppShapes = Shapes(
    small = RoundedCornerShape(8),
    medium = RoundedCornerShape(16),
    large = RoundedCornerShape(24)
)

private val AppTypography = Typography(
    titleLarge = Typography().titleLarge,
    bodyLarge = Typography().bodyLarge.copy(
        lineHeight = Typography().bodyLarge.lineHeight * 1.1f
    )
)

@Composable
fun IloTokiTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (useDarkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
