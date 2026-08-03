package one.larkin.ilotoki.ui

import androidx.compose.ui.graphics.CompositingStrategy

/**
 * How an entrance's fade is composited — and it is not the same on both platforms.
 *
 * The hard shadow is painted *outside* the element it belongs to, so an offscreen
 * buffer the size of that element cuts it off: on Android every entrance dropped
 * its shadow for the whole animation and snapped it back on at the end, when the
 * layer went away. `ModulateAlpha` fixes that by applying the alpha to each draw
 * command instead of to a buffer — but on Skia it also stops some filled surfaces
 * being drawn at all (the history screen's ALL chip vanished outright), and iOS
 * loses the shadow for two frames rather than for the whole animation.
 *
 * So: the cure where the disease is, and the default where the cure is worse.
 */
internal expect val entranceCompositing: CompositingStrategy
