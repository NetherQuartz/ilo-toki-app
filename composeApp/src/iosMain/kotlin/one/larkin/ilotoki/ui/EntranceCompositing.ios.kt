package one.larkin.ilotoki.ui

import androidx.compose.ui.graphics.CompositingStrategy

/** The default: on Skia, modulating the alpha loses whole filled surfaces. */
internal actual val entranceCompositing: CompositingStrategy = CompositingStrategy.Auto
