package one.larkin.ilotoki.ui

import androidx.compose.runtime.Composable

/**
 * Turns off the keyboard's word suggestions for every text field inside [content].
 *
 * This exists because `KeyboardOptions.autoCorrectEnabled = false` is not what its
 * name suggests on Android. It only *omits* `TYPE_TEXT_FLAG_AUTO_CORRECT`, which
 * stops the keyboard rewriting a finished word — the suggestion strip, the
 * predictions and the tap-to-complete all carry on, and none of them has ever heard
 * of `mi`, `sina` or `pilin`. What actually takes them away is
 * `TYPE_TEXT_FLAG_NO_SUGGESTIONS`, and no `KeyboardOptions` field maps to it, so the
 * Android side reaches the `EditorInfo` itself. iOS needs nothing: there
 * `autoCorrectEnabled` becomes `autocorrectionType = .no`, which does take the
 * QuickType bar with it.
 *
 * Both platforms use the device's own keyboard throughout — this changes what is
 * asked of it, never what is drawn.
 *
 * It wraps a subtree rather than decorating one field because the mechanism is a
 * composition local on Android; anything with a text field in it can be handed to
 * it, and [enabled] false leaves the platform exactly as it was.
 */
@Composable
expect fun PlainKeyboard(enabled: Boolean, content: @Composable () -> Unit)
