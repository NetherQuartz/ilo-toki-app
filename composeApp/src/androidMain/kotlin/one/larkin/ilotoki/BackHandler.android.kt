package one.larkin.ilotoki

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import one.larkin.ilotoki.ui.Motion

/** How long the screen takes to finish leaving once the gesture is committed. */
private const val EXIT_MS = 340

/** How long the peel takes to go home when the finger changes its mind. */
private const val RETURN_MS = 180

/**
 * The Android back gesture, with its drag.
 *
 * The gesture is a flow of events that either completes — the user let go past the
 * threshold — or is cancelled, which arrives as a [CancellationException] and is a
 * value here rather than a fault. Everything that outlives the gesture runs in the
 * composition's own scope and not the handler's: the handler's coroutine is the one
 * that has just been cancelled, and an animation started in it would die on its
 * first frame.
 */
@Composable
actual fun rememberBackGesture(
    enabled: Boolean,
    previewed: Boolean,
    onBack: () -> Unit,
): BackGesture {
    val gesture = remember { BackGesture() }
    val scope = rememberCoroutineScope()

    // The one writer of `drag`. It takes the finger's value as given while the
    // gesture is live and only animates on the way home.
    LaunchedEffect(gesture) {
        snapshotFlow { gesture.pull }.collectLatest { pull ->
            if (pull > 0f) {
                gesture.drag.snapTo(pull)
            } else {
                gesture.drag.animateTo(0f, tween(RETURN_MS, easing = Motion.Out))
                gesture.inFlight = false
            }
        }
    }

    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                if (previewed) {
                    gesture.inFlight = true
                    gesture.pull = event.progress
                }
            }
            if (!previewed) {
                onBack()
                return@PredictiveBackHandler
            }
            // Let it finish leaving before the screen underneath becomes the
            // screen: the swap itself is then invisible, because what is already
            // on display behind the departing card is the very thing it swaps to.
            scope.launch {
                gesture.exit.animateTo(1f, tween(EXIT_MS, easing = Motion.Emphasized))
                onBack()
                // Out of flight first, so that nothing is peeled by the values
                // being put back for the next gesture.
                gesture.inFlight = false
                gesture.pull = 0f
                gesture.drag.snapTo(0f)
                gesture.exit.snapTo(0f)
            }
        } catch (_: CancellationException) {
            // Not an error: the finger came back. Handing zero to the follower is
            // what runs the peel home and clears the flight when it arrives.
            gesture.pull = 0f
        }
    }
    return gesture
}
