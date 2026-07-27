package one.larkin.ilotoki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.larkin.ilotoki.ui.theme.IloTokiTheme

/** The border every surface in this design has. Keyboard keys are the only 1.5 dp one. */
val BorderWidth = 2.dp

/**
 * Fill and hard shadow behind the content, outline **over** it.
 *
 * The order matters: several surfaces are filled by a child that reaches their own
 * edge — the growing accent behind the slab's label, the progress line, the active
 * cell of a segmented control. Drawn behind, the outline disappears under all of
 * them; drawn last, the 2 dp ink line stays unbroken, which is the whole look.
 *
 * The shadow is drawn rather than elevated on purpose: `Modifier.shadow` is blurred
 * and alpha-graded, which is the one thing this look never uses. It is painted
 * outside the element's bounds, so whatever lays a plate out has to leave [shadow]
 * worth of room for it — the 13–14 dp gaps in the layouts already do.
 *
 * Doing the fill and the outline here, rather than with `background()` + `border()`,
 * is also what makes the dashed variants (the empty target plate, models that are
 * not on the device) possible at all — `Modifier.border` has no dash.
 */
private fun Modifier.surface(
    background: Color,
    border: Color,
    radius: Dp,
    shadow: Dp,
    shadowColor: Color,
    dashed: Boolean,
    borderWidth: Dp = BorderWidth,
): Modifier = drawWithContent {
    // Pills ask for radius 999; clamp so the corner never exceeds half the box,
    // which some backends draw as a straight edge rather than as a full round.
    val px = radius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
    val r = CornerRadius(px)
    if (shadow > 0.dp) {
        val d = shadow.toPx()
        drawRoundRect(shadowColor, topLeft = Offset(d, d), size = size, cornerRadius = r)
    }
    drawRoundRect(background, size = size, cornerRadius = r)

    drawContent()

    if (borderWidth > 0.dp) {
        val w = borderWidth.toPx()
        drawRoundRect(
            color = border,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius((px - w / 2).coerceAtLeast(0f)),
            style = Stroke(
                width = w,
                pathEffect = if (dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(w * 3, w * 2.5f))
                } else {
                    null
                },
            ),
        )
    }
}

/**
 * The bottom inset for a screen: the navigation bar, or the keyboard when it is up.
 *
 * A `union` rather than `navigationBarsPadding().imePadding()` chained together.
 * The chained pair depends on one modifier consuming the inset before the other
 * reads it, and on Android it was measured flipping between 0 and the gesture bar's
 * height for about half a second at a time — which showed up as the plates quietly
 * breathing in and out. The union takes the larger of the two, which is what is
 * wanted anyway: an open keyboard already covers the navigation bar.
 */
@Composable
fun Modifier.screenBottomInsets(): Modifier =
    windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))

/** A press that leaves no ripple — nothing in this design has one. */
@Composable
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * Paper surface, 2 dp outline, hard shadow. The base of every screen.
 *
 * [contentColor] is published as [LocalContentColor] so text and icons inside pick
 * the right ink without every call site restating it — that matters most on the
 * accent and laso plates, where it is not the theme's ink.
 */
@Composable
fun Plate(
    modifier: Modifier = Modifier,
    background: Color = IloTokiTheme.colors.paper,
    contentColor: Color = IloTokiTheme.colors.ink,
    border: Color = IloTokiTheme.colors.line,
    radius: Dp = 22.dp,
    shadow: Dp = 3.dp,
    dashed: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    clipContent: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = IloTokiTheme.colors
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .surface(background, border, radius, shadow, colors.shadow, dashed)
            .then(if (clipContent) Modifier.clip(shape) else Modifier)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = { content() })
    }
}

/** Text. A thin wrapper so the ink and the type scale never have to be repeated. */
@Composable
fun AppText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    val resolved = if (color.isSpecified()) color else LocalContentColor.current
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = resolved, textAlign = textAlign ?: style.textAlign),
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun Color.isSpecified() = this != Color.Unspecified

/**
 * The pill label the design calls a stamp: ink fill, background-coloured text.
 *
 * [glyph] is a sitelen pona word rendered as content, not as an icon — `toki`
 * before a toki pona pair label, `olin` on a marked history card.
 */
@Composable
fun Stamp(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = IloTokiTheme.colors.ink,
    contentColor: Color = IloTokiTheme.colors.bg,
    border: Color? = null,
    glyph: String? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val type = IloTokiTheme.type
    Row(
        modifier = modifier
            .surface(
                background = background,
                border = border ?: Color.Transparent,
                radius = 999.dp,
                shadow = 0.dp,
                shadowColor = Color.Transparent,
                dashed = false,
                borderWidth = if (border == null) 0.dp else BorderWidth,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            AppText(glyph, type.sitelen.copy(fontSize = 15.sp, lineHeight = 15.sp), color = contentColor)
        }
        AppText(text, type.stamp, color = contentColor)
        trailing?.invoke(this)
    }
}

/**
 * The 38 dp square that carries the header icons and the back chevron.
 *
 * [dot] is the loje signal — an update waiting, or no translator on the device.
 * It sits outside the square's corner, which is why nothing here clips.
 */
@Composable
fun IconSquare(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 23.dp,
    dot: Boolean = false,
) {
    val colors = IloTokiTheme.colors
    Box(modifier = modifier.size(38.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .surface(colors.paper, colors.line, 12.dp, 2.dp, colors.shadow, dashed = false)
                .tap(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            VectorIcon(icon, contentDescription, colors.ink, iconSize)
        }
        if (dot) SignalDot(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp))
    }
}

/** The same square, with a chevron instead of an icon. */
@Composable
fun BackSquare(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = IloTokiTheme.colors
    Box(
        modifier = modifier
            .size(38.dp)
            .surface(colors.paper, colors.line, 12.dp, 2.dp, colors.shadow, dashed = false)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        VectorIcon(
            icon = IloTokiIcons.Chevron,
            contentDescription = "back",
            tint = colors.ink,
            size = 18.dp,
            modifier = Modifier.rotate(180f),
        )
    }
}

/** The `›` that ends a row leading somewhere else. */
@Composable
fun Chevron(tint: Color = IloTokiTheme.colors.muted, size: Dp = 16.dp) {
    VectorIcon(IloTokiIcons.Chevron, null, tint, size)
}

/** 12 dp loje circle with a 2 dp ink ring. The only red thing on a quiet screen. */
@Composable
fun SignalDot(modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val colors = IloTokiTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .surface(colors.loje, colors.line, size / 2, 0.dp, Color.Transparent, dashed = false),
    )
}

/** The accent tile the mark sits on. Tapping it opens *about*. */
@Composable
fun MarkTile(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    radius: Dp = 11.dp,
    markSize: Dp = 23.dp,
) {
    val colors = IloTokiTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .surface(colors.accent, colors.line, radius, 2.dp, colors.shadow, dashed = false)
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        VectorIcon(IloTokiIcons.Mark, "ilo toki", colors.onAccent, markSize)
    }
}

/** 46 × 26 pill toggle: ink knob, accent fill when on. */
@Composable
fun AppToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = IloTokiTheme.colors
    Box(
        modifier = modifier
            .size(46.dp, 26.dp)
            .surface(
                background = if (checked) colors.accent else Color.Transparent,
                border = colors.line,
                radius = 13.dp,
                shadow = 0.dp,
                shadowColor = Color.Transparent,
                dashed = false,
            )
            .tap { onCheckedChange(!checked) }
            // 4 dp, the same as the vertical gap the 18 dp knob leaves in 26 dp of
            // height: that puts the knob's centre 13 dp in, concentric with the
            // pill's own 13 dp corner radius. At 2 dp it crowds the outline.
            .padding(horizontal = 4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .background(if (checked) colors.onAccent else colors.ink, RoundedCornerShape(9.dp)),
        )
    }
}

/**
 * Two or more cells inside one outline, the active one accent-filled.
 *
 * The dividers are the cells' own left borders rather than separate lines, so the
 * control keeps a single 2 dp outline however many cells it has.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .surface(
                background = Color.Transparent,
                border = colors.line,
                radius = 13.dp,
                shadow = 0.dp,
                shadowColor = Color.Transparent,
                dashed = false,
            )
            .clip(RoundedCornerShape(13.dp)),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (active) colors.accent else Color.Transparent)
                    // After the fill, not before it: a divider drawn first would be
                    // painted over by the active cell's own accent.
                    .then(
                        if (index > 0) {
                            Modifier.drawBehind {
                                val w = BorderWidth.toPx()
                                drawRect(colors.line, size = Size(w, size.height))
                            }
                        } else {
                            Modifier
                        },
                    )
                    .tap { onSelect(index) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = label,
                    style = type.stamp.copy(fontSize = 12.5.sp, letterSpacing = 0.sp),
                    color = if (active) colors.onAccent else colors.ink,
                )
            }
        }
    }
}

/** Outlined paper chip — sample phrases, history filters, the about card's links. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: Color = IloTokiTheme.colors.paper,
    contentColor: Color = IloTokiTheme.colors.ink,
    border: Color = IloTokiTheme.colors.line,
    style: TextStyle = IloTokiTheme.type.meta.copy(fontSize = 11.5.sp),
    radius: Dp = 13.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
) {
    Box(
        modifier = modifier
            .surface(background, border, radius, 0.dp, Color.Transparent, dashed = false)
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) {
        AppText(text, style, color = contentColor, maxLines = 1)
    }
}

/**
 * The download line: 12 dp tall, under the header, never in the way.
 *
 * It is the whole of the download UI on the translator screen — the point of the
 * redesign is that fetching a model no longer takes the screen hostage.
 */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val colors = IloTokiTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .surface(colors.paper, colors.line, 8.dp, 0.dp, Color.Transparent, dashed = false)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(colors.accent),
        )
    }
}

/** Renders an [ImageVector] tinted flat — the set has no multi-colour icon. */
@Composable
fun VectorIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

