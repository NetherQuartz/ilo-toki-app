package one.larkin.ilotoki.ui

import androidx.compose.runtime.Composable

/**
 * Turns off the keyboard's word suggestions for every text field inside [content].
 *
 * This exists because `KeyboardOptions.autoCorrectEnabled = false` is not what its
 * name suggests on Android. It only *omits* `TYPE_TEXT_FLAG_AUTO_CORRECT`, which
 * stops the keyboard rewriting a finished word — the suggestion strip, the
 * predictions and the tap-to-complete all carry on, and none of them has ever heard
 * of `mi`, `sina` or `pilin`. The flag that asks for them to go is
 * `TYPE_TEXT_FLAG_NO_SUGGESTIONS`, and no `KeyboardOptions` field maps to it, so the
 * Android side reaches the `EditorInfo` itself.
 *
 * It is a request, and **Gboard does not grant it** — on a Pixel 6 the strip kept
 * offering «missing» for `mi` with the flag plainly in the `EditorInfo`. It is kept
 * for the keyboards that do honour it. The one thing Gboard obeys is the visible-
 * password variation, and that was tried and turned down: it adds a row of digits
 * and takes the emoji key and voice input, so toki pona would get a different
 * keyboard from every other language, which is worse than a strip nobody has to tap.
 * On iOS the strip is gone anyway, through `KeyboardType.Ascii` at the call site.
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
