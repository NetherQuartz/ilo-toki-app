package one.larkin.ilotoki.ui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Adds `TYPE_TEXT_FLAG_NO_SUGGESTIONS` to the `EditorInfo` Compose hands the IME.
 *
 * The flag has to go on *after* the request has filled the attributes out, because
 * `EditorInfo.update()` assigns `inputType` outright rather than adding to it —
 * setting the flag first would simply be overwritten.
 *
 * `remember(enabled)` is the whole of the switching: the interceptor lives in a
 * `MutableState` that the session collects, so handing over a different instance is
 * what cancels the input method and starts it again with new attributes. Remembering
 * it across a change of [enabled] would leave the old flags in place until the field
 * next lost focus.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlainKeyboard(enabled: Boolean, content: @Composable () -> Unit) {
    val interceptor = remember(enabled) {
        PlatformTextInputInterceptor { request, nextHandler ->
            if (!enabled) {
                nextHandler.startInputMethod(request)
            } else {
                nextHandler.startInputMethod(
                    object : PlatformTextInputMethodRequest {
                        override fun createInputConnection(
                            outAttributes: EditorInfo,
                        ): InputConnection {
                            val connection = request.createInputConnection(outAttributes)
                            outAttributes.inputType = outAttributes.inputType or
                                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            return connection
                        }
                    },
                )
            }
        }
    }
    InterceptPlatformTextInput(interceptor, content)
}
