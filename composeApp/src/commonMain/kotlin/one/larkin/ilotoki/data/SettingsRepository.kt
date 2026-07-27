package one.larkin.ilotoki.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.larkin.ilotoki.ioDispatcher
import one.larkin.ilotoki.ui.theme.ThemeSetting

/**
 * What the settings screen owns.
 *
 * The script — latin or sitelen pona — is deliberately absent: it is a property of
 * the text being read, not a preference, so it lives on the plate that shows the
 * text and nowhere else.
 */
data class AppSettings(
    val theme: ThemeSetting = ThemeSetting.System,
    /** Android only, and only from API 31; ignored where there is no system palette. */
    val useSystemColours: Boolean = false,
    val keepHistory: Boolean = true,
)

/**
 * Process-wide, like [one.larkin.ilotoki.model.ModelRepository]: the theme has to
 * be readable before the first composition and must survive the ViewModel being
 * recreated on a configuration change.
 */
object SettingsRepository {
    private const val FILE = "settings"

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        scope.launch { _settings.value = read() }
    }

    fun setTheme(theme: ThemeSetting) = update { it.copy(theme = theme) }

    fun setUseSystemColours(enabled: Boolean) = update { it.copy(useSystemColours = enabled) }

    fun setKeepHistory(enabled: Boolean) = update { it.copy(keepHistory = enabled) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        scope.launch { write(next) }
    }

    private fun read(): AppSettings {
        val text = TextFile.read(FILE) ?: return AppSettings()
        val values = text.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.take(separator) to line.drop(separator + 1)
            }
            .toMap()
        return AppSettings(
            // An unknown value means a downgrade or a hand-edited file; falling back
            // to the default is better than refusing to start.
            theme = ThemeSetting.entries.firstOrNull { it.name == values["theme"] }
                ?: ThemeSetting.System,
            useSystemColours = values["systemColours"] == "true",
            keepHistory = values["keepHistory"] != "false",
        )
    }

    private fun write(settings: AppSettings) = TextFile.write(
        FILE,
        buildString {
            append("theme=").append(settings.theme.name).append('\n')
            append("systemColours=").append(settings.useSystemColours).append('\n')
            append("keepHistory=").append(settings.keepHistory).append('\n')
        },
    )
}
