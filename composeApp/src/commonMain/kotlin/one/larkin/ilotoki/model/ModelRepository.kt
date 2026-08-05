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

/** What the translators screen needs to know about one catalog entry. */
data class ModelState(
    val spec: ModelSpec,
    val downloaded: Boolean,
    val selected: Boolean,
    /**
     * What the file actually takes on disk, including a part-finished download.
     * The storage plate adds these up rather than the catalog's declared sizes,
     * so a resumed transfer is counted for what it is so far.
     */
    val bytesOnDisk: Long = 0,
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
    private val MODEL_SUFFIXES = listOf(".gguf", ".gguf.part")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http by lazy { modelHttpClient() }

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Idle)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val _models = MutableStateFlow(emptyList<ModelState>())
    val models: StateFlow<List<ModelState>> = _models.asStateFlow()

    /**
     * The model the engine actually holds, which is not the selected one.
     *
     * Selecting a translator that has to be fetched leaves the previous one loaded
     * and answering for the length of a gigabyte download, so anything claiming
     * «this is what translates for you» has to name this rather than [selectedSpec].
     */
    private val _loaded = MutableStateFlow<ModelSpec?>(null)
    val loaded: StateFlow<ModelSpec?> = _loaded.asStateFlow()

    private var engine: LlmEngine? = null
    private var job: Job? = null
    private var selected: ModelSpec = ModelCatalog.default

    /** The loaded engine, or null while a model is still being fetched or loaded. */
    fun engineOrNull(): LlmEngine? = engine

    /**
     * The model the user has chosen, which is what the download and the translators
     * screen are about. **Not** the one answering — while a chosen model is still
     * being fetched another may be standing in for it, so the prompt format has to
     * come from [loaded] instead. Sending the wrong format does not fail loudly.
     */
    fun selectedSpec(): ModelSpec = selected

    /**
     * Loads the selected model if it is already on the device. Safe to call from
     * every composition.
     *
     * Deliberately does **not** start a download. Spending a gigabyte of someone's
     * data plan is a decision they make, by tapping the slab or picking a model on
     * the translators screen — both of which route to [fetchSelected] or [select].
     * Until then the app stays usable and simply has nothing to answer with.
     */
    fun ensureLoaded() {
        if (engine != null || job?.isActive == true) return
        job = scope.launch {
            selected = readSelection()
            removeUnknownFiles()
            refreshModels()
            if (onDisk(selected)) prepare() else loadStandIn()
        }
    }

    /**
     * Stops an in-flight download without throwing away what it has fetched.
     *
     * The `.part` file is left alone, so [fetchSelected] picks the transfer up from
     * where it stopped — pausing a 1.3 GiB download would be pointless otherwise.
     */
    fun pauseDownload() {
        if (_status.value !is ModelStatus.Downloading) return
        job?.cancel()
        job = null
        _status.value = ModelStatus.Idle
    }

    /**
     * Fetches the selected model if it is missing and loads it.
     *
     * This is the one entry point that is allowed to start a transfer, and it is
     * also the retry after a failure or a pause: every attempt resumes from the
     * `.part` file, so the two are the same operation.
     */
    fun fetchSelected() {
        if (job?.isActive == true) return
        _status.value = ModelStatus.Idle
        job = scope.launch { prepare() }
    }

    /** Switches to [spec], downloading it first if it is not on disk yet. */
    fun select(spec: ModelSpec) {
        // Nothing to do only when the chosen one is also the one running. «Selected
        // and an engine exists» is not the same thing now that another model can be
        // standing in — that reading made tapping the chosen model's own download
        // button do nothing at all, since the stand-in had filled the engine slot.
        if (spec.id == selected.id && _loaded.value?.id == spec.id) return
        job?.cancel()
        job = scope.launch {
            selected = spec
            writeSelection(spec)
            refreshModels()
            _status.value = ModelStatus.Idle
            // Only swap engines when the new one is here. Picking a translator that
            // has to be fetched used to unload the working one immediately, which
            // left the app with nothing to translate with for the length of a
            // gigabyte download; whatever is loaded stays until its replacement
            // has actually arrived.
            if (onDisk(spec)) unloadEngine()
            prepare()
        }
    }

    /**
     * Deletes [spec]'s file.
     *
     * Deleting the model in use unloads it and leaves the app with nothing to
     * translate with, which the target plate says plainly. It does not re-fetch:
     * someone who just freed a gigabyte did not ask for it back.
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
        }
    }

    /**
     * Loads whatever translator the device already has, when the chosen one is not
     * here yet.
     *
     * Otherwise the app is dead for the length of a gigabyte download while a
     * perfectly good model sits in its files directory — which is what it did, both
     * on a launch where the selection names a model that was never fetched and for
     * the whole of a switch to a new one. The catalog is newest first, so this takes
     * the best available. It never overrides a loaded engine and never downloads.
     *
     * The consequence is that the loaded model can differ from the selected one, so
     * anything reading a model has to be clear about which it wants — the prompt
     * format in particular, which fails silently against the wrong one.
     */
    private suspend fun loadStandIn() {
        if (engine != null) return
        val standIn = ModelCatalog.entries
            .firstOrNull { it.id != selected.id && onDisk(it) } ?: return
        try {
            _status.value = ModelStatus.Loading
            engine = loadLlmEngine(fileOf(standIn).toString(), LlmParams())
            _loaded.value = standIn
            _status.value = ModelStatus.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A stand-in is a courtesy. If it will not load, say nothing and carry
            // on with the download that is the actual job.
            _status.value = ModelStatus.Idle
        }
    }

    private suspend fun unloadEngine() {
        engine?.close()
        engine = null
        _loaded.value = null
    }

    private fun fileOf(spec: ModelSpec) = Path(modelsDirectory(), spec.fileName)

    private fun refreshModels() {
        _models.value = ModelCatalog.entries.map { spec ->
            val file = fileOf(spec)
            val metadata = SystemFileSystem.metadataOrNull(file)
            ModelState(
                spec = spec,
                downloaded = metadata != null,
                selected = spec.id == selected.id,
                bytesOnDisk = metadata?.size ?: partialSizeOf(file),
            )
        }
    }

    /**
     * Removes model files the catalog no longer claims.
     *
     * This is what reclaims the space taken by a model that a newer release
     * retired — without it a superseded download sits there for good.
     *
     * It matches on the model suffixes rather than deleting everything unrecognised:
     * the directory is also where the settings and the history live, and a sweep
     * that defaults to deleting would quietly eat them the next time something is
     * added beside them.
     */
    private fun removeUnknownFiles() {
        val directory = Path(modelsDirectory())
        val known = ModelCatalog.knownFileNames()
        val present = runCatching { SystemFileSystem.list(directory) }.getOrDefault(emptyList())
        for (path in present) {
            val isModelFile = MODEL_SUFFIXES.any { path.name.endsWith(it) }
            if (isModelFile && path.name !in known) {
                SystemFileSystem.delete(path, mustExist = false)
            }
        }
    }

    /**
     * The model to load, which is not simply [ModelCatalog.default].
     *
     * Anyone who never opened the translators screen has no selection file, so they
     * are implicitly on whatever the catalog calls newest — and the moment a release
     * adds a newer entry, that name points at a file they do not have. Falling
     * straight through to the default then announces NO TRANSLATOR YET and offers a
     * gigabyte download to someone whose working model is sitting right there on
     * disk. Keeping the superseded entry listed saves the *file*; this is what saves
     * the use of it.
     *
     * So an absent or unrecognised selection resolves to the newest entry actually
     * present, and only falls back to the default when the device holds nothing at
     * all — where announcing that there is no translator is the truth.
     */
    private fun readSelection(): ModelSpec {
        val file = Path(modelsDirectory(), SELECTION_FILE)
        val id = runCatching {
            SystemFileSystem.source(file).buffered().use { it.readString() }.trim()
        }.getOrNull()
        ModelCatalog.byId(id)?.let { return it }
        return ModelCatalog.entries.firstOrNull { onDisk(it) } ?: ModelCatalog.default
    }

    private fun onDisk(spec: ModelSpec) = SystemFileSystem.metadataOrNull(fileOf(spec)) != null

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

        // Outside the retry loop and in a finally: the platform's hold on the
        // process has to cover every attempt and be released on all four ways out
        // — done, given up, cancelled by a pause, or the scope dying.
        downloadBegan(spec, DownloadProgress(progressed, spec.sizeBytes))
        try {
            while (stalled < STALLED_ATTEMPTS_BEFORE_GIVING_UP) {
                try {
                    _status.value =
                        ModelStatus.Downloading(DownloadProgress(progressed, spec.sizeBytes))
                    downloadModel(http, spec.url, modelFile) { progress ->
                        _status.value = ModelStatus.Downloading(progress)
                        downloadProgressed(progress)
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
        } finally {
            downloadEnded()
        }

        throw lastError ?: IllegalStateException("the download failed")
    }

    private suspend fun prepare() {
        val spec = selected
        val modelFile = fileOf(spec)

        if (SystemFileSystem.metadataOrNull(modelFile) == null) {
            // Before the transfer, not after: the point is to be usable *during* it.
            loadStandIn()
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
            // Drops the stand-in, if a download just ran with one in place. Two of
            // these mapped at once is 2.6 GiB of a phone's memory.
            unloadEngine()
            engine = loadLlmEngine(modelFile.toString(), LlmParams())
            _loaded.value = spec
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
