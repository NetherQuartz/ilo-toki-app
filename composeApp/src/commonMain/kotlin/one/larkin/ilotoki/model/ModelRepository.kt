package one.larkin.ilotoki.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import one.larkin.ilotoki.llm.LlmEngine
import one.larkin.ilotoki.llm.LlmParams
import one.larkin.ilotoki.llm.loadLlmEngine
import one.larkin.ilotoki.modelsDirectory

sealed interface ModelStatus {
    data object Idle : ModelStatus

    data class Downloading(val progress: DownloadProgress) : ModelStatus

    data object Loading : ModelStatus

    data object Ready : ModelStatus

    data class Failed(val message: String) : ModelStatus
}

/**
 * Owns the downloaded model and the loaded engine for the whole process.
 *
 * Deliberately not tied to a ViewModel: loading costs a multi-second read of a 2 GB
 * file, and a ViewModel is recreated on every configuration change.
 */
object ModelRepository {
    private const val STALLED_ATTEMPTS_BEFORE_GIVING_UP = 3
    private const val RETRY_DELAY_MILLIS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http by lazy { modelHttpClient() }

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Idle)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private var engine: LlmEngine? = null
    private var job: Job? = null

    /** The loaded engine, or null while the model is still being fetched or loaded. */
    fun engineOrNull(): LlmEngine? = engine

    /**
     * Starts fetching and loading the model if that is not already done or under way.
     * Safe to call from every composition.
     */
    fun ensureLoaded() {
        if (engine != null || job?.isActive == true) return
        job = scope.launch { prepare() }
    }

    /** Retries after a failure, resuming a partial download where one exists. */
    fun retry() {
        if (job?.isActive == true) return
        _status.value = ModelStatus.Idle
        job = scope.launch { prepare() }
    }

    /**
     * Downloads the model, retrying by itself when the connection drops.
     *
     * A CDN resetting the stream part-way through a 2 GB transfer is routine, and
     * every attempt resumes from the `.part` file, so retrying is cheap. Attempts
     * that fail without moving a single byte forward are the ones worth giving up
     * on — those are a dead network or a gone URL, not a flaky connection.
     */
    private suspend fun download(modelFile: Path) {
        var progressed = partialSizeOf(modelFile)
        var stalled = 0
        var lastError: Exception? = null

        while (stalled < STALLED_ATTEMPTS_BEFORE_GIVING_UP) {
            try {
                _status.value = ModelStatus.Downloading(DownloadProgress(progressed, -1))
                downloadModel(http, ModelSpec.URL, modelFile) { progress ->
                    _status.value = ModelStatus.Downloading(progress)
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                val downloaded = partialSizeOf(modelFile)
                stalled = if (downloaded > progressed) 0 else stalled + 1
                progressed = downloaded
                delay(RETRY_DELAY_MILLIS)
            }
        }

        throw lastError ?: IllegalStateException("the download failed")
    }

    private suspend fun prepare() {
        val modelFile = Path(modelsDirectory(), ModelSpec.FILE_NAME)

        if (SystemFileSystem.metadataOrNull(modelFile) == null) {
            try {
                download(modelFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The .part file is kept on purpose so a retry resumes instead of
                // starting the 2 GB transfer over again.
                _status.value = ModelStatus.Failed(e.message ?: "the download failed")
                return
            }
        }

        try {
            _status.value = ModelStatus.Loading
            engine = loadLlmEngine(modelFile.toString(), LlmParams())
            _status.value = ModelStatus.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A model that will not load is a bad file; drop it so the retry refetches.
            SystemFileSystem.delete(modelFile, mustExist = false)
            _status.value = ModelStatus.Failed(e.message ?: "the model could not be loaded")
        }
    }
}
