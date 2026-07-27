package one.larkin.ilotoki

/** The languages the model was fine-tuned to translate to and from Toki Pona. */
enum class Language(val modelName: String, val flag: String) {
    English("English", "🇺🇸"),
    Russian("Russian", "🇷🇺"),
    Vietnamese("Vietnamese", "🇻🇳"),
}

const val TOKI_PONA = "Toki Pona"

/**
 * The prompt format the model was fine-tuned on. It is a plain completion prompt,
 * not a chat exchange, so no chat template is applied.
 */
fun translationPrompt(text: String, fromTokiPona: Boolean, other: Language): String {
    val source = if (fromTokiPona) TOKI_PONA else other.modelName
    val target = if (fromTokiPona) other.modelName else TOKI_PONA
    return "Translate $source to $target.\nQuery: ${text.trim()}\nAnswer:"
}
