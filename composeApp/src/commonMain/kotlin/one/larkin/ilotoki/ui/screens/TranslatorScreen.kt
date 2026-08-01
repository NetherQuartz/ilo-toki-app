package one.larkin.ilotoki.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import one.larkin.ilotoki.Language
import one.larkin.ilotoki.MainViewModel
import one.larkin.ilotoki.SamplePhrases
import one.larkin.ilotoki.TOKI_PONA
import one.larkin.ilotoki.TranslatorState
import one.larkin.ilotoki.fractionOrZero
import one.larkin.ilotoki.model.ModelCatalog
import one.larkin.ilotoki.model.ModelSpec
import one.larkin.ilotoki.model.ModelState
import one.larkin.ilotoki.model.ModelStatus
import one.larkin.ilotoki.ui.AppText
import one.larkin.ilotoki.ui.Chip
import one.larkin.ilotoki.ui.IloTokiIcons
import one.larkin.ilotoki.ui.Plate
import one.larkin.ilotoki.ui.screenBottomInsets
import one.larkin.ilotoki.ui.Stamp
import one.larkin.ilotoki.ui.VectorIcon
import one.larkin.ilotoki.ui.formatGiB
import one.larkin.ilotoki.ui.gibLabel
import one.larkin.ilotoki.ui.tap
import one.larkin.ilotoki.ui.theme.IloTokiTheme
import one.larkin.ilotoki.ui.tokensPerSecondLabel

/** How many sample phrases the row under the slab offers. */
private const val SAMPLE_COUNT = 3

/** Where the first plate starts inside the plate area — the column's top padding. */
private val PLATE_TOP = 8.dp

/** Plate padding plus the stamp's own height, so the list clears the stamp. */
private val STAMP_DROP = 48.dp

/** The popover sits slightly inboard of the plate edge, as in the mock. */
private val POPOVER_INSET = 22.dp

/** Gap left between the popover and the plate it flips above. */
private val POPOVER_GAP = 8.dp

/**
 * The main screen: source plate, target plate, the knob on the seam, the slab.
 *
 * The model's state never takes this screen away. Downloading, loading, failing and
 * having no model at all are all shown *inside* the target plate (plus the progress
 * line under the header), because a translator that hides itself behind a progress
 * bar is unusable exactly when someone first opens it.
 */
@Composable
fun TranslatorScreen(
    state: TranslatorState,
    status: ModelStatus,
    models: List<ModelState>,
    viewModel: MainViewModel,
    onOpenModels: () -> Unit,
) {
    var languagesOpen by remember { mutableStateOf(false) }

    // With the keyboard up the target plate gives up its space to the input and
    // keeps only enough to show the last result on one line.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Only one of the two sides has a language to choose, and it is never the toki
    // pona one — that side of the pair is the whole point of the app. So the picker
    // belongs to whichever plate currently holds the other language, and swaps over
    // to the other plate with the direction.
    val pickerOnSource = !state.fromTokiPona
    val togglePicker = { languagesOpen = !languagesOpen }

    // Where the target plate starts, so the popover can hang off the stamp there
    // instead of off the bottom of the screen. Measured rather than derived: the
    // plate's top moves with the keyboard, and with whatever state it is in. The
    // area's own height and the popover's are measured for the same reason — with
    // the keyboard up the target plate is a strip at the foot of the area, and the
    // list dropped below its stamp lands under the keyboard.
    var plateAreaTop by remember { mutableFloatStateOf(0f) }
    var plateAreaHeight by remember { mutableIntStateOf(0) }
    var targetTop by remember { mutableFloatStateOf(0f) }
    var popoverHeight by remember { mutableIntStateOf(0) }

    // A fresh three every launch, and again whenever one is used.
    var sampleRoll by remember { mutableIntStateOf(0) }
    val samples = remember(sampleRoll) { SamplePhrases.pick(SAMPLE_COUNT) }

    Column(Modifier.fillMaxSize().screenBottomInsets()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned {
                    plateAreaTop = it.positionInRoot().y
                    plateAreaHeight = it.size.height
                },
        ) {
            Column(
                Modifier.fillMaxSize().padding(top = 8.dp).padding(horizontal = 14.dp),
            ) {
                SourcePlate(
                    state = state,
                    viewModel = viewModel,
                    onPickLanguage = if (pickerOnSource) togglePicker else null,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                // The seam. The knob is taller than the gap and overflows it on
                // purpose — that overlap is what makes it read as a hinge, which
                // only works if it draws over both plates rather than under them.
                Box(Modifier.fillMaxWidth().height(14.dp).zIndex(1f)) {
                    SwapKnob(
                        onClick = viewModel::swapDirection,
                        modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-12).dp),
                    )
                }
                TargetArea(
                    state = state,
                    status = status,
                    models = models,
                    viewModel = viewModel,
                    onOpenModels = onOpenModels,
                    onPickLanguage = if (pickerOnSource) null else togglePicker,
                    modifier = (
                        if (imeVisible) {
                            Modifier.height(76.dp).fillMaxWidth()
                        } else {
                            Modifier.weight(1f).fillMaxWidth()
                        }
                        ).onGloballyPositioned { targetTop = it.positionInRoot().y },
                    collapsed = imeVisible,
                )
            }

            // Until there is something to translate with, the one thing a new
            // user has to know is where the translator comes from.
            if (models.isNotEmpty() && models.none { it.downloaded }) {
                FirstRunCallout(
                    sizeBytes = models.selectedSpec().sizeBytes,
                    modifier = Modifier.align(Alignment.TopEnd).zIndex(2f),
                )
            }

            if (languagesOpen) {
                // A tap anywhere else puts it away; a popover that can only be
                // closed by the control that opened it is a trap.
                Box(Modifier.fillMaxSize().zIndex(5f).tap { languagesOpen = false })
                LanguagePopover(
                    selected = state.target,
                    onSelect = {
                        languagesOpen = false
                        viewModel.onTargetChange(it)
                    },
                    // Hung off the stamp that opened it: just under the source
                    // plate's own stamp, or under the target plate's, wherever that
                    // plate currently begins — and *above* that plate instead when
                    // the list would not fit below it, which is what happens to the
                    // target plate as soon as the keyboard collapses it into a strip
                    // at the foot of the area.
                    modifier = Modifier
                        .zIndex(6f)
                        .align(Alignment.TopStart)
                        // Until it has been measured once there is no telling which
                        // way it goes; drawing it would show a frame at the wrong end.
                        .alpha(if (popoverHeight > 0) 1f else 0f)
                        .onGloballyPositioned { popoverHeight = it.size.height }
                        .offset {
                            val plateTop = if (pickerOnSource) {
                                PLATE_TOP.roundToPx()
                            } else {
                                (targetTop - plateAreaTop).roundToInt()
                            }
                            val below = plateTop + STAMP_DROP.roundToPx()
                            val fits = below + popoverHeight <= plateAreaHeight
                            val y = if (popoverHeight == 0 || fits) {
                                below
                            } else {
                                (plateTop - POPOVER_GAP.roundToPx() - popoverHeight)
                                    .coerceAtLeast(0)
                            }
                            IntOffset(POPOVER_INSET.roundToPx(), y)
                        },
                )
            }
        }

        Column(Modifier.padding(14.dp)) {
            Slab(state = state, status = status, models = models, viewModel = viewModel)
            // The samples are an offer for an empty input. Once someone is typing
            // they are answered, and the row is only taking a strip of the little
            // room the keyboard leaves.
            if (!imeVisible) {
                Spacer(Modifier.height(11.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    samples.forEach { sample ->
                        Chip(
                            text = sample,
                            onClick = {
                                // Reroll as one is taken, so the row is never the same
                                // three phrases session after session.
                                sampleRoll++
                                viewModel.reuse(sample, true, state.target)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The label above a plate, naming the language on that side.
 *
 * [onPick] is null on the toki pona side — there is nothing to choose there — and
 * that is what decides whether the stamp carries the caret and reacts to a tap.
 */
@Composable
private fun PairStamp(
    isTokiPona: Boolean,
    other: Language,
    onAccentPlate: Boolean,
    onPick: (() -> Unit)?,
) {
    val colors = IloTokiTheme.colors
    val background = if (onAccentPlate) colors.onAccent else colors.ink
    val content = if (onAccentPlate) colors.accent else colors.bg
    Stamp(
        text = (if (isTokiPona) TOKI_PONA else other.displayName).uppercase(),
        modifier = if (onPick != null) Modifier.tap(onClick = onPick) else Modifier,
        background = background,
        contentColor = content,
        glyph = if (isTokiPona) "toki" else null,
        trailing = if (onPick == null) {
            null
        } else {
            { AppText("▾", IloTokiTheme.type.stamp, color = content) }
        },
    )
}

@Composable
private fun SourcePlate(
    state: TranslatorState,
    viewModel: MainViewModel,
    onPickLanguage: (() -> Unit)?,
    modifier: Modifier,
) {
    val colors = IloTokiTheme.colors
    val fromTokiPona = state.fromTokiPona
    val glyphs = fromTokiPona && state.useSitelenPona

    Plate(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PairStamp(
                    isTokiPona = fromTokiPona,
                    other = state.target,
                    onAccentPlate = false,
                    onPick = onPickLanguage,
                )
            }
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().weight(1f),
                textStyle = textStyleFor(glyphs).copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                // Toki Pona is written entirely in lower case and none of its words
                // are in the keyboard's dictionary, so capitalization and autocorrect
                // only corrupt the input. Typing the other language keeps both.
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    capitalization = if (fromTokiPona) {
                        KeyboardCapitalization.None
                    } else {
                        KeyboardCapitalization.Sentences
                    },
                    autoCorrectEnabled = !fromTokiPona,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.translate() }),
                decorationBox = { field ->
                    Box(Modifier.fillMaxWidth()) {
                        if (state.query.isEmpty()) {
                            AppText(
                                text = if (fromTokiPona) {
                                    "o toki…"
                                } else {
                                    "type ${state.target.displayName}…"
                                },
                                style = textStyleFor(glyphs),
                                color = colors.muted,
                            )
                        }
                        field()
                    }
                },
            )
            if (fromTokiPona) {
                Spacer(Modifier.height(10.dp))
                ScriptButton(
                    on = state.useSitelenPona,
                    onAccentPlate = false,
                    onClick = viewModel::toggleSitelenPona,
                )
            }
        }
    }
}

/**
 * Whatever belongs where the answer goes.
 *
 * Every model state that is not «ready» surfaces here rather than as a screen: the
 * plate is where a user is already looking, and it keeps the input above it usable
 * while a download runs.
 */
@Composable
private fun TargetArea(
    state: TranslatorState,
    status: ModelStatus,
    models: List<ModelState>,
    viewModel: MainViewModel,
    onOpenModels: () -> Unit,
    onPickLanguage: (() -> Unit)?,
    modifier: Modifier,
    collapsed: Boolean,
) {
    // An empty list is «the disk has not been looked at yet», not «nothing is
    // downloaded» — the catalog is never empty. Saying «no translator» during that
    // first frame, or while a model that is right there is being loaded, is a lie
    // the user sees on every single launch.
    val scanned = models.isNotEmpty()
    val haveSelectedFile = models.any { it.selected && it.downloaded }
    when {
        status is ModelStatus.Failed -> FailedPlate(
            message = status.message,
            onRetry = viewModel::getTranslator,
            modifier = modifier,
            collapsed = collapsed,
        )

        status is ModelStatus.Downloading -> DownloadingPlate(
            status = status,
            spec = models.selectedSpec(),
            onPause = viewModel::pauseDownload,
            onAnotherModel = onOpenModels,
            modifier = modifier,
            collapsed = collapsed,
        )

        scanned && !haveSelectedFile && status !is ModelStatus.Ready -> NoTranslatorPlate(
            model = models.selectedSpec(),
            modifier = modifier,
            collapsed = collapsed,
        )

        state.result.isEmpty() && !state.isTranslating -> EmptyTargetPlate(
            state = state,
            onPickLanguage = onPickLanguage,
            modifier = modifier,
            collapsed = collapsed,
        )

        else -> ResultPlate(
            state = state,
            viewModel = viewModel,
            onPickLanguage = onPickLanguage,
            modifier = modifier,
            collapsed = collapsed,
        )
    }
}

@Composable
private fun ResultPlate(
    state: TranslatorState,
    viewModel: MainViewModel,
    onPickLanguage: (() -> Unit)?,
    modifier: Modifier,
    collapsed: Boolean,
) {
    val colors = IloTokiTheme.colors
    val targetIsTokiPona = !state.fromTokiPona
    val glyphs = targetIsTokiPona && state.useSitelenPona

    if (collapsed) {
        CollapsedTarget(
            modifier = modifier,
            background = colors.accent,
            contentColor = colors.onAccent,
            onAccentPlate = true,
            isTokiPona = targetIsTokiPona,
            other = state.target,
            onPickLanguage = onPickLanguage,
        ) {
            AppText(
                text = state.result,
                style = collapsedTextStyle(glyphs),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    Plate(modifier = modifier, background = colors.accent, contentColor = colors.onAccent) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PairStamp(
                    isTokiPona = targetIsTokiPona,
                    other = state.target,
                    onAccentPlate = true,
                    onPick = onPickLanguage,
                )
                if (state.isTranslating && state.tokensPerSecond > 0f) {
                    Stamp(
                        text = tokensPerSecondLabel(state.tokensPerSecond),
                        background = colors.onAccent,
                        contentColor = colors.accent,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Tapping the text flips the script too — the pill is the discoverable
            // control, this is the one a reader reaches for.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .then(
                        if (targetIsTokiPona) {
                            Modifier.tap(onClick = viewModel::toggleSitelenPona)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                SelectionContainer {
                    StreamingText(
                        text = state.result,
                        streaming = state.isTranslating,
                        style = textStyleFor(glyphs).copy(color = colors.onAccent),
                        caretColor = colors.onAccent,
                    )
                }
            }
            if (targetIsTokiPona) {
                Spacer(Modifier.height(10.dp))
                ScriptButton(
                    on = state.useSitelenPona,
                    onAccentPlate = true,
                    onClick = viewModel::toggleSitelenPona,
                )
            }
        }
    }
}

/** The result plus the blinking block caret while tokens are still arriving. */
@Composable
private fun StreamingText(
    text: String,
    streaming: Boolean,
    style: TextStyle,
    caretColor: Color,
) {
    if (!streaming) {
        AppText(text, style)
        return
    }

    val blink = rememberInfiniteTransition(label = "caret")
    val alpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // One second, hard steps: the design has no easing anywhere.
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 449
                0f at 450
                0f at 999
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "caretAlpha",
    )

    val density = LocalDensity.current
    val annotated = buildAnnotatedString {
        append(text)
        appendInlineContent(CARET, "▍")
    }
    val caret = mapOf(
        CARET to InlineTextContent(
            Placeholder(
                width = with(density) { 15.dp.toSp() },
                height = with(density) { 24.dp.toSp() },
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
            ),
        ) {
            Box(
                Modifier
                    .padding(start = 3.dp)
                    .size(12.dp, 24.dp)
                    .alpha(alpha)
                    .background(caretColor),
            )
        },
    )
    BasicText(
        text = annotated,
        style = style,
        inlineContent = caret,
    )
}

private const val CARET = "caret"

/**
 * Nothing translated yet: dashed outline, the mark as a watermark, one stamp.
 *
 * It keeps the pair stamp even though the mock's empty state does not — without it
 * there is no way to choose the language until after the first translation, which
 * is exactly when someone is most likely to want a different one.
 */
@Composable
private fun EmptyTargetPlate(
    state: TranslatorState,
    onPickLanguage: (() -> Unit)?,
    modifier: Modifier,
    collapsed: Boolean,
) {
    val colors = IloTokiTheme.colors
    if (collapsed) {
        CollapsedTarget(
            modifier = modifier,
            background = Color.Transparent,
            contentColor = colors.ink,
            border = colors.faint,
            shadow = 0.dp,
            dashed = true,
            onAccentPlate = false,
            isTokiPona = !state.fromTokiPona,
            other = state.target,
            onPickLanguage = onPickLanguage,
        ) {
            AppText(
                text = "translation lands here",
                style = IloTokiTheme.type.meta.copy(fontSize = 13.sp),
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Plate(
        modifier = modifier,
        background = Color.Transparent,
        border = colors.faint,
        shadow = 0.dp,
        dashed = true,
    ) {
        Column(Modifier.fillMaxSize()) {
            PairStamp(
                isTokiPona = !state.fromTokiPona,
                other = state.target,
                onAccentPlate = false,
                onPick = onPickLanguage,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VectorIcon(IloTokiIcons.Mark, null, colors.faint, 54.dp)
                    Spacer(Modifier.height(14.dp))
                    Stamp(
                        text = "TRANSLATION LANDS HERE",
                        background = Color.Transparent,
                        contentColor = colors.muted,
                        border = colors.faint,
                    )
                }
            }
        }
    }
}

/** No model on the device at all. Says what it costs and what it buys. */
@Composable
private fun NoTranslatorPlate(model: ModelSpec, modifier: Modifier, collapsed: Boolean) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type
    if (collapsed) {
        CollapsedStrip(modifier = modifier, background = colors.paper) {
            AppText(
                text = "no translator yet · ${gibLabel(model.sizeBytes)} to download once",
                style = type.meta.copy(fontSize = 13.sp),
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Plate(modifier = modifier, background = colors.paper) {
        Column(Modifier.fillMaxSize()) {
            AppText("NO TRANSLATOR YET", type.section, color = colors.muted)
            Spacer(Modifier.height(12.dp))
            AppText(
                text = "${gibLabel(model.sizeBytes)} once — then it works with " +
                    "the plane on and nothing leaves the phone",
                style = type.meta.copy(fontSize = 13.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(8.dp))
            AppText(
                text = "a dropped download resumes by itself",
                style = type.meta,
                color = colors.muted,
            )
        }
    }
}

/** While the model is coming down. The input above stays live throughout. */
@Composable
private fun DownloadingPlate(
    status: ModelStatus.Downloading,
    spec: ModelSpec,
    onPause: () -> Unit,
    onAnotherModel: () -> Unit,
    modifier: Modifier,
    collapsed: Boolean,
) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type
    val percent = (status.progress.fractionOrZero() * 100).toInt()
    if (collapsed) {
        CollapsedStrip(modifier = modifier, background = colors.paper) {
            AppText(
                text = "$percent% · ${formatGiB(status.progress.downloaded)} / " +
                    gibLabel(spec.sizeBytes),
                style = type.meta.copy(fontSize = 13.sp),
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Plate(modifier = modifier, background = colors.paper) {
        Column(Modifier.fillMaxSize()) {
            AppText("GETTING THE TRANSLATOR", type.section, color = colors.muted)
            Spacer(Modifier.height(10.dp))
            AppText("$percent%", type.bigNumber.copy(fontSize = 38.sp), color = colors.ink)
            Spacer(Modifier.height(6.dp))
            AppText(
                text = "${formatGiB(status.progress.downloaded)} / " + gibLabel(spec.sizeBytes),
                style = type.meta,
                color = colors.muted,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(
                    text = "PAUSE",
                    onClick = onPause,
                    background = Color.Transparent,
                    style = type.stamp,
                    radius = 999.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                )
                Chip(
                    text = "ANOTHER MODEL",
                    onClick = onAnotherModel,
                    background = Color.Transparent,
                    style = type.stamp,
                    radius = 999.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/** A failure is a plate with a way out, not a dead end screen. */
@Composable
private fun FailedPlate(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier,
    collapsed: Boolean,
) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type
    if (collapsed) {
        CollapsedStrip(
            modifier = modifier,
            background = colors.paper,
            border = colors.loje,
        ) {
            AppText(
                text = message,
                style = type.meta.copy(fontSize = 13.sp),
                color = colors.loje,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Plate(modifier = modifier, background = colors.paper, border = colors.loje) {
        Column(Modifier.fillMaxSize()) {
            AppText("THAT DID NOT WORK", type.section, color = colors.loje)
            Spacer(Modifier.height(10.dp))
            AppText(message, type.meta.copy(fontSize = 13.sp), color = colors.ink)
            Spacer(Modifier.weight(1f))
            Chip(
                text = "TRY AGAIN",
                onClick = onRetry,
                background = Color.Transparent,
                contentColor = colors.loje,
                border = colors.loje,
                style = type.stamp,
                radius = 999.dp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

/** The primary action, in whichever of its four shapes the model state calls for. */
@Composable
private fun Slab(
    state: TranslatorState,
    status: ModelStatus,
    models: List<ModelState>,
    viewModel: MainViewModel,
) {
    val colors = IloTokiTheme.colors
    val type = IloTokiTheme.type
    val selected = models.selectedSpec()
    val ready = status is ModelStatus.Ready
    val downloading = status as? ModelStatus.Downloading
    // The file is here and only needs opening: mapping a gigabyte takes about a
    // second, and during it the slab must not offer to fetch what it already has.
    val opening = !ready && downloading == null &&
        (models.isEmpty() || models.any { it.selected && it.downloaded })

    val enabled = when {
        downloading != null || opening -> false
        !ready -> true
        else -> state.query.isNotBlank() && !state.isTranslating
    }

    val label = when {
        downloading != null -> {
            val fraction = downloading.progress.fractionOrZero()
            "${(fraction * 100).toInt()}% · ${formatGiB(downloading.progress.downloaded)} / " +
                gibLabel(selected.sizeBytes)
        }

        opening -> "waking the translator…"
        !ready -> "get the translator · ${gibLabel(selected.sizeBytes)}"
        state.isTranslating -> "translating…"
        else -> "translate"
    }

    // «Opening» wears the ready slab, not the download one: nothing is being asked
    // of the user, so it must not look like an offer.
    val slabIsInk = ready || opening
    val background = if (slabIsInk) colors.ink else colors.accent
    val content = if (slabIsInk) colors.bg else colors.onAccent

    Plate(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .tap(enabled = enabled) {
                if (ready) viewModel.translate() else viewModel.getTranslator()
            },
        background = if (downloading != null) colors.paper else background,
        contentColor = if (downloading != null) colors.ink else content,
        radius = 20.dp,
        // Disabled keeps the shape and loses the shadow — it stays a slab, it just
        // stops looking pressable.
        shadow = if (enabled) 3.dp else 0.dp,
        contentPadding = PaddingValues(0.dp),
        clipContent = true,
    ) {
        if (downloading != null) {
            Box(
                Modifier
                    .fillMaxWidth(downloading.progress.fractionOrZero())
                    .fillMaxHeight()
                    .background(colors.accent),
            )
        }
        Row(
            modifier = Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.4f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                downloading != null -> Unit
                slabIsInk -> {
                    AppText("pana", IloTokiTheme.type.sitelen.copy(fontSize = 36.sp, lineHeight = 36.sp))
                    Spacer(Modifier.width(10.dp))
                }

                else -> {
                    VectorIcon(IloTokiIcons.Download, null, colors.onAccent, 20.dp)
                    Spacer(Modifier.width(10.dp))
                }
            }
            AppText(label, type.slab, maxLines = 1)
        }
    }
}

/**
 * The pointer at the gear, shown until a model has been downloaded once.
 *
 * The triangle is offset to land under the gear rather than under the callout's own
 * corner: the gear sits 80 dp in from the screen edge and the plate ends at 14 dp,
 * so the point is 59 dp left of the plate's right edge.
 */
@Composable
private fun FirstRunCallout(sizeBytes: Long, modifier: Modifier) {
    val colors = IloTokiTheme.colors
    Column(
        modifier = modifier.padding(end = 14.dp).widthIn(max = 224.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Canvas(Modifier.padding(end = 59.dp).size(14.dp, 9.dp)) {
            drawPath(
                path = Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                },
                color = colors.line,
            )
        }
        Plate(
            background = colors.accent,
            contentColor = colors.onAccent,
            radius = 14.dp,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
        ) {
            AppText(
                text = "settings live here — and your translator, " +
                    "${gibLabel(sizeBytes)} to download once",
                style = IloTokiTheme.type.meta.copy(fontSize = 12.sp),
            )
        }
    }
}

/**
 * The target plate while the keyboard is up: one 76 dp strip.
 *
 * The input needs the room, so everything here is a single row — the pair stamp,
 * which is also the language picker and so must stay reachable, and one line of
 * whatever the plate would otherwise be showing. Stacking the header above the text
 * does not fit in 76 dp at the full reading size, and squeezing it in is what makes
 * the plate look broken rather than compact.
 *
 * The strip exists only while the keyboard is up, so tapping it puts the keyboard
 * away and gives the plate back its full size — the result is what someone reaching
 * for it wants to read, and it is the one thing on screen the keyboard is covering.
 * The pair stamp inside keeps its own tap: a child is hit first.
 */
@Composable
private fun CollapsedStrip(
    modifier: Modifier,
    background: Color,
    contentColor: Color = IloTokiTheme.colors.ink,
    border: Color = IloTokiTheme.colors.line,
    shadow: Dp = 3.dp,
    dashed: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Plate(
        modifier = modifier.tap {
            focusManager.clearFocus()
            keyboard?.hide()
        },
        background = background,
        contentColor = contentColor,
        border = border,
        shadow = shadow,
        dashed = dashed,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Box(Modifier.weight(1f)) { text() }
        }
    }
}

/** The strip for the two states that have a language on this side to name. */
@Composable
private fun CollapsedTarget(
    modifier: Modifier,
    background: Color,
    contentColor: Color,
    onAccentPlate: Boolean,
    isTokiPona: Boolean,
    other: Language,
    onPickLanguage: (() -> Unit)?,
    border: Color = IloTokiTheme.colors.line,
    shadow: Dp = 3.dp,
    dashed: Boolean = false,
    text: @Composable () -> Unit,
) = CollapsedStrip(
    modifier = modifier,
    background = background,
    contentColor = contentColor,
    border = border,
    shadow = shadow,
    dashed = dashed,
    leading = {
        PairStamp(
            isTokiPona = isTokiPona,
            other = other,
            onAccentPlate = onAccentPlate,
            onPick = onPickLanguage,
        )
    },
    text = text,
)

/** The reading size does not fit the strip; this is the same text, one notch down. */
@Composable
private fun collapsedTextStyle(glyphs: Boolean): TextStyle =
    if (glyphs) {
        IloTokiTheme.type.sitelen.copy(fontSize = 26.sp, lineHeight = 30.sp)
    } else {
        IloTokiTheme.type.body.copy(fontSize = 17.sp, lineHeight = 22.sp)
    }

/** 50 dp accent square on the seam. Flips the direction and the two texts with it. */
@Composable
private fun SwapKnob(onClick: () -> Unit, modifier: Modifier) {
    val colors = IloTokiTheme.colors
    Plate(
        // requiredSize, not size: the knob lives in the 14 dp seam between the
        // plates and has to ignore that box's constraints to keep its square.
        modifier = modifier.requiredSize(50.dp).tap(onClick = onClick),
        background = colors.accent,
        contentColor = colors.onAccent,
        radius = 15.dp,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            VectorIcon(IloTokiIcons.SwapVertical, "swap direction", colors.onAccent, 26.dp)
        }
    }
}

/**
 * The pill that flips latin ↔ sitelen pona, written in the script it turns on.
 *
 * It lives at the foot of whichever plate holds toki pona, on the left. The
 * top-right corner is where the swap knob overhangs the target plate, and the pill
 * was disappearing under it.
 */
@Composable
private fun ScriptButton(on: Boolean, onAccentPlate: Boolean, onClick: () -> Unit) {
    val colors = IloTokiTheme.colors
    val background = when {
        !on -> Color.Transparent
        onAccentPlate -> colors.onAccent
        else -> colors.accent
    }
    val content = when {
        !on -> if (onAccentPlate) colors.onAccent else colors.ink
        onAccentPlate -> colors.accent
        else -> colors.onAccent
    }
    Plate(
        modifier = Modifier.tap(onClick = onClick).alpha(if (on) 1f else 0.55f),
        background = background,
        contentColor = content,
        border = if (on) {
            if (onAccentPlate) colors.onAccent else colors.line
        } else {
            colors.faint
        },
        radius = 12.dp,
        shadow = 0.dp,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
    ) {
        AppText(
            text = "sitelen pona",
            // The pill is a label sitting among 700-weight stamps, so its glyphs are
            // emboldened to match. sitelen pona pona ships one weight, so this is a
            // synthetic bold — which is exactly what is wanted: the same strokes,
            // thicker, rather than a different face.
            style = IloTokiTheme.type.sitelen.copy(
                fontSize = 23.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

/** The language list, hung off the target plate's pair stamp. */
@Composable
private fun LanguagePopover(
    selected: Language,
    onSelect: (Language) -> Unit,
    modifier: Modifier,
) {
    val colors = IloTokiTheme.colors
    Plate(
        modifier = modifier.width(200.dp),
        radius = 18.dp,
        shadow = 4.dp,
        contentPadding = PaddingValues(7.dp),
    ) {
        Column {
            Language.entries.forEach { language ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tap { onSelect(language) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = language.displayName,
                        style = IloTokiTheme.type.row.copy(fontSize = 15.sp),
                        color = colors.ink,
                    )
                    if (language == selected) {
                        AppText(
                            text = "pona",
                            style = IloTokiTheme.type.sitelen.copy(
                                fontSize = 26.sp,
                                lineHeight = 26.sp,
                            ),
                            color = colors.ink,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The entry the sizes on this screen refer to.
 *
 * The list is empty for the first frame or two, before the repository has finished
 * looking at the disk; falling back to the catalog default keeps the slab from
 * briefly offering to download 0.00 GiB.
 */
private fun List<ModelState>.selectedSpec(): ModelSpec =
    firstOrNull { it.selected }?.spec ?: ModelCatalog.default

@Composable
private fun textStyleFor(glyphs: Boolean): TextStyle =
    if (glyphs) IloTokiTheme.type.sitelen else IloTokiTheme.type.body
