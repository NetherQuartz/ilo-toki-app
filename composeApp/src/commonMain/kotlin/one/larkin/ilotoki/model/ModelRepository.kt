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
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
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

/** What the picker needs to know about one catalog entry. */
data class ModelState(
    val spec: ModelSpec,
    val downloaded: Boolean,
    val selected: Boolean,
)

/**
 * Owns the downloaded models and the loaded engine for the whole process.
 *
 * Deliberately not tied to a ViewModel: loading costs a multi-second read of a
 * multi-gigabyte file, and a ViewModel is recreated on every configuration change.
 */
object ModelRepository {
    private const val STALLED_ATTEMPTS_BEFORE_GIVING_UP = 3
    private const val RETRY_DELAY_MILLIS = 2_000L
    private const val SELECTION_FILE = "selected-model"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http by lazy { modelHttpClient() }

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Idle)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val _models = MutableStateFlow(emptyList<ModelState>())
    val models: StateFlow<List<ModelState>> = _models.asStateFlow()

    private var engine: LlmEngine? = null
    private var job: Job? = null
    private var selected: ModelSpec = ModelCatalog.default

    /** The loaded engine, or null while a model is still being fetched or loaded. */
    fun engineOrNull(): LlmEngine? = engine

    /** The model currently in use, which decides the prompt format. */
    fun selectedSpec(): ModelSpec = selected

    /**
     * Starts fetching and loading the selected model if that is not already done or
     * under way. Safe to call from every composition.
     */
    fun ensureLoaded() {
        if (engine != null || job?.isActive == true) return
        job = scope.launch {
            selected = readSelection()
            removeUnknownFiles()
            refreshModels()
            prepare()
        }
    }

    /** Retries after a failure, resuming a partial download where one exists. */
    fun retry() {
        if (job?.isActive == true) return
        _status.value = ModelStatus.Idle
        job = scope.launch { prepare() }
    }

    /** Switches to [spec], downloading it first if it is not on disk yet. */
    fun select(spec: ModelSpec) {
        if (spec.id == selected.id && engine != null) return
        job?.cancel()
        job = scope.launch {
            unloadEngine()
            selected = spec
            writeSelection(spec)
            refreshModels()
            _status.value = ModelStatus.Idle
            prepare()
        }
    }

    /**
     * Deletes [spec]'s file. Deleting the model in use unloads it first and leaves
     * the app on the download screen, which is the honest thing to show.
     */
    fun delete(spec: ModelSpec) {
        job?.cancel()
        job = scope.launch {
            if (spec.id == selected.id) {
                unloadEngine()
                _status.value = ModelStatus.Idle
            }
            val file = fileOf(spec)
            SystemFileSystem.delete(file, mustExist = false)
            SystemFileSystem.delete(partialFileOf(file), mustExist = false)
            refreshModels()
            if (spec.id == selected.id) prepare()
        }
    }

    private suspend fun unloadEngine() {
        engine?.close()
        engine = null
    }

    private fun fileOf(spec: ModelSpec) = Path(modelsDirectory(), spec.fileName)

    private fun refreshModels() {
        _models.value = ModelCatalog.entries.map { spec ->
            ModelState(
                spec = spec,
                downloaded = SystemFileSystem.metadataOrNull(fileOf(spec)) != null,
                selected = spec.id == selected.id,
            )
        }
    }

    /**
     * Removes anything in the models directory the catalog does not claim.
     *
     * This is what reclaims the space taken by a model that a newer release
     * retired — without it a superseded download sits there for good.
     */
    private fun removeUnknownFiles() {
        val directory = Path(modelsDirectory())
        val known = ModelCatalog.knownFileNames() + SELECTION_FILE
        val present = runCatching { SystemFileSystem.list(directory) }.getOrDefault(emptyList())
        for (path in present) {
            if (path.name !in known) {
                SystemFileSystem.delete(path, mustExist = false)
            }
        }
    }

    private fun readSelection(): ModelSpec {
        val file = Path(modelsDirectory(), SELECTION_FILE)
        val id = runCatching {
            SystemFileSystem.source(file).buffered().use { it.readString() }.trim()
        }.getOrNull()
        return ModelCatalog.byId(id) ?: ModelCatalog.default
    }

    private fun writeSelection(spec: ModelSpec) {
        val file = Path(modelsDirectory(), SELECTION_FILE)
        runCatching {
            SystemFileSystem.sink(file).buffered().use { it.writeString(spec.id) }
        }
    }

    /**
     * Downloads the model, retrying by itself when the connection drops.
     *
     * A CDN resetting the stream part-way through a multi-gigabyte transfer is
     * routine, and every attempt resumes from the `.part` file, so retrying is
     * cheap. Attempts that fail without moving a single byte forward are the ones
     * worth giving up on — those are a dead network or a gone URL, not a flaky
     * connection.
     */
    private suspend fun download(spec: ModelSpec, modelFile: Path) {
        var progressed = partialSizeOf(modelFile)
        var stalled = 0
        var lastError: Exception? = null

        while (stalled < STALLED_ATTEMPTS_BEFORE_GIVING_UP) {
            try {
                _status.value = ModelStatus.Downloading(DownloadProgress(progressed, spec.sizeBytes))
                downloadModel(http, spec.url, modelFile) { progress ->
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
        val spec = selected
        val modelFile = fileOf(spec)

        if (SystemFileSystem.metadataOrNull(modelFile) == null) {
            try {
                download(spec, modelFile)
                refreshModels()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The .part file is kept on purpose so a retry resumes instead of
                // starting the whole transfer over again.
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
            refreshModels()
            _status.value = ModelStatus.Failed(e.message ?: "the model could not be loaded")
        }
    }
}
