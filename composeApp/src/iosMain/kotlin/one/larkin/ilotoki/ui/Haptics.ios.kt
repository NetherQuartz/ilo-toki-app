package one.larkin.ilotoki.ui

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * The two generators whose character matches what the taps are for.
 *
 * A press is an impact — light, so it reads as a click rather than a knock. A word
 * landing is a *selection change*, which is the Taptic Engine's faintest tick and
 * the one Apple uses for values scrolling past under a finger; it is built to be
 * repeated, which is the whole problem with using an impact for this.
 *
 * The generators are kept rather than made per tap: constructing one is what warms
 * the engine up, and a cold one answers late enough to feel detached from what
 * caused it. `prepare()` on top of that keeps it warm through a burst of words.
 */
private val impact by lazy {
    UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
}

private val selection by lazy { UISelectionFeedbackGenerator() }

internal actual fun performHaptic(haptic: Haptic) {
    when (haptic) {
        Haptic.Press -> {
            impact.impactOccurred()
            impact.prepare()
        }

        Haptic.Word -> {
            selection.selectionChanged()
            selection.prepare()
        }
    }
}
