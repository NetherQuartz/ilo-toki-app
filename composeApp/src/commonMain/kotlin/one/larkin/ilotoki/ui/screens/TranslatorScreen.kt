package one.larkin.ilotoki.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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
import one.larkin.ilotoki.ui.Motion
import one.larkin.ilotoki.ui.Plate
import one.larkin.ilotoki.ui.PressSqueeze
import one.larkin.ilotoki.ui.animatedProgress
import one.larkin.ilotoki.ui.cardIn
import one.larkin.ilotoki.ui.fadeIn
import one.larkin.ilotoki.ui.nudge
import one.larkin.ilotoki.ui.plateIn
import one.larkin.ilotoki.ui.popIn
import one.larkin.ilotoki.ui.screenBottomInsets
import one.larkin.ilotoki.ui.screenIn
import one.larkin.ilotoki.ui.stampIn
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

/** The seam between the plates, which the swap knob overhangs. */
private val SEAM = 14.dp

/** What the target plate keeps for itself once the keyboard has the rest. */
private val COLLAPSED_HEIGHT = 76.dp

/** Below this it wears the strip; above it, the full stack. */
private val COLLAPSED_CONTENT_LIMIT = 120.dp

/**
 * What the input keeps for itself once the keyboard is up.
 *
 * It is the input that is being used at that moment, so it gets the room and the
 * target plate takes what is left, down to its strip. The value has to sit between
 * two bounds: at or below half the area, or the plate would jump the moment the
 * keyboard is announced instead of travelling; and at or above what a keyboard
 * leaves less 76 dp, or the plate never reaches the strip at all. On a phone that
 * is a wide window and 300 dp sits in it; where a keyboard is small enough that no
 * value satisfies both, the plate simply stops short of the strip, which is right —
 * the strip exists because the keyboard takes the screen, and a small one does not.
 */
private val SOURCE_MIN = 300.dp

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
    //
    // The insets object is taken here; the *value* is read in the measure blocks
    // below, never at composition. That is the difference between this screen being
    // rebuilt sixty times a second while a keyboard travels and it merely being
    // measured again — and the plate area used to be a `BoxWithConstraints`, whose
    // every re-measure is a subcomposition of everything inside it. Measured on a
    // Pixel 6: 22 ms a frame that way, 9 ms this way.
    val imeInsets = WindowInsets.ime
    val navigationInsets = WindowInsets.navigationBars

    // How far up the keyboard is, as a fraction — the same movement the plates ride
    // through the space they are left with, in a form the things *below* them can
    // ride too. Its own height is the only thing missing to compute it, and the
    // keyboard is the only one who knows that, so it is remembered from the deepest
    // squeeze seen. Before there is one — the first time a keyboard opens in a
    // session — this falls back to «up or not», which is what everything used to do.
    //
    // What counts is the squeeze, not the inset: the screen's bottom padding is
    // `navigationBars ∪ ime`, so the last stretch of the keyboard's travel — the
    // part still inside the gesture bar's own height — moves nothing. Measured from
    // the raw inset instead, the chips were still unfolding through that stretch and
    // taking their space back off the plates *after* the plates had finished
    // growing, which is the top block growing and then shrinking again.
    //
    // Kept in a plain array rather than in state: it is written and read inside the
    // measure blocks, and as state every frame of the keyboard's travel would write
    // it and invite a recomposition of this whole screen.
    val imeFull = remember { IntArray(1) }
    val squeeze = { density: Density ->
        (imeInsets.getBottom(density) - navigationInsets.getBottom(density)).coerceAtLeast(0)
    }
    val imeFraction = { density: Density ->
        val bottom = squeeze(density)
        if (bottom > imeFull[0]) imeFull[0] = bottom
        when {
            imeFull[0] > 0 -> (bottom.toFloat() / imeFull[0]).coerceIn(0f, 1f)
            bottom > 0 -> 1f
            else -> 0f
        }
    }

    // Which of its two forms the target plate is wearing. It is decided where the
    // height is — in measure — and put back here, so it changes once each way rather
    // than being asked every frame.
    var collapsed by remember { mutableStateOf(false) }

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

    Column(Modifier.fillMaxSize().screenIn().screenBottomInsets()) {
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
                    pickerOpen = languagesOpen,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                // The seam. The knob is taller than the gap and overflows it on
                // purpose — that overlap is what makes it read as a hinge, which
                // only works if it draws over both plates rather than under them.
                Box(Modifier.fillMaxWidth().height(SEAM).zIndex(1f)) {
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
                    pickerOpen = languagesOpen,
                    // The target plate gives up its half to the keyboard rather than
                    // being swapped for a strip: both states are a height, so the
                    // plate travels between them instead of jumping. The source plate
                    // keeps its weight and takes back whatever this one leaves.
                    //
                    // The height is a function of the room there is, worked out here
                    // in measure — that area is already being squeezed by the ime
                    // inset frame by frame, so reading it is exact synchronisation
                    // for free. A tween cannot be: «is the keyboard up» only goes
                    // false once the keyboard has *finished* leaving, and the plate
                    // would grow back after it rather than with it.
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val open = constraints.maxHeight
                            val height = if (squeeze(this) > 0) {
                                (open - SOURCE_MIN.roundToPx()).coerceIn(
                                    COLLAPSED_HEIGHT.roundToPx(),
                                    open / 2,
                                )
                            } else {
                                open / 2
                            }
                            // The contents change over at a size where they still
                            // fit, rather than at the moment the keyboard is
                            // announced: a stacked column in a 76 dp box is clipped
                            // mid-line, which is the whole reason the strip exists.
                            val wanted = height < COLLAPSED_CONTENT_LIMIT.roundToPx()
                            if (wanted != collapsed) collapsed = wanted
                            val placeable = measurable.measure(
                                constraints.copy(minHeight = height, maxHeight = height),
                            )
                            layout(placeable.width, height) { placeable.place(0, 0) }
                        }
                        .onGloballyPositioned { targetTop = it.positionInRoot().y },
                    collapsed = collapsed,
                )
            }

            // Until there is something to translate with, the one thing a new
            // user has to know is where the translator comes from.
            if (models.isNotEmpty() && models.none { it.downloaded }) {
                FirstRunCallout(
                    sizeBytes = models.selectedSpec().sizeBytes,
                    modifier = Modifier.align(Alignment.TopEnd).zIndex(2f).nudge(),
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
                        }
                        // Last in the chain, so the entrance transforms the popover
                        // itself and not a node whose placement is still deferred:
                        // above the `offset`, the layer drew nowhere on iOS and the
                        // list simply turned up when the tween ended. It also waits
                        // for the measurement above rather than being hidden through
                        // it — the entrance is 140 ms, and an `alpha(0f)` guard over
                        // the top would eat most of it.
                        .popIn(enabled = popoverHeight > 0),
                )
            }
        }

        Column(Modifier.padding(14.dp)) {
            Slab(state = state, status = status, models = models, viewModel = viewModel)
            // The samples are an offer for an empty input. Once someone is typing
            // they are answered, and the row is only taking a strip of the little
            // room the keyboard leaves.
            //
            // It folds on the keyboard's own fraction rather than on a tween of its
            // own, because a tween can only start when «is the keyboard up» flips —
            // which on the way down is when the keyboard has already *finished*
            // leaving. That is one movement of the plates followed by a second one
            // down here, and the two should be one.
            // Both the fold and the fade read that fraction where they are applied —
            // in measure and in draw — so the keyboard's whole travel costs this
            // screen no recompositions at all.
            Column(
                Modifier
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val height = (placeable.height * (1f - imeFraction(this)))
                            .roundToInt()
                            .coerceAtLeast(0)
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { alpha = 1f - imeFraction(this) },
            ) {
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
                            modifier = Modifier.cardIn(220),
                            onClick = {
                                // Reroll as one is taken, so the row is never the
                                // same three phrases session after session.
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
 * The caret turns over while the list is open, so the stamp says which way the
 * next tap goes without a second control.
 */
@Composable
private fun PairStamp(
    isTokiPona: Boolean,
    other: Language,
    onAccentPlate: Boolean,
    onPick: (() -> Unit)?,
    pickerOpen: Boolean = false,
) {
    val colors = IloTokiTheme.colors
    val background = if (onAccentPlate) colors.onAccent else colors.ink
    val content = if (onAccentPlate) colors.accent else colors.bg
    val caret = animateFloatAsState(
        targetValue = if (pickerOpen) 180f else 0f,
        animationSpec = tween(160, easing = Motion.Out),
        label = "caret",
    )
    Stamp(
        text = (if (isTokiPona) TOKI_PONA else other.displayName).uppercase(),
        modifier = Modifier
            .stampIn()
            .then(
                if (onPick != null) {
                    Modifier.tap(pressScale = PressSqueeze, onClick = onPick)
                } else {
                    Modifier
                },
            ),
        background = background,
        contentColor = content,
        glyph = if (isTokiPona) "toki" else null,
        trailing = if (onPick == null) {
            null
        } else {
            {
                AppText(
                    text = "▾",
                    style = IloTokiTheme.type.stamp,
                    modifier = Modifier.graphicsLayer { rotationZ = caret.value },
                    color = content,
                )
            }
        },
    )
}

@Composable
private fun SourcePlate(
    state: TranslatorState,
    viewModel: MainViewModel,
    onPickLanguage: (() -> Unit)?,
    pickerOpen: Boolean,
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
                    pickerOpen = pickerOpen,
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
                                modifier = Modifier.fadeIn(200),
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
    pickerOpen: Boolean,
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
            pickerOpen = pickerOpen,
            modifier = modifier,
            collapsed = collapsed,
        )

        else -> ResultPlate(
            state = state,
            viewModel = viewModel,
            onPickLanguage = onPickLanguage,
            pickerOpen = pickerOpen,
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
    pickerOpen: Boolean,
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
            pickerOpen = pickerOpen,
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

    Plate(
        modifier = modifier.plateIn(),
        background = colors.accent,
        contentColor = colors.onAccent,
    ) {
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
                    pickerOpen = pickerOpen,
                )
                if (state.isTranslating && state.tokensPerSecond > 0f) {
                    Stamp(
                        text = tokensPerSecondLabel(state.tokensPerSecond),
                        modifier = Modifier.popIn(160, TransformOrigin.Center),
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

/**
 * The result plus the blinking block caret while tokens are still arriving.
 *
 * A word comes up out of nothing as it is decoded rather than appearing at full
 * strength — the design's `tokenIn`. It is done as a span alpha on one string
 * rather than as a composable per word: the result has to wrap, be selectable and
 * be laid out as sitelen pona, and a row of separate texts is none of those.
 */
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

    // Where the word being decoded starts. Tokens land inside a word too, so the
    // fade is keyed to the word rather than restarted on every token.
    val lastWord = remember(text) { text.trimEnd().lastIndexOf(' ') + 1 }
    val arriving = remember { Animatable(1f) }
    LaunchedEffect(lastWord) {
        arriving.snapTo(0f)
        arriving.animateTo(1f, tween(Motion.TOKEN_MS, easing = Motion.EaseOut))
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
        append(text.substring(0, lastWord))
        withStyle(SpanStyle(color = style.color.copy(alpha = arriving.value))) {
            append(text.substring(lastWord))
        }
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
    pickerOpen: Boolean,
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
            pickerOpen = pickerOpen,
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
        modifier = modifier.plateIn(),
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
                pickerOpen = pickerOpen,
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
    Plate(modifier = modifier.plateIn(), background = colors.paper) {
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
    Plate(modifier = modifier.plateIn(), background = colors.paper) {
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
    Plate(modifier = modifier.plateIn(), background = colors.paper, border = colors.loje) {
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

    // The slab is one control that changes what it is — offer, progress, action —
    // so its two colours cross over rather than cut, and the label changes under a
    // surface that is already on its way to the new state.
    val slabBackground by animateColorAsState(
        targetValue = if (downloading != null) colors.paper else background,
        animationSpec = tween(Motion.SLOW_COLOUR_MS, easing = Motion.EaseOut),
        label = "slabBackground",
    )
    val slabContent by animateColorAsState(
        targetValue = if (downloading != null) colors.ink else content,
        animationSpec = tween(Motion.SLOW_COLOUR_MS, easing = Motion.EaseOut),
        label = "slabContent",
    )

    Plate(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        background = slabBackground,
        contentColor = slabContent,
        radius = 20.dp,
        // Disabled keeps the shape and loses the shadow — it stays a slab, it just
        // stops looking pressable.
        shadow = if (enabled) 3.dp else 0.dp,
        contentPadding = PaddingValues(0.dp),
        clipContent = true,
        onClick = if (enabled) {
            { if (ready) viewModel.translate() else viewModel.getTranslator() }
        } else {
            null
        },
    ) {
        if (downloading != null) {
            Box(
                Modifier
                    .fillMaxWidth(animatedProgress(downloading.progress.fractionOrZero()))
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
    pickerOpen: Boolean,
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
            pickerOpen = pickerOpen,
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

/**
 * 50 dp accent square on the seam. Flips the direction and the two texts with it.
 *
 * The arrows turn half a circle per tap and keep turning the same way — they carry
 * the swap rather than illustrate it, which is why the count only ever goes up
 * instead of alternating between two angles. The curve overshoots and comes back,
 * the one place in the app that does.
 */
@Composable
private fun SwapKnob(onClick: () -> Unit, modifier: Modifier) {
    val colors = IloTokiTheme.colors
    var halfTurns by remember { mutableIntStateOf(0) }
    val angle = animateFloatAsState(
        targetValue = halfTurns * 180f,
        animationSpec = tween(340, easing = Motion.Overshoot),
        label = "swap",
    )
    Plate(
        // requiredSize, not size: the knob lives in the 14 dp seam between the
        // plates and has to ignore that box's constraints to keep its square.
        modifier = modifier.requiredSize(50.dp),
        background = colors.accent,
        contentColor = colors.onAccent,
        radius = 15.dp,
        contentPadding = PaddingValues(0.dp),
        onClick = {
            halfTurns++
            onClick()
        },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            VectorIcon(
                icon = IloTokiIcons.SwapVertical,
                contentDescription = "swap direction",
                tint = colors.onAccent,
                size = 26.dp,
                modifier = Modifier.graphicsLayer { rotationZ = angle.value },
            )
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
    val borderColor = if (on) {
        if (onAccentPlate) colors.onAccent else colors.line
    } else {
        colors.faint
    }
    // Fill, ink, outline and the dimming all cross together: the pill is written in
    // the script it turns on, so what changes is the same word lighting up.
    val pillSpec = tween<Color>(170, easing = Motion.EaseOut)
    val pillBackground by animateColorAsState(background, pillSpec, label = "pillBackground")
    val pillContent by animateColorAsState(content, pillSpec, label = "pillContent")
    val pillBorder by animateColorAsState(borderColor, pillSpec, label = "pillBorder")
    val pillAlpha by animateFloatAsState(
        targetValue = if (on) 1f else 0.55f,
        animationSpec = tween(170, easing = Motion.EaseOut),
        label = "pillAlpha",
    )
    Plate(
        modifier = Modifier
            .tap(pressScale = PressSqueeze, onClick = onClick)
            .alpha(pillAlpha),
        background = pillBackground,
        contentColor = pillContent,
        border = pillBorder,
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
