package one.larkin.ilotoki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import one.larkin.ilotoki.model.DownloadProgress
import one.larkin.ilotoki.model.ModelCatalog
import one.larkin.ilotoki.model.ModelStatus
import one.larkin.ilotoki.ui.AppText
import one.larkin.ilotoki.ui.BackSquare
import one.larkin.ilotoki.ui.IconSquare
import one.larkin.ilotoki.ui.IloTokiIcons
import one.larkin.ilotoki.ui.MarkTile
import one.larkin.ilotoki.ui.LocalEntranceSuppressed
import one.larkin.ilotoki.ui.peeledBack
import one.larkin.ilotoki.ui.revealedBack
import one.larkin.ilotoki.ui.screenIn
import one.larkin.ilotoki.ui.screens.AboutOverlay
import one.larkin.ilotoki.ui.screens.HistoryScreen
import one.larkin.ilotoki.ui.screens.SettingsScreen
import one.larkin.ilotoki.ui.screens.TranslatorScreen
import one.larkin.ilotoki.ui.screens.TranslatorsScreen
import one.larkin.ilotoki.ui.theme.IloTokiTheme

/** Long enough for a screen's entrance to be over before the next one is built. */
private const val PRELOAD_DELAY_MS = 300L

/**
 * The four places in the app. Deliberately not a navigation library: there is one
 * level of depth (translators sits under settings) and no deep links, so a single
 * piece of state is the whole router.
 */
enum class Screen(val title: String) {
    Translator("ilo toki"),
    Settings("settings"),
    Models("translators"),
    History("history"),
}

@Composable
fun App(viewModel: MainViewModel = viewModel { MainViewModel() }) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    IloTokiTheme(setting = settings.theme, useSystemColours = settings.useSystemColours) {
        LaunchedEffect(Unit) { viewModel.onStart() }

        val state by viewModel.state.collectAsStateWithLifecycle()
        val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
        val models by viewModel.models.collectAsStateWithLifecycle()
        val history by viewModel.history.collectAsStateWithLifecycle()
        val hasUpdate by viewModel.hasUpdate.collectAsStateWithLifecycle()
        val loadedModel by viewModel.loadedModel.collectAsStateWithLifecycle()
        val standingIn by viewModel.standingIn.collectAsStateWithLifecycle()

        var screen by remember { mutableStateOf(Screen.Translator) }
        var aboutOpen by remember { mutableStateOf(false) }
        // A screen the back gesture has already been showing must not make its
        // entrance a second time when it becomes the screen for real.
        var arrivedByGesture by remember { mutableStateOf(false) }

        val goBack = {
            when {
                aboutOpen -> aboutOpen = false
                screen == Screen.Models -> screen = Screen.Settings
                else -> screen = Screen.Translator
            }
        }

        // What a back gesture would uncover. Null for the about card: the screen it
        // sits on is already drawn under it, so there is nothing to add behind.
        val behind = when {
            aboutOpen -> null
            screen == Screen.Models -> Screen.Settings
            screen != Screen.Translator -> Screen.Translator
            else -> null
        }

        val back = rememberBackGesture(
            enabled = aboutOpen || screen != Screen.Translator,
            // The about card is what back is dismissing, not a screen it is
            // leaving: it has nothing behind it to preview, so it just goes.
            previewed = !aboutOpen,
        ) {
            arrivedByGesture = true
            goBack()
        }
        LaunchedEffect(screen, aboutOpen) { arrivedByGesture = false }

        // The same screen, once the one on top has stopped arriving. Waiting is the
        // point: composing it is the expensive part, and it has to happen where
        // nothing is moving — during the entrance it would be just as visible as
        // during the gesture it is there to pay for.
        var readyBehind by remember { mutableStateOf<Screen?>(null) }
        LaunchedEffect(behind) {
            readyBehind = null
            if (behind != null) {
                delay(PRELOAD_DELAY_MS)
                readyBehind = behind
            }
        }

        val colors = IloTokiTheme.colors
        // One screen and everything framing it. Taken as a lambda rather than a
        // composable of its own so that drawing the back gesture's destination is a
        // second call and not a second copy of eleven arguments.
        val frame: @Composable (Screen) -> Unit = { shown ->
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Header(
                    screen = shown,
                    // The dot is the one thing allowed to ask for attention, so it
                    // means every kind of «you need to go to settings»: nothing to
                    // translate with, something better to translate with, or a
                    // translator standing in because the chosen one is still coming.
                    showDot = hasUpdate || standingIn ||
                        (models.isNotEmpty() && models.none { it.downloaded }),
                    onMark = { aboutOpen = true },
                    onBack = goBack,
                    onSettings = { screen = Screen.Settings },
                    onHistory = { screen = Screen.History },
                )

                val status = modelStatus

                when (shown) {
                    Screen.Translator -> TranslatorScreen(
                        state = state,
                        status = status,
                        models = models,
                        hasTranslator = loadedModel != null,
                        viewModel = viewModel,
                        onOpenModels = { screen = Screen.Models },
                    )

                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        models = models,
                        hasUpdate = hasUpdate,
                        standingIn = standingIn,
                        historyCount = history.size,
                        viewModel = viewModel,
                        onOpenModels = { screen = Screen.Models },
                        onOpenHistory = { screen = Screen.History },
                        onOpenAbout = { aboutOpen = true },
                    )

                    Screen.Models -> TranslatorsScreen(
                        models = models,
                        status = status,
                        viewModel = viewModel,
                        onStartedDownload = { screen = Screen.Translator },
                    )

                    Screen.History -> HistoryScreen(
                        entries = history,
                        viewModel = viewModel,
                        onReuse = { screen = Screen.Translator },
                    )
                }
            }
        }

        CompositionLocalProvider(LocalEntranceSuppressed provides arrivedByGesture) {
            Box(Modifier.fillMaxSize().background(colors.bg)) {
                // The destination, under the screen being peeled away and a little
                // under its own size until that screen has gone.
                //
                // It is composed *before* the gesture rather than during it. A whole
                // screen takes longer to compose than a frame lasts — measured on a
                // Pixel 6, building it inside the gesture janked a third of the
                // frames and put the 90th percentile at 85 ms, against 13 ms with
                // nothing to build — and a quick flick is over in six frames, so
                // there is nowhere in it to hide that. Built quietly a moment after
                // the screen settles instead, it is only drawn when a gesture is
                // actually in flight.
                val under = if (back.inFlight) behind else readyBehind
                if (under != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .revealedBack { back.exiting }
                            .drawWithContent { if (back.inFlight) drawContent() },
                    ) {
                        frame(under)
                    }
                }

                PeeledScreen(back, peeling = !aboutOpen) { frame(screen) }

                if (aboutOpen) {
                    AboutOverlay(
                        // What is loaded when something is, and what is on its way
                        // when nothing is — the card changes tense rather than
                        // claiming to translate with a file that is still arriving.
                        model = loadedModel
                            ?: models.firstOrNull { it.selected }?.spec
                            ?: ModelCatalog.default,
                        loaded = loadedModel != null,
                        // Dragging back takes the card away rather than the screen:
                        // it is what the gesture is dismissing.
                        onDismiss = { aboutOpen = false },
                    )
                }
            }
        }
    }
}

/**
 * The screen, wrapped so that a back drag peels it.
 *
 * Nothing here reads the gesture's progress: it is handed to the peel as a lambda
 * and read at draw time. A screen is an expensive thing to recompose and there are
 * two of them on stage during a gesture.
 */
@Composable
private fun PeeledScreen(
    back: BackGesture,
    peeling: Boolean,
    content: @Composable () -> Unit,
) {
    // Gated on the flight and not only on the values: they are put back to zero
    // after the swap, and a frame that caught them half-reset would peel the screen
    // that has just arrived. `inFlight` changes twice a gesture, so reading it here
    // costs nothing.
    val live = peeling && back.inFlight
    Box(
        // Its own background, inside the peel so it is scaled and rounded with it:
        // the screen has to be opaque or the one being uncovered shows *through*
        // it rather than out from behind it, and both are legible at once.
        Modifier
            .fillMaxSize()
            .peeledBack(
                progress = { if (live) back.progress else 0f },
                exiting = { if (live) back.exiting else 0f },
            )
            .background(IloTokiTheme.colors.bg),
    ) {
        content()
    }
}

/** Header: mark or back on the left, gear and clock on the right at home only. */
@Composable
private fun Header(
    screen: Screen,
    showDot: Boolean,
    onMark: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
) {
    val atHome = screen == Screen.Translator
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (atHome) MarkTile(onClick = onMark, heavy = true) else BackSquare(onClick = onBack)
            AppText(screen.title, IloTokiTheme.type.title, color = IloTokiTheme.colors.ink)
        }
        if (atHome) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                IconSquare(IloTokiIcons.Gear, "settings", onSettings, dot = showDot)
                IconSquare(IloTokiIcons.Clock, "history", onHistory, iconSize = 22.dp)
            }
        }
    }
}

/** A server that does not report a length leaves the line empty rather than lying. */
internal fun DownloadProgress.fractionOrZero(): Float =
    if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
