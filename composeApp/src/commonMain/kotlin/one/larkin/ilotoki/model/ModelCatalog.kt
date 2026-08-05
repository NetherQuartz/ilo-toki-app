package one.larkin.ilotoki.model

import one.larkin.ilotoki.PromptStyle

/**
 * One downloadable model.
 *
 * [id] is what gets persisted, so it must stay stable once released — renaming it
 * would orphan the file already on the user's device.
 */
data class ModelSpec(
    val id: String,
    /**
     * The Hugging Face repository name, so what is listed here can be looked up.
     * The `-merged` and `-GGUF` suffixes are dropped: they carry no information a
     * reader needs and only make the row wrap.
     */
    val displayName: String,
    val quantization: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    /** Each fine-tune answers to the prompt format it was trained on. */
    val promptStyle: PromptStyle,
    /** Superseded, but kept listed so anyone who already has it can still use it. */
    val deprecated: Boolean = false,
) {
    /**
     * The repository this file comes from, for the link in *about*.
     *
     * Derived from [url] rather than stored beside it: a hand-written second link
     * is a thing that silently goes stale when a catalog entry is replaced, and
     * every download URL already contains the page it belongs to.
     */
    val pageUrl: String get() = url.substringBefore("/resolve/")
}

/**
 * The models the app knows about.
 *
 * Retiring a model means deleting its entry here: files in the models directory
 * that no entry claims are removed on the next launch, which is how a superseded
 * download stops occupying a couple of gigabytes forever. Mark an entry
 * [ModelSpec.deprecated] first and drop it a release later, so that people who
 * already downloaded it are not left without a translator on the way.
 */
object ModelCatalog {
    val entries: List<ModelSpec> = listOf(
        ModelSpec(
            id = "milmmt-46-1b-v1-1-q8-0",
            displayName = "ilo-toki-1.1-MiLMMT-46-1b",
            quantization = "Q8_0",
            fileName = "ilo-toki-1.1-MiLMMT-46-1b-Q8_0.gguf",
            url = "https://huggingface.co/NetherQuartz/ilo-toki-1.1-MiLMMT-46-1b-merged/" +
                "resolve/main/ilo-toki-1.1-MiLMMT-46-1b-Q8_0.gguf",
            sizeBytes = 1_390_169_824L,
            promptStyle = PromptStyle.SourceTarget,
        ),
        ModelSpec(
            id = "milmmt-46-1b-q8-0",
            displayName = "ilo-toki-MiLMMT-46-1b",
            quantization = "Q8_0",
            fileName = "ilo-toki-MiLMMT-46-1b-Q8_0.gguf",
            url = "https://huggingface.co/NetherQuartz/ilo-toki-MiLMMT-46-1b-merged/" +
                "resolve/main/ilo-toki-MiLMMT-46-1b-Q8_0.gguf",
            sizeBytes = 1_390_169_824L,
            promptStyle = PromptStyle.SourceTarget,
            deprecated = true,
        ),
        ModelSpec(
            id = "gemma-2-2b-q6-k",
            displayName = "tatoeba-tok-multi-gemma-2-2b",
            quantization = "Q6_K",
            fileName = "tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf",
            url = "https://huggingface.co/NetherQuartz/tatoeba-tok-multi-gemma-2-2b-merged-Q6_K-GGUF/" +
                "resolve/main/tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf",
            sizeBytes = 2_151_392_800L,
            promptStyle = PromptStyle.QueryAnswer,
            deprecated = true,
        ),
    )

    val default: ModelSpec = entries.firstOrNull { !it.deprecated } ?: entries.first()

    fun byId(id: String?): ModelSpec? = entries.firstOrNull { it.id == id }

    /** File names the catalog lays claim to, including in-progress downloads. */
    fun knownFileNames(): Set<String> =
        entries.flatMap { listOf(it.fileName, "${it.fileName}.part") }.toSet()
}
