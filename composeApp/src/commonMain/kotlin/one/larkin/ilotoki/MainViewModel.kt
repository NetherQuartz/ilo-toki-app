package one.larkin.ilotoki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.larkin.ilotoki.model.ModelRepository

data class TranslatorState(
    val query: String = "",
    val result: String = "",
    val fromTokiPona: Boolean = true,
    val target: Language = Language.English,
    val useSitelenPona: Boolean = false,
    val isTranslating: Boolean = false,
    val error: String? = null,
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(TranslatorState())
    val state: StateFlow<TranslatorState> = _state.asStateFlow()

    val modelStatus = ModelRepository.status

    private var translation: Job? = null

    fun onStart() = ModelRepository.ensureLoaded()

    fun retryModel() = ModelRepository.retry()

    fun onQueryChange(query: String) = _state.update { it.copy(query = query, error = null) }

    fun onTargetChange(target: Language) = _state.update { it.copy(target = target) }

    fun onSitelenPonaChange(enabled: Boolean) = _state.update { it.copy(useSitelenPona = enabled) }

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
                error = null,
            )
        }
    }

    fun translate() {
        val current = _state.value
        if (current.query.isBlank()) return

        val engine = ModelRepository.engineOrNull()
        if (engine == null) {
            _state.update { it.copy(error = "the model is not loaded yet") }
            return
        }

        // A second request replaces the first rather than queuing behind it.
        cancelTranslation()
        _state.update { it.copy(result = "", isTranslating = true, error = null) }

        val prompt = translationPrompt(current.query, current.fromTokiPona, current.target)
        translation = viewModelScope.launch {
            try {
                engine.generate(prompt).collect { piece ->
                    _state.update { it.copy(result = it.result + piece) }
                }
                _state.update { it.copy(isTranslating = false) }
            } catch (e: CancellationException) {
                // Superseded by a newer request, which owns the state from here on.
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isTranslating = false, error = e.message ?: "translation failed") }
            }
        }
    }

    private fun cancelTranslation() {
        translation?.cancel()
        translation = null
    }
}
