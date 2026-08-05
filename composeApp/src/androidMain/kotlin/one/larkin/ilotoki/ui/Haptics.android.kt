package one.larkin.ilotoki.ui

import android.view.HapticFeedbackConstants
import android.view.View
import one.larkin.ilotoki.currentActivity

/**
 * `View.performHapticFeedback`, not the vibrator.
 *
 * The constants are mapped to a device's own actuator by the platform, so a press
 * feels like a press on hardware that has a good one and does not turn into a buzz
 * on hardware that does not. It also obeys the system haptics setting for free,
 * which a direct `Vibrator` call would have to be told about.
 *
 * `KEYBOARD_TAP` is the crisp one — the same feedback a key gives, which is exactly
 * what a button is. `CLOCK_TICK` is the faintest thing in the set, made for values
 * ticking past under a finger, and that is the right shape for words arriving one
 * after another.
 */
internal actual fun performHaptic(haptic: Haptic) {
    val view: View = currentActivity?.window?.decorView ?: return
    val constant = when (haptic) {
        Haptic.Press -> HapticFeedbackConstants.KEYBOARD_TAP
        Haptic.Word -> HapticFeedbackConstants.CLOCK_TICK
    }
    // FLAG_IGNORE_VIEW_SETTING is deliberately not passed: a view or a device that
    // has haptics turned off should stay quiet.
    runCatching { view.performHapticFeedback(constant) }
}
