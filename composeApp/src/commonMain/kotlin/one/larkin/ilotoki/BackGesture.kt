package one.larkin.ilotoki

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How far through a system back gesture the user is, while they are still deciding.
 *
 * The point of it is the preview: on Android the back swipe is a drag, not a tap,
 * and the screen it is going to should already be visible under your thumb before
 * you let go. [inFlight] is the composition-level question — «is there something to
 * show behind» — and [progress] is the per-frame one, so read them where each
 * belongs and not the other way round.
 */
@Stable
class BackGesture internal constructor() {
    internal val drag = Animatable(0f)
    internal val exit = Animatable(0f)

    /**
     * Where the finger says the drag is, which is not where the screen is.
     *
     * The system reports progress in jumps — a flick arrives as two or three events
     * — so [drag] follows this on a spring rather than being set from it. Snapping
     * to each event is what made a quick gesture stutter.
     */
    internal var pull by mutableFloatStateOf(0f)

    /** 0 at rest, 1 at a full drag. Live: read it where the transform is. */
    val progress: Float get() = drag.value

    /**
     * 0 until the gesture is let go for good, then 1 as the screen finishes leaving.
     *
     * The drag stops where the thumb stops, which is rarely all the way; without
     * this the screen would be cut off mid-departure the moment the finger lifts,
     * and that reads as no animation at all.
     */
    val exiting: Float get() = exit.value

    /** True from the first touch until the gesture has finished or gone home. */
    var inFlight by mutableStateOf(false)
        internal set
}

/**
 * Registers a handler for the platform's back gesture and reports its progress.
 *
 * [onBack] runs when the gesture is committed — or immediately, on a platform whose
 * back is a button rather than a drag.
 *
 * [previewed] is for the things back does that are not a change of screen. The
 * about card is one: there is nothing behind it to preview, it is *itself* what
 * back is dismissing, and sliding it about while the finger moves only asks the
 * user to wonder where it is going. With this off the gesture keeps its meaning
 * and loses its animation — [progress] never leaves zero, and [onBack] runs the
 * moment the gesture is let go rather than after something has finished moving.
 */
@Composable
expect fun rememberBackGesture(
    enabled: Boolean,
    previewed: Boolean = true,
    onBack: () -> Unit,
): BackGesture
