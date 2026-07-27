package one.larkin.ilotoki

/** The languages the model was fine-tuned to translate to and from Toki Pona. */
enum class Language(val modelName: String, val flag: String) {
    English("English", "🇺🇸"),
    Russian("Russian", "🇷🇺"),
    Vietnamese("Vietnamese", "🇻🇳"),
}

const val TOKI_PONA = "Toki Pona"

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
