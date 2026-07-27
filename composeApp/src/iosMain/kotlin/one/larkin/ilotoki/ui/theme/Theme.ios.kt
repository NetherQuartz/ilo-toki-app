package one.larkin.ilotoki.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** iOS has no user-set system palette to borrow, so the row is not offered. */
actual val systemColoursAvailable: Boolean = false

@Composable
actual fun systemAccentOrNull(): Color? = null
