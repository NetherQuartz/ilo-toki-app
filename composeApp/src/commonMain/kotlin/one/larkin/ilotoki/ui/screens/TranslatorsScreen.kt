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
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.larkin.ilotoki.Language
import one.larkin.ilotoki.MainViewModel
import one.larkin.ilotoki.freeDiskBytes
import one.larkin.ilotoki.model.ModelState
import one.larkin.ilotoki.model.ModelStatus
import one.larkin.ilotoki.ui.AppText
import one.larkin.ilotoki.ui.Chip
import one.larkin.ilotoki.ui.Motion
import one.larkin.ilotoki.ui.Plate
import one.larkin.ilotoki.ui.cardIn
import one.larkin.ilotoki.ui.screenBottomInsets
import one.larkin.ilotoki.ui.screenIn
import one.larkin.ilotoki.ui.SignalDot
import one.larkin.ilotoki.ui.Stamp
import one.larkin.ilotoki.ui.gibLabel
import one.larkin.ilotoki.ui.roundGibLabel
import one.larkin.ilotoki.ui.animateStateColour
import one.larkin.ilotoki.ui.theme.IloTokiTheme

/**
 * The only place models are managed, and the only place a download starts by hand.
 *
 * Each catalog entry gets a plate whose stamp sits on its top edge, so the states
 * — in use, newer, on device, superseded, not on device — are readable at a glance
 * without a legend.
 */
@Composable
fun TranslatorsScreen(
    models: List<ModelState>,
    status: ModelStatus,
    viewModel: MainViewModel,
    onStartedDownload: () -> Unit,
) {
    // Read once per visit: it is a syscall, and the figure is context, not a gauge.
    val free = remember(models) { freeDiskBytes() }
    val taken = models.sumOf { it.bytesOnDisk }
    val files = models.count { it.downloaded }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenIn()
            .screenBottomInsets()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        StoragePlate(taken = taken, files = files, free = free)
        models.forEachIndexed { index, model ->
            ModelPlate(
                model = model,
                downloading = status is ModelStatus.Downloading && model.selected,
                // The stack deals itself out rather than landing at once.
                delayMillis = index * 60,
                onUse = {
                    val wasOnDevice = model.downloaded
                    viewModel.selectModel(model.spec)
                    // Choosing something that is not here yet is a download, and the
                    // download belongs on the main screen where it stays out of the way.
                    if (!wasOnDevice) onStartedDownload()
                },
                onDelete = { viewModel.deleteModel(model.spec) },
            )
        }
        AppText(
            text = "a dropped download resumes by itself · switching keeps both files " +
                "until you delete one",
            style = IloTokiTheme.type.meta.copy(fontSize = 11.5.sp),
            color = IloTokiTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/**
 * What the app is costing in storage. No bar: total device capacity is not the
 * comparison anyone needs when one model is a gigabyte and the phone has a hundred.
 */
@Composable
private fun StoragePlate(taken: Long, files: Int, free: Long) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type
    Plate(
        modifier = Modifier.fillMaxWidth(),
        radius = 18.dp,
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 13.dp),
    ) {
        // Two rows, not two columns: side-by-side columns of different heights can
        // only be aligned at one end, so the shorter one's label slid down. Row one
        // holds both labels, row two both figures, and the figures sit on a shared
        // baseline rather than a shared box edge — 22 sp and 16 sp have different
        // descents, so bottom-aligning them would still look a pixel out.
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText("TAKEN BY MODELS", type.section)
                AppText("FREE", type.section, color = colors.muted)
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth()) {
                AppText(gibLabel(taken), type.bigNumber, Modifier.alignByBaseline())
                Spacer(Modifier.width(7.dp))
                AppText(
                    text = if (files == 1) "1 file" else "$files files",
                    style = type.meta.copy(fontSize = 11.5.sp),
                    color = colors.muted,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.weight(1f))
                AppText(
                    text = roundGibLabel(free),
                    style = type.bigNumber.copy(fontSize = 16.sp),
                    color = colors.muted,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

/** One catalog entry, wearing its state as a stamp on the plate's top edge. */
@Composable
private fun ModelPlate(
    model: ModelState,
    downloading: Boolean,
    delayMillis: Int,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type

    val inUse = model.selected && model.downloaded
    val newer = !model.downloaded && !model.spec.deprecated && !model.selected
    // Not here and not worth getting: kept listed only for whoever already has it.
    val faded = !model.downloaded && !newer

    val stamp = when {
        inUse -> "IN USE"
        downloading -> "COMING DOWN"
        newer -> "NEWER"
        model.downloaded -> "ON DEVICE"
        model.spec.deprecated -> "SUPERSEDED"
        else -> "NOT ON DEVICE"
    }
    val background = when {
        inUse -> colors.laso
        newer -> colors.accent
        else -> colors.paper
    }
    val foreground = when {
        inUse -> colors.onLaso
        newer -> colors.onAccent
        else -> colors.ink
    }

    Box(Modifier.fillMaxWidth().cardIn(delayMillis = delayMillis).padding(top = 12.dp)) {
        Plate(
            modifier = Modifier.fillMaxWidth().alpha(if (faded) 0.75f else 1f),
            background = background,
            contentColor = foreground,
            border = if (faded) colors.faint else colors.line,
            shadow = if (faded) 0.dp else 3.dp,
            dashed = faded,
            contentPadding = PaddingValues(16.dp),
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (newer) SignalDot(size = 11.dp)
                    AppText(model.spec.displayName, type.modelTitle)
                }
                Spacer(Modifier.height(7.dp))
                AppText(
                    text = buildString {
                        append(model.spec.quantization)
                        append(" · ")
                        append(gibLabel(model.spec.sizeBytes))
                        append(" · ")
                        append(Language.entries.size)
                        append(" languages")
                        if (model.spec.deprecated) append(" · superseded")
                    },
                    style = type.meta.copy(fontSize = 12.5.sp),
                )
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        text = when {
                            inUse -> "in use"
                            downloading -> "getting it"
                            model.downloaded -> "switch to it"
                            else -> "get it · ${gibLabel(model.spec.sizeBytes)}"
                        },
                        onClick = if (inUse || downloading) null else onUse,
                        background = if (newer) colors.onAccent else Color.Transparent,
                        contentColor = if (newer) colors.accent else foreground,
                        border = if (newer) colors.onAccent else foreground.copy(alpha = 0.5f),
                        style = type.stamp.copy(fontSize = 12.sp, letterSpacing = 0.72.sp),
                        radius = 999.dp,
                        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 10.dp),
                        modifier = Modifier.alpha(if (inUse) 0.6f else 1f),
                    )
                    // Only offer to free space that is actually taken, and never
                    // the file the app is translating with right now.
                    if (model.downloaded && !inUse) {
                        Chip(
                            text = "free ${gibLabel(model.bytesOnDisk)}",
                            onClick = onDelete,
                            background = Color.Transparent,
                            contentColor = colors.loje,
                            border = colors.loje.copy(alpha = 0.5f),
                            style = type.stamp.copy(fontSize = 12.sp, letterSpacing = 0.72.sp),
                            radius = 999.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
        // The stamp is where a switch shows up — «NOT ON DEVICE» becomes «IN USE» on
        // the plate you just tapped — so its two colours cross rather than cut.
        val stampSpec = tween<Color>(Motion.SLOW_COLOUR_MS, easing = Motion.EaseOut)
        val stampBackground by animateStateColour(
            targetValue = when {
                inUse -> colors.accent
                newer -> colors.onAccent
                faded && model.spec.deprecated -> colors.loje
                else -> colors.paper
            },
            animationSpec = stampSpec,
            label = "stampBackground",
        )
        val stampInk by animateStateColour(
            targetValue = when {
                inUse -> colors.onAccent
                newer -> colors.accent
                faded && model.spec.deprecated -> colors.onLaso
                else -> colors.ink
            },
            animationSpec = stampSpec,
            label = "stampInk",
        )
        Stamp(
            text = stamp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-12).dp),
            background = stampBackground,
            contentColor = stampInk,
            border = colors.line,
        )
    }
}
