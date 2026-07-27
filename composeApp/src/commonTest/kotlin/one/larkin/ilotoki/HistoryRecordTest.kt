package one.larkin.ilotoki

import one.larkin.ilotoki.data.escapeField
import one.larkin.ilotoki.data.unescapeField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * History records are one tab-separated line each, and the input is a multi-line
 * text field, so a pasted paragraph would split one entry into several malformed
 * ones. Nothing throws when that happens — the row is simply dropped on the next
 * read — which is why the round trip is pinned here rather than left to review.
 */
class HistoryRecordTest {

    @Test
    fun separatorsSurviveTheRoundTrip() {
        val awkward = "mi\ttoki\nlon tomo\\ni\r\nsina"
        assertEquals(awkward, awkward.escapeField().unescapeField())
    }

    @Test
    fun anEscapedFieldCarriesNoSeparators() {
        val escaped = "one\ttwo\nthree".escapeField()
        assertFalse(escaped.contains('\t'), "a tab would split the record")
        assertFalse(escaped.contains('\n'), "a newline would split the file")
    }

    @Test
    fun aTrailingBackslashIsNotSwallowed() {
        // The unescaper looks one character ahead; the last character has none.
        assertEquals("tomo\\", "tomo\\".escapeField().unescapeField())
    }
}
