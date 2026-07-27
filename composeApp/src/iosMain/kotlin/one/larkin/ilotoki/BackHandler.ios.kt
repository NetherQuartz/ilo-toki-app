package one.larkin.ilotoki

import androidx.compose.runtime.Composable

/**
 * iOS has no system back to intercept: the app's own back square is the only way
 * out of a subscreen, and it already calls the same handler.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
