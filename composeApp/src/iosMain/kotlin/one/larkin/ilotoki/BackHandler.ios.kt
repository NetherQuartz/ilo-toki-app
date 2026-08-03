package one.larkin.ilotoki

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS has no system back to intercept: the app's own back square is the only way
 * out of a subscreen, and it already calls the same handler. The gesture therefore
 * never starts, and everything watching it simply never fires.
 */
@Composable
actual fun rememberBackGesture(
    enabled: Boolean,
    previewed: Boolean,
    onBack: () -> Unit,
): BackGesture = remember { BackGesture() }
