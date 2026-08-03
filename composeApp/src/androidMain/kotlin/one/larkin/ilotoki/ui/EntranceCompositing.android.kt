package one.larkin.ilotoki.ui

import androidx.compose.ui.graphics.CompositingStrategy

/** Per draw command, so the shadow outside the element's bounds survives the fade. */
internal actual val entranceCompositing: CompositingStrategy =
    CompositingStrategy.ModulateAlpha
