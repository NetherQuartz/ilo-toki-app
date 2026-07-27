package one.larkin.ilotoki

import one.larkin.ilotoki.model.ModelCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Prompt formats are pinned rather than merely exercised: sending a model the
 * wrong one does not throw, it just makes the model ignore the requested target
 * language, which is easy to miss in review and obvious only to a user.
 */
class TranslationPromptTest {

    @Test
    fun queryAnswerFormatIsUnchanged() {
        assertEquals(
            "Translate Toki Pona to Russian.\nQuery: mi olin e sina\nAnswer:",
            translationPrompt(
                text = "  mi olin e sina  ",
                fromTokiPona = true,
                other = Language.Russian,
                style = PromptStyle.QueryAnswer,
            ),
        )
    }

    @Test
    fun sourceTargetFormatMatchesTheBaseModel() {
        assertEquals(
            "Translate this from English to Toki Pona:\nEnglish: The man eats fruit.\nToki Pona:",
            translationPrompt(
                text = "The man eats fruit.",
                fromTokiPona = false,
                other = Language.English,
                style = PromptStyle.SourceTarget,
            ),
        )
    }

    @Test
    fun everyCatalogEntryKeepsItsOwnFormat() {
        val styles = ModelCatalog.entries.associate { it.id to it.promptStyle }
        assertEquals(PromptStyle.QueryAnswer, styles["gemma-2-2b-q6-k"])
        assertEquals(PromptStyle.SourceTarget, styles["milmmt-46-1b-q8-0"])
    }
}
