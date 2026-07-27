package one.larkin.ilotoki

/**
 * The languages the model was fine-tuned to translate to and from Toki Pona.
 *
 * [modelName] goes into the prompt and must stay in English — it is what the
 * fine-tune was trained on. [displayName] is what a person reads, so it is the
 * endonym; the two are deliberately not the same string.
 */
enum class Language(val modelName: String, val displayName: String) {
    English("English", "English"),
    Russian("Russian", "Русский"),
    Vietnamese("Vietnamese", "Tiếng Việt"),
}

const val TOKI_PONA = "Toki Pona"

/** «TOKI PONA → ENGLISH», the pair stamp above each plate and on history cards. */
fun pairLabel(fromTokiPona: Boolean, other: Language): String {
    val source = if (fromTokiPona) TOKI_PONA else other.displayName
    val target = if (fromTokiPona) other.displayName else TOKI_PONA
    return "${source.uppercase()} → ${target.uppercase()}"
}

/**
 * How a model wants its translation request phrased. This belongs to the model
 * rather than the app: a fine-tune answers to the format it was trained on, and
 * sending the wrong one does not fail loudly — the model keeps producing fluent
 * text while quietly ignoring the target language.
 */
enum class PromptStyle {
    /** `Translate X to Y.` / `Query:` / `Answer:` — the gemma-2 fine-tune. */
    QueryAnswer,

    /** `Translate this from X to Y:` / `X:` / `Y:` — inherited from MiLMMT. */
    SourceTarget,
}

/**
 * Builds a plain completion prompt. None of these models use a chat template.
 */
fun translationPrompt(
    text: String,
    fromTokiPona: Boolean,
    other: Language,
    style: PromptStyle,
): String {
    val source = if (fromTokiPona) TOKI_PONA else other.modelName
    val target = if (fromTokiPona) other.modelName else TOKI_PONA
    val query = text.trim()
    return when (style) {
        PromptStyle.QueryAnswer -> "Translate $source to $target.\nQuery: $query\nAnswer:"
        PromptStyle.SourceTarget -> "Translate this from $source to $target:\n$source: $query\n$target:"
    }
}
