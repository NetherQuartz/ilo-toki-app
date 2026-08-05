package one.larkin.ilotoki.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.larkin.ilotoki.MainViewModel
import one.larkin.ilotoki.data.AppSettings
import one.larkin.ilotoki.model.ModelCatalog
import one.larkin.ilotoki.model.ModelState
import one.larkin.ilotoki.ui.AppText
import one.larkin.ilotoki.ui.AppToggle
import one.larkin.ilotoki.ui.BorderWidth
import one.larkin.ilotoki.ui.Chevron
import one.larkin.ilotoki.ui.Plate
import one.larkin.ilotoki.ui.screenBottomInsets
import one.larkin.ilotoki.ui.screenIn
import one.larkin.ilotoki.ui.SegmentedControl
import one.larkin.ilotoki.ui.SignalDot
import one.larkin.ilotoki.ui.Stamp
import one.larkin.ilotoki.ui.stampIn
import one.larkin.ilotoki.ui.gibLabel
import one.larkin.ilotoki.ui.tap
import one.larkin.ilotoki.ui.theme.IloTokiTheme
import one.larkin.ilotoki.ui.theme.ThemeSetting
import one.larkin.ilotoki.ui.theme.systemColoursAvailable

/**
 * Settings.
 *
 * There is no script switch here on purpose: latin versus sitelen pona is a
 * property of the text on screen, so it is flipped from the plate showing it.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    models: List<ModelState>,
    hasUpdate: Boolean,
    standingIn: Boolean,
    historyCount: Int,
    viewModel: MainViewModel,
    onOpenModels: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenIn()
            .screenBottomInsets()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        AppearancePlate(settings, viewModel)
        TranslatorsPlate(models, hasUpdate, standingIn, onOpenModels)
        ListPlate(settings, historyCount, viewModel, onOpenHistory, onOpenAbout)
    }
}

@Composable
private fun AppearancePlate(settings: AppSettings, viewModel: MainViewModel) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type
    Plate(Modifier.fillMaxWidth(), radius = 20.dp, contentPadding = PaddingValues(14.dp)) {
        Column {
            AppText("APPEARANCE", type.section)
            Spacer(Modifier.height(12.dp))
            // The mock has two cells; «system» is the third because it is also the
            // default, and without it there would be no way back to following the
            // phone once either of the other two had been chosen.
            SegmentedControl(
                options = listOf("light", "dark", "system"),
                selectedIndex = when (settings.theme) {
                    ThemeSetting.Light -> 0
                    ThemeSetting.Dark -> 1
                    ThemeSetting.System -> 2
                },
                onSelect = {
                    viewModel.setTheme(
                        when (it) {
                            0 -> ThemeSetting.Light
                            1 -> ThemeSetting.Dark
                            else -> ThemeSetting.System
                        },
                    )
                },
            )
            // Omitted, not disabled, where the platform has no palette to borrow.
            if (systemColoursAvailable) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = colors.faint,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = BorderWidth.toPx(),
                            )
                        }
                        // Before the padding, so the whole row answers rather than
                        // just the strip the text sits in. The toggle's own tap
                        // consumes a press that lands on it, so it fires once.
                        .tap { viewModel.setUseSystemColours(!settings.useSystemColours) }
                        .padding(top = 13.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            AppText("system colours", type.row)
                            Spacer(Modifier.height(4.dp))
                            AppText(
                                text = "takes the wallpaper palette instead of the yellow",
                                style = type.meta.copy(fontSize = 11.sp),
                                color = colors.muted,
                            )
                        }
                        AppToggle(settings.useSystemColours, viewModel::setUseSystemColours)
                    }
                }
            }
        }
    }
}

/** The way in to model management, and the only place an update is announced. */
@Composable
private fun TranslatorsPlate(
    models: List<ModelState>,
    hasUpdate: Boolean,
    standingIn: Boolean,
    onOpenModels: () -> Unit,
) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type
    // The chosen translator, even when another is standing in for it while it
    // downloads — this row is about the choice, and what is actually answering in
    // the meantime is a detail for the screen it leads to.
    val selected = models.firstOrNull { it.selected }?.spec ?: ModelCatalog.default

    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Plate(
            modifier = Modifier.fillMaxWidth(),
            background = colors.accent,
            contentColor = colors.onAccent,
            radius = 20.dp,
            contentPadding = PaddingValues(14.dp),
            onClick = onOpenModels,
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasUpdate || standingIn) SignalDot(size = 11.dp)
                    AppText("TRANSLATORS", type.section)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        AppText(selected.displayName, type.modelTitle.copy(fontSize = 15.5.sp))
                        Spacer(Modifier.height(3.dp))
                        AppText(
                            text = "${selected.quantization} · ${gibLabel(selected.sizeBytes)}",
                            style = type.meta,
                        )
                    }
                    Chevron(tint = colors.onAccent, size = 18.dp)
                }
            }
        }
        // Same words the translators screen uses, so the plate and the screen it
        // opens are saying the same thing about the same model.
        val stamp = when {
            hasUpdate -> "NEWER AVAILABLE"
            standingIn -> "NOT ON DEVICE"
            else -> null
        }
        if (stamp != null) {
            Stamp(
                text = stamp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-14).dp, y = (-12).dp)
                    .stampIn(240),
                background = colors.onAccent,
                contentColor = colors.accent,
                border = colors.line,
            )
        }
    }
}

@Composable
private fun ListPlate(
    settings: AppSettings,
    historyCount: Int,
    viewModel: MainViewModel,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type
    Plate(
        modifier = Modifier.fillMaxWidth(),
        radius = 20.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
    ) {
        Column {
            SettingsRow(
                divider = true,
                onClick = { viewModel.setHaptics(!settings.haptics) },
            ) {
                AppText("haptic feedback", type.row)
                AppToggle(settings.haptics, viewModel::setHaptics)
            }
            SettingsRow(
                divider = true,
                onClick = { viewModel.setKeepHistory(!settings.keepHistory) },
            ) {
                AppText("keep history", type.row)
                AppToggle(settings.keepHistory, viewModel::setKeepHistory)
            }
            SettingsRow(divider = true, onClick = onOpenHistory) {
                AppText("history", type.row)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText("$historyCount", type.meta.copy(fontSize = 13.sp), color = colors.muted)
                    Chevron()
                }
            }
            SettingsRow(divider = false, onClick = onOpenAbout) {
                AppText("about ilo toki", type.row)
                Chevron()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    divider: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = IloTokiTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)
            .then(
                if (divider) {
                    Modifier.drawBehind {
                        drawLine(
                            color = colors.faint,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = BorderWidth.toPx(),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = { content() },
    )
}
