package one.larkin.ilotoki.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The whole icon set, drawn from rectangles, triangles and circles on a 100×100
 * grid. There is no icon font and no hand-drawn path beyond that — the shapes are
 * the design language, so they are built here rather than imported, and the
 * geometry below is the same as the design bundle's SVGs.
 *
 * **Stroke weights are chosen for the size each icon is drawn at, not shared.**
 * The grid is scaled to whatever a call site asks for, so what reaches the screen
 * is `stroke ÷ 100 × size`, and a single grid stroke across icons of different
 * sizes comes out visibly uneven — the clock and the back chevron sat at 1.54 and
 * 1.62 dp against the gear's 2.30 and read as thin next to it. Everything aims at
 * about 2.2 dp instead, which is also [BorderWidth], the line every surface in
 * this design is outlined with. Change a size at a call site and the stroke here
 * has to move with it:
 *
 * | icon | drawn at | stroke | on screen |
 * |---|---|---|---|
 * | gear | 23 dp | 10 | 2.30 dp |
 * | clock | 22 dp | 10 | 2.20 dp |
 * | chevron | 18 dp | 12 | 2.16 dp |
 * | swap | 26 dp | 8.5 | 2.21 dp |
 * | mark, header | 23 dp | 9.5 | 2.19 dp |
 * | mark, elsewhere | 29 dp | 7 | 2.03 dp |
 * | close | 14 dp | 15.5 | 2.17 dp |
 */
object IloTokiIcons {

    /**
     * Bar widths of the mark on the 100 grid — see [Mark].
     *
     * Two of them, because the weight that reaches the eye is the bar divided by
     * the grid and multiplied by the size drawn at, so one number cannot serve a
     * 23 dp tile and a 29 dp one. The header's mark stands in a row with the gear
     * and the clock and has to match them; the about card's stands alone beside
     * the wordmark, where the design's original 7 is what looks right.
     */
    private const val MARK_BAR = 7f

    private const val MARK_BAR_HEAVY = 9.5f

    /**
     * `ilo` box with three `toki` rays. Header, about card, empty-state watermark.
     *
     * Every bar is [MARK_BAR] wide and the outer bounds are fixed, so the whole
     * figure follows from one number: the box spans x 16..84 and y 32..88, the
     * stem and the middle ray are centred on 50, and the leaning rays on 23.5 and
     * 76.5, which are also their pivots. 9.5 puts it at 2.19 dp in the header's
     * 23 dp — the weight the rest of the icon set carries — where the original 7
     * left it at 1.61 and reading light beside them.
     *
     * The launcher and status bar icons are their own drawables under
     * `androidMain/res` and do **not** follow this: they are the same silhouette
     * at the original 7, and were deliberately left there.
     */
    val Mark: ImageVector by lazy { markOf(MARK_BAR) }

    /** The same figure with heavier bars, for the header tile. See [MARK_BAR]. */
    val MarkHeavy: ImageVector by lazy { markOf(MARK_BAR_HEAVY) }

    private fun markOf(w: Float): ImageVector =
        ImageVector.Builder(
            name = "mark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 100f,
            viewportHeight = 100f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // The box, its lid and floor, and the middle ray.
                rect(16f, 32f, 68f, w)
                rect(16f, 88f - w, 68f, w)
                rect(16f, 32f, w, 56f)
                rect(84f - w, 32f, w, 56f)
                rect(50f - w / 2f, 32f, w, 63f)
                rect(50f - w / 2f, 5f, w, 16f)
            }
            // The outer two rays lean away from the middle one.
            group(name = "left-ray", rotate = -38f, pivotX = 23.5f, pivotY = 15f) {
                path(fill = SolidColor(Color.Black)) { rect(23.5f - w / 2f, 7f, w, 16f) }
            }
            group(name = "right-ray", rotate = 38f, pivotX = 76.5f, pivotY = 15f) {
                path(fill = SolidColor(Color.Black)) { rect(76.5f - w / 2f, 7f, w, 16f) }
            }
        }.build()

    /** Settings. Carries the update dot. */
    val Gear: ImageVector by lazy {
        ImageVector.Builder(
            name = "gear",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 100f,
            viewportHeight = 100f,
        ).apply {
            path(fill = SolidColor(Color.Black)) { teeth() }
            group(name = "diagonal-teeth", rotate = 45f, pivotX = 50f, pivotY = 50f) {
                path(fill = SolidColor(Color.Black)) { teeth() }
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 10f,
            ) { circle(50f, 50f, 25f) }
        }.build()
    }

    /** History. */
    val Clock: ImageVector by lazy {
        ImageVector.Builder(
            name = "clock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 100f,
            viewportHeight = 100f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 10f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) { circle(50f, 50f, 34f) }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 10f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(50f, 27f)
                lineTo(50f, 52f)
                lineTo(68f, 61f)
            }
        }.build()
    }

    /**
     * The knob between the plates. Vertical, unlike the `swap_horiz` it replaces —
     * the two plates are stacked, so the arrows point along the same axis.
     */
    val SwapVertical: ImageVector by lazy {
        filled("swap-vertical") {
            // The bars carry the weight; the arrowheads are already solid, so only
            // these move. 8.5 wide keeps them centred on 27.5 and 72.5.
            rect(23.25f, 34f, 8.5f, 42f)
            moveTo(27.5f, 13f); lineTo(43f, 40f); lineTo(12f, 40f); close()
            rect(68.25f, 24f, 8.5f, 42f)
            moveTo(72.5f, 87f); lineTo(57f, 60f); lineTo(88f, 60f); close()
        }
    }

    /**
     * The `›` affordance, drawn rather than typed: Space Grotesk has no single
     * guillemet, so the character falls back to a mismatched `>` from another font.
     */
    val Chevron: ImageVector by lazy {
        ImageVector.Builder(
            name = "chevron",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 100f,
            viewportHeight = 100f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 12f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(38f, 22f)
                lineTo(66f, 50f)
                lineTo(38f, 78f)
            }
        }.build()
    }

    /** Dismiss. Drawn rather than typed, for the same reason as [Chevron]. */
    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 100f,
            viewportHeight = 100f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 15.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(27f, 27f); lineTo(73f, 73f)
                moveTo(73f, 27f); lineTo(27f, 73f)
            }
        }.build()
    }

    /** Inside model plates only — never in the header. */
    val Download: ImageVector by lazy {
        filled("download") {
            rect(46.5f, 12f, 7f, 40f)
            moveTo(50f, 74f); lineTo(28f, 45f); lineTo(72f, 45f); close()
            rect(16f, 80f, 68f, 7f)
        }
    }
}

private fun filled(name: String, body: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 100f,
        viewportHeight = 100f,
    ).apply { path(fill = SolidColor(Color.Black), pathBuilder = body) }.build()

/** The four axis-aligned gear teeth; the diagonal four are these rotated by 45°. */
private fun PathBuilder.teeth() {
    rect(44.5f, 3f, 11f, 19f)
    rect(44.5f, 78f, 11f, 19f)
    rect(3f, 44.5f, 19f, 11f)
    rect(78f, 44.5f, 19f, 11f)
}

private fun PathBuilder.rect(x: Float, y: Float, width: Float, height: Float) {
    moveTo(x, y)
    lineTo(x + width, y)
    lineTo(x + width, y + height)
    lineTo(x, y + height)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    moveTo(cx - radius, cy)
    arcTo(radius, radius, 0f, true, true, cx + radius, cy)
    arcTo(radius, radius, 0f, true, true, cx - radius, cy)
    close()
}
