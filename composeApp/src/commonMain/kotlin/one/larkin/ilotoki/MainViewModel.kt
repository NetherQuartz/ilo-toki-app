package one.larkin.ilotoki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.larkin.ilotoki.data.HistoryRepository
import one.larkin.ilotoki.data.SettingsRepository
import one.larkin.ilotoki.model.ModelRepository
import one.larkin.ilotoki.model.ModelSpec
import one.larkin.ilotoki.model.ModelState
import one.larkin.ilotoki.ui.theme.ThemeSetting
import kotlin.time.TimeSource

data class TranslatorState(
    val query: String = "",
    val result: String = "",
    val fromTokiPona: Boolean = true,
    val target: Language = Language.English,
    /**
     * Which script the toki pona side is drawn in. Not a setting — it is flipped
     * from the plate that holds toki pona, and belongs to the text being read.
     */
    val useSitelenPona: Boolean = false,
    val isTranslating: Boolean = false,
    /** Live decoding speed, for the stamp on the target plate. Zero when idle. */
    val tokensPerSecond: Float = 0f,
    val error: String? = null,
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(TranslatorState())
    val state: StateFlow<TranslatorState> = _state.asStateFlow()

    val modelStatus = ModelRepository.status
    val models = ModelRepository.models
    val loadedModel = ModelRepository.loaded
    val settings = SettingsRepository.settings
    val history = HistoryRepository.entries

    /**
     * Whether a better translator is waiting: a catalog entry listed above the one
     * in use, still current, and not on the device. This is what lights the loje dot
     * on the gear — the only thing on the main screen that ever asks for attention.
     */
    val hasUpdate: StateFlow<Boolean> = models
        .map(::hasNewerThanSelected)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var translation: Job? = null

    fun onStart() {
        SettingsRepository.ensureLoaded()
        HistoryRepository.ensureLoaded()
        ModelRepository.ensureLoaded()
    }

    /** «get the translator» on the slab, and «try again» after a failure. */
    fun getTranslator() = ModelRepository.fetchSelected()

    fun pauseDownload() = ModelRepository.pauseDownload()

    fun selectModel(spec: ModelSpec) {
        cancelTranslation()
        _state.update { it.copy(result = "", isTranslating = false, error = null) }
        ModelRepository.select(spec)
    }

    fun deleteModel(spec: ModelSpec) {
        cancelTranslation()
        _state.update { it.copy(result = "", isTranslating = false, error = null) }
        ModelRepository.delete(spec)
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query, error = null) }

    fun onTargetChange(target: Language) = _state.update {
        // The old result was in the language that just stopped being the target.
        it.copy(target = target, result = "", error = null)
    }

    fun toggleSitelenPona() = _state.update { it.copy(useSitelenPona = !it.useSitelenPona) }

    fun setTheme(theme: ThemeSetting) = SettingsRepository.setTheme(theme)

    fun setUseSystemColours(enabled: Boolean) = SettingsRepository.setUseSystemColours(enabled)

    fun setKeepHistory(enabled: Boolean) = SettingsRepository.setKeepHistory(enabled)

    fun toggleOlin(id: Long) = HistoryRepository.toggleOlin(id)

    fun removeHistoryEntry(id: Long) = HistoryRepository.remove(id)

    /** Clears the stack but keeps everything marked with `olin`. */
    fun clearHistory() = HistoryRepository.clearUnmarked()

    /** Puts a past phrase back in the input, in the direction it was made in. */
    fun reuse(source: String, fromTokiPona: Boolean, other: Language) {
        cancelTranslation()
        _state.update {
            it.copy(
                query = source,
                result = "",
                fromTokiPona = fromTokiPona,
                target = other,
                isTranslating = false,
                error = null,
            )
        }
    }

    /**
     * Flips the direction, feeding the previous result back in as the new query so
     * a round trip does not need retyping.
     */
    fun swapDirection() {
        cancelTranslation()
        _state.update { current ->
            current.copy(
                fromTokiPona = !current.fromTokiPona,
                query = current.result.ifEmpty { current.query },
                result = "",
                isTranslating = false,
                tokensPerSecond = 0f,
                error = null,
            )
        }
    }

    fun translate() {
        val current = _state.value
        if (current.query.isBlank()) return

        val engine = ModelRepository.engineOrNull()
        if (engine == null) {
            _state.update { it.copy(error = "the translator is not ready yet") }
            return
        }

        // A second request replaces the first rather than queuing behind it.
        cancelTranslation()
        _state.update { it.copy(result = "", isTranslating = true, tokensPerSecond = 0f, error = null) }

        val prompt = translationPrompt(
            text = current.query,
            fromTokiPona = current.fromTokiPona,
            other = current.target,
            style = ModelRepository.selectedSpec().promptStyle,
        )
        translation = viewModelScope.launch {
            val started = TimeSource.Monotonic.markNow()
            var pieces = 0
            try {
                engine.generate(prompt).collect { piece ->
                    pieces++
                    // One piece is one decoded token, so this is the real rate as it
                    // happens; the engine's own figure is only final once it stops.
                    val seconds = started.elapsedNow().inWholeMilliseconds / 1000f
                    val rate = if (seconds > 0f) pieces / seconds else 0f
                    _state.update { it.copy(result = it.result + piece, tokensPerSecond = rate) }
                }
                val rate = ModelRepository.engineOrNull()?.tokensPerSecond ?: 0f
                _state.update { it.copy(isTranslating = false, tokensPerSecond = rate) }
                if (SettingsRepository.settings.value.keepHistory) {
                    HistoryRepository.record(
                        fromTokiPona = current.fromTokiPona,
                        other = current.target,
                        source = current.query,
                        result = _state.value.result,
                    )
                }
            } catch (e: CancellationException) {
                // Superseded by a newer request, which owns the state from here on.
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isTranslating = false, error = e.message ?: "translation failed")
                }
            }
        }
    }

    private fun cancelTranslation() {
        translation?.cancel()
        translation = null
    }
}

/**
 * True when a still-current entry above the selected one is missing from the device.
 *
 * The catalog is ordered newest first, so «above» is «newer»; an entry that is
 * already downloaded is not an update, it is a choice the user has already made.
 */
internal fun hasNewerThanSelected(entries: List<ModelState>): Boolean {
    val selectedIndex = entries.indexOfFirst { it.selected }
    if (selectedIndex < 0) return false
    return entries.take(selectedIndex).any { !it.spec.deprecated && !it.downloaded }
}
