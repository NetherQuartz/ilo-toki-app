package one.larkin.ilotoki.ui

import one.larkin.ilotoki.data.SettingsRepository

/**
 * The two taps this app makes, and nothing else.
 *
 * They are deliberately different in character rather than the same buzz at two
 * volumes: a press is a discrete decision and answers with a crisp click, while a
 * word arriving is a texture — it happens over and over inside one action and has
 * to stay under the threshold of «something is vibrating in my hand». On both
 * platforms [Word] is the lightest thing the system offers.
 *
 * Nothing here is a fallback to a raw vibrator: these go through the platform's own
 * UI-feedback path, which is tuned per device and already respects the phone's
 * system-wide haptics switch. The app's own switch sits on top of that, for people
 * who want the rest of the phone to keep its haptics and this app to be silent.
 */
enum class Haptic {
    /** Under a finger: a button, a chip, a plate. */
    Press,

    /** Under a decoded word as it lands. The lightest tick available. */
    Word,
}

/**
 * Plays [haptic] unless the setting is off.
 *
 * Reads the repository directly rather than taking the flag as a parameter: this is
 * called from press handlers all over the tree and from the token loop, and
 * threading a boolean through every one of them would be a lot of plumbing for a
 * value that changes about once in an install's lifetime.
 */
fun playHaptic(haptic: Haptic) {
    if (!SettingsRepository.settings.value.haptics) return
    performHaptic(haptic)
}

internal expect fun performHaptic(haptic: Haptic)
