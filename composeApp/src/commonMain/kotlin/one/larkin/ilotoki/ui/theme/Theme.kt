package one.larkin.ilotoki.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppShapes = Shapes(
    small = RoundedCornerShape(8),
    medium = RoundedCornerShape(16),
    large = RoundedCornerShape(24),
)

private val AppTypography = Typography(
    bodyLarge = Typography().bodyLarge.copy(
        lineHeight = Typography().bodyLarge.lineHeight * 1.1f,
    ),
)

/** Fallback palette for platforms (and Android versions) without dynamic colour. */
internal val FallbackLightColors = lightColorScheme(
    primary = Color(0xFF2C6A5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFAEF0DD),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A635C),
    surfaceVariant = Color(0xFFDBE5E0),
)

internal val FallbackDarkColors = darkColorScheme(
    primary = Color(0xFF92D4C1),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF115143),
    onPrimaryContainer = Color(0xFFAEF0DD),
    secondary = Color(0xFFB1CCC4),
    surfaceVariant = Color(0xFF3F4945),
)

/**
 * The platform's preferred palette: Material You on Android 12+, the fallback
 * palette everywhere else.
 */
@Composable
expect fun platformColorScheme(useDarkTheme: Boolean): ColorScheme

@Composable
fun IloTokiTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = platformColorScheme(useDarkTheme),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
