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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import one.larkin.ilotoki.model.DownloadProgress
import one.larkin.ilotoki.model.ModelCatalog
import one.larkin.ilotoki.model.ModelStatus
import one.larkin.ilotoki.ui.AppText
import one.larkin.ilotoki.ui.BackSquare
import one.larkin.ilotoki.ui.IconSquare
import one.larkin.ilotoki.ui.IloTokiIcons
import one.larkin.ilotoki.ui.MarkTile
import one.larkin.ilotoki.ui.ProgressLine
import one.larkin.ilotoki.ui.screens.AboutOverlay
import one.larkin.ilotoki.ui.screens.HistoryScreen
import one.larkin.ilotoki.ui.screens.SettingsScreen
import one.larkin.ilotoki.ui.screens.TranslatorScreen
import one.larkin.ilotoki.ui.screens.TranslatorsScreen
import one.larkin.ilotoki.ui.theme.IloTokiTheme

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

        var screen by remember { mutableStateOf(Screen.Translator) }
        var aboutOpen by remember { mutableStateOf(false) }

        val goBack = {
            when {
                aboutOpen -> aboutOpen = false
                screen == Screen.Models -> screen = Screen.Settings
                else -> screen = Screen.Translator
            }
        }
        PlatformBackHandler(enabled = aboutOpen || screen != Screen.Translator, onBack = goBack)

        val colors = IloTokiTheme.colors
        Box(Modifier.fillMaxSize().background(colors.bg)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Header(
                    screen = screen,
                    // The dot is the one thing allowed to ask for attention, so it
                    // means both kinds of «you need to go to settings»: nothing to
                    // translate with, or something better to translate with.
                    showDot = hasUpdate ||
                        (models.isNotEmpty() && models.none { it.downloaded }),
                    onMark = { aboutOpen = true },
                    onBack = goBack,
                    onSettings = { screen = Screen.Settings },
                    onHistory = { screen = Screen.History },
                )

                val status = modelStatus
                if (status is ModelStatus.Downloading) {
                    ProgressLine(
                        fraction = status.progress.fractionOrZero(),
                        modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 4.dp),
                    )
                }

                when (screen) {
                    Screen.Translator -> TranslatorScreen(
                        state = state,
                        status = status,
                        models = models,
                        viewModel = viewModel,
                        onOpenModels = { screen = Screen.Models },
                    )

                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        models = models,
                        hasUpdate = hasUpdate,
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

            if (aboutOpen) {
                AboutOverlay(
                    model = models.firstOrNull { it.selected }?.spec ?: ModelCatalog.default,
                    onDismiss = { aboutOpen = false },
                )
            }
        }
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
            if (atHome) MarkTile(onClick = onMark) else BackSquare(onClick = onBack)
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

/**
 * The system back gesture. Android has a hardware/gesture back that would
 * otherwise leave the app from a subscreen; iOS has the edge swipe.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** A server that does not report a length leaves the line empty rather than lying. */
internal fun DownloadProgress.fractionOrZero(): Float =
    if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
