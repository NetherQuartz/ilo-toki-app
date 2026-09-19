package one.larkin.ilotoki.ui

import androidx.compose.runtime.Composable

/**
 * Nothing to do: `autoCorrectEnabled = false` already reaches UIKit as
 * `autocorrectionType = .no`, and on iOS that is the switch that takes the QuickType
 * bar away as well as the corrections. There is no second flag to set, and no way to
 * ask for a keyboard language either — the language of the keyboard belongs to the
 * user's own input modes, which is why the [PlainKeyboard] doc talks about Android.
 */
@Composable
actual fun PlainKeyboard(enabled: Boolean, content: @Composable () -> Unit) = content()
