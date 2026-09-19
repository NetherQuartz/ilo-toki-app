package one.larkin.ilotoki

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The answer's leading space is invisible on the plate and visible everywhere it
 * goes afterwards — the input field after a swap, the history card — so what is
 * pinned here is that it never enters the string in the first place, and that the
 * trim stops at the head instead of eating the spaces between words.
 */
class ResultAssemblyTest {

    private fun assemble(pieces: List<String>): String =
        pieces.fold("") { result, piece -> appendPiece(result, piece) }

    @Test
    fun theSpaceTheModelOpensWithIsDropped() {
        assertEquals("Привет, мир", assemble(listOf(" При", "вет,", " мир")))
    }

    @Test
    fun aPieceThatIsNothingButSpaceDoesNotUseUpTheTrim() {
        assertEquals("mi olin", assemble(listOf(" ", " mi", " olin")))
    }

    @Test
    fun spacesInsideTheAnswerAreLeftAlone() {
        assertEquals("the man eats  fruit", assemble(listOf("the man", " eats ", " fruit")))
    }
}
