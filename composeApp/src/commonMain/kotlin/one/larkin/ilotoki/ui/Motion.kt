package one.larkin.ilotoki.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.larkin.ilotoki.ui.theme.IloTokiTheme

/**
 * The design's motion, in one place: three curves, the durations that go with them,
 * and the entrances built out of both.
 *
 * Every value here is transcribed from the handoff rather than invented — the
 * keyframe names below are the design's own (`screenIn`, `plateIn`, `stampIn`,
 * `popIn`, `cardIn`, `cardDrop`, `tokenIn`, `dotPulse`, `calloutNudge`). Nothing in
 * this design eases *in*: things arrive fast and settle, which is what [Out] does,
 * and the two that overshoot say so explicitly.
 *
 * The idle loops read their animated value inside the `graphicsLayer` lambda, which
 * defers it to the draw phase and costs no recompositions — they run forever, so
 * that matters. The entrances deliberately do not; see [entrance].
 */
object Motion {
    /** `cubic-bezier(.2,.8,.2,1)` — the default. Anything that arrives or slides. */
    val Out: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** `cubic-bezier(.34,1.4,.5,1)` — goes past its target and comes back. */
    val Overshoot: Easing = CubicBezierEasing(0.34f, 1.4f, 0.5f, 1f)

    /** CSS `ease-out`. Fades and colour changes, which have nothing to overshoot. */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** CSS `ease-in-out`. Only the two idle loops use it. */
    val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /**
     * The platform's own predictive-back curve, `cubic-bezier(.1,.1,0,1)`.
     *
     * It is not one of this design's — it is Android's, and the back gesture
     * belongs to Android rather than to us. It spends almost everything in the
     * first fifth of the drag: a fifth of the way the screen has already moved
     * four fifths of what it is going to. That is what makes a back swipe feel
     * like a switch being thrown rather than like dragging the screen around, and
     * it is why the settings app does not appear to track your thumb.
     */
    val PredictiveBack: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    /**
     * `cubic-bezier(.4,0,.2,1)` — the only thing here that eases *in*.
     *
     * Reserved for finishing a back gesture after the thumb has gone. Measured
     * against the settings app frame by frame, its commit builds and then settles
     * rather than bolting the moment you let go: a departure that starts at full
     * speed reads as a cut, which is what the design's own [Out] gave here.
     */
    val Emphasized: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** A finger goes down and the surface answers. Short enough to feel like contact. */
    const val PRESS_MS = 90

    /** A colour crossing over to its other state — a filled pill, a live track. */
    const val COLOUR_MS = 180

    /** The same, on the two surfaces the design gives a slower change: slab and stamp. */
    const val SLOW_COLOUR_MS = 200

    /** A word appearing as it is decoded, and the placeholder it replaces. */
    const val TOKEN_MS = 170
}

/**
 * Set while a screen arrives somewhere it was already visible.
 *
 * A back gesture shows its destination under your thumb before you let go, so that
 * screen has already made its entrance by the time it becomes the screen. Playing
 * it again on arrival reads as a flash. Nothing else turns this on.
 *
 * Dynamic and not `staticCompositionLocalOf` on purpose: static is cheaper to read
 * but recomposes the *entire* subtree under the provider when it changes, and this
 * one changes twice per back gesture — which is two whole-app recompositions landing
 * exactly on the frames of the transition. Only the entrances read it.
 */
val LocalEntranceSuppressed = compositionLocalOf { false }

/**
 * Opacity 0 → 1 with an offset (and optionally a scale) that settles.
 *
 * The one primitive behind every named entrance below: [fromY] is where the element
 * starts relative to where it lands, so a negative value drops in from above.
 *
 * [enabled] is for anything that has to be measured before it can be placed: it
 * holds at nothing until told to go. Hiding the first frame some other way instead
 * — an `alpha(0f)` over the top — throws the entrance away rather than delaying it,
 * because [Motion.Out] is three quarters done in its first two frames.
 */
@Composable
fun Modifier.entrance(
    durationMillis: Int,
    fromY: Dp = 0.dp,
    fromScale: Float = 1f,
    delayMillis: Int = 0,
    easing: Easing = Motion.Out,
    origin: TransformOrigin = TransformOrigin.Center,
    enabled: Boolean = true,
): Modifier {
    val suppressed = LocalEntranceSuppressed.current
    val progress = remember { Animatable(if (suppressed) 1f else 0f) }
    LaunchedEffect(enabled) {
        if (enabled && !suppressed) {
            progress.animateTo(1f, tween(durationMillis, delayMillis, easing))
        }
    }
    // Read at composition and applied through the *parameter* overload of
    // graphicsLayer, which is the path `Modifier.alpha` and `Modifier.scale` take.
    // Reading inside the block variant instead defers it to the draw phase and is
    // cheaper, but the block is only re-run when the node is re-placed, and a node
    // that nothing else moves never is. An entrance is a fifth of a second; a few
    // recompositions are the right price for it happening at all.
    //
    // Where this modifier goes in a chain matters — see the note in AGENTS.md.
    val settled = progress.value
    val scale = fromScale + (1f - fromScale) * settled
    return graphicsLayer(
        scaleX = scale,
        scaleY = scale,
        alpha = settled,
        translationY = with(LocalDensity.current) { fromY.toPx() } * (1f - settled),
        transformOrigin = origin,
        // Which is not the same on both platforms, and the reason is a long one:
        // see [entranceCompositing]. It is what keeps the hard shadow fading in
        // with the element it belongs to instead of snapping on at the end.
        compositingStrategy = entranceCompositing,
    )
}

/** `screenIn` — a whole screen arriving. */
@Composable
fun Modifier.screenIn(durationMillis: Int = 190): Modifier =
    entrance(durationMillis, fromY = 6.dp)

/** `plateIn` — the target plate, in whichever of its states it turns up. */
@Composable
fun Modifier.plateIn(): Modifier = entrance(200, fromY = 8.dp)

/** `stampIn` — a pill dropping onto the plate it labels. */
@Composable
fun Modifier.stampIn(durationMillis: Int = 200): Modifier =
    entrance(durationMillis, fromY = (-4).dp)

/** `popIn` — a popover or a badge, scaled up from the corner it hangs off. */
@Composable
fun Modifier.popIn(
    durationMillis: Int = 140,
    origin: TransformOrigin = TransformOrigin(0f, 0f),
    enabled: Boolean = true,
): Modifier = entrance(
    durationMillis = durationMillis,
    fromY = (-5).dp,
    fromScale = 0.95f,
    origin = origin,
    enabled = enabled,
)

/** `cardIn` — one card in a stack. [delayMillis] is what staggers the stack. */
@Composable
fun Modifier.cardIn(durationMillis: Int = 240, delayMillis: Int = 0): Modifier =
    entrance(durationMillis, fromY = 7.dp, delayMillis = delayMillis)

/** `cardDrop` — the about card, which comes down from under the header. */
@Composable
fun Modifier.cardDrop(): Modifier = entrance(210, fromY = (-10).dp)

/** `tokenIn` / `scrimIn` — opacity and nothing else. */
@Composable
fun Modifier.fadeIn(durationMillis: Int = Motion.TOKEN_MS): Modifier =
    entrance(durationMillis, easing = Motion.EaseOut)

/** How much of itself the leaving screen gives up, as the platform's own does. */
private const val PEEL_SCALE = 0.10f

/** And how far it slides clear of the edge it is leaving towards. */
private val PEEL_SHIFT = 12.dp

/** The extra travel it takes once let go, on its way out. */
private val PEEL_EXIT_SHIFT = 28.dp

/** How far ahead of that travel the leaving screen fades: gone by two thirds. */
private const val FADE_LEAD = 1.6f

/**
 * How far under its own size the screen being uncovered waits.
 *
 * The half of the transition that is easy to forget: the platform's own does not
 * merely fade the leaving screen out, it grows the arriving one into place, and
 * that is most of what makes the two read as one movement rather than as a layer
 * being switched off.
 */
private const val REVEAL_INSET = 0.05f

/** It lifts off the screen, so it stops being a rectangle while it does. */
private val PEEL_RADIUS = 26.dp

/**
 * The predictive-back peel: the screen being left shrinks aside, uncovering the one
 * behind it.
 *
 * Two things drive it. [progress] is the drag, through [Motion.PredictiveBack] —
 * which front-loads it so hard that the screen has all but arrived by a fifth of
 * the way and then stops answering the thumb, exactly as the platform's own does;
 * a back swipe should read as a switch being thrown, not as dragging the screen
 * about. [exiting] is what happens after the finger is lifted on a committed
 * gesture: the rest of the travel plus the fade, so the screen leaves rather than
 * being cut.
 *
 * It always leaves to the right, whichever edge the finger came from. Mirroring it
 * seems like the obliging thing to do and is wrong: back means the same direction
 * every time, and the platform agrees — a right-edge swipe in the settings app
 * moves its page right too, and only the system's own arrow changes sides.
 */
@Composable
fun Modifier.peeledBack(progress: () -> Float, exiting: () -> Float = { 0f }): Modifier {
    val density = LocalDensity.current
    val shiftPx = with(density) { PEEL_SHIFT.toPx() }
    val exitShiftPx = with(density) { PEEL_EXIT_SHIFT.toPx() }
    val radiusPx = with(density) { PEEL_RADIUS.toPx() }
    val line = IloTokiTheme.colors.line
    // The values are read through lambdas and only inside the layer and draw blocks,
    // never at composition. A back gesture is the one animation in this app with two
    // whole screens on stage, and reading it at composition recomposed both of them
    // on every frame of the drag: measured on a Pixel 6, 38% of the frames were
    // janked and the UI thread was the reason. Deferred, the gesture costs no
    // recompositions at all.
    return graphicsLayer {
        val travelled = travelled(progress(), exiting())
        val leaving = exiting().coerceIn(0f, 1f)
        scaleX = 1f - PEEL_SCALE * travelled
        scaleY = 1f - PEEL_SCALE * travelled
        translationX = shiftPx * travelled + exitShiftPx * leaving
        // Opaque for the whole drag — two screens readable at once is worse than
        // either — and then gone well before the travel is, so that the moment
        // where both are legible is as short as this look can afford. These plates
        // are hard black on cream; they overlap far more loudly than the softer
        // surfaces the platform's own transition was drawn for.
        alpha = 1f - (leaving * FADE_LEAD).coerceAtMost(1f)
        // The far edge is the pivot, so all of the shrink opens the gap on the left.
        transformOrigin = TransformOrigin(1f, 0.5f)
        shape = RoundedCornerShape((radiusPx * travelled).toDp())
        clip = travelled > 0f
    }.drawWithContent {
        drawContent()
        val travelled = travelled(progress(), exiting())
        if (travelled <= 0f) return@drawWithContent
        // The same 2 dp line every lifted surface in this design carries. It is not
        // decoration in dark: the screen and the one it is lifting off are both the
        // same near-black, and without the outline the two simply bleed together.
        val w = BorderWidth.toPx()
        drawRoundRect(
            color = line,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius((radiusPx * travelled - w / 2).coerceAtLeast(0f)),
            style = Stroke(w),
        )
    }
}

/** How far along the peel is: the drag, then whatever the exit carries it the rest. */
private fun travelled(progress: Float, exiting: Float): Float {
    val dragged = Motion.PredictiveBack.transform(progress.coerceIn(0f, 1f))
    return dragged + (1f - dragged) * exiting.coerceIn(0f, 1f)
}

/**
 * The other side of the peel: the screen being uncovered, waiting a little under
 * its own size and growing into place as the one on top leaves.
 *
 * It is deliberately still during the drag — only a sliver of it is showing, and
 * anything moving in that sliver would be noise — and does all its growing while
 * the leaving screen fades, arriving at exactly full size in the frame the two
 * swap over, so that swap draws nothing new.
 */
fun Modifier.revealedBack(exiting: () -> Float): Modifier = graphicsLayer {
    val scale = 1f - REVEAL_INSET * (1f - exiting().coerceIn(0f, 1f))
    scaleX = scale
    scaleY = scale
}

/**
 * `dotPulse` — the loje dot swells once every 2.6 s.
 *
 * It is the only thing in the app allowed to move on its own, and it stays still
 * for three quarters of the cycle so that it reads as a heartbeat rather than a
 * throb: 1.18× for a fifth of a second, then nothing.
 */
@Composable
fun Modifier.pulse(): Modifier {
    val loop = rememberInfiniteTransition(label = "dotPulse")
    val scale by loop.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2600
                1f at 0 using Motion.EaseInOut
                1f at 1872 using Motion.EaseInOut // 72%
                1.18f at 2132 using Motion.EaseInOut // 82%
                1f at 2600
            },
        ),
        label = "dotScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** `calloutNudge` — one 3 dp hop every 4.2 s, to be noticed without nagging. */
@Composable
fun Modifier.nudge(): Modifier {
    val loop = rememberInfiniteTransition(label = "calloutNudge")
    val hop by loop.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                0f at 0 using Motion.EaseInOut
                0f at 3696 using Motion.EaseInOut // 88%
                -3f at 3948 using Motion.EaseInOut // 94%
                0f at 4200
            },
        ),
        label = "calloutHop",
    )
    return graphicsLayer { translationY = hop.dp.toPx() }
}
