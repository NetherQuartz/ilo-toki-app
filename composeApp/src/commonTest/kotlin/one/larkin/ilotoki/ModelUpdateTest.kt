package one.larkin.ilotoki

import one.larkin.ilotoki.model.ModelCatalog
import one.larkin.ilotoki.model.ModelState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The loje dot on the gear is the only thing in the app that asks for attention,
 * so what lights it is worth pinning: a dot that is always on is noise, and one
 * that never comes on means nobody ever learns a better translator exists.
 */
class ModelUpdateTest {

    private fun state(index: Int, downloaded: Boolean, selected: Boolean) = ModelState(
        spec = ModelCatalog.entries[index],
        downloaded = downloaded,
        selected = selected,
    )

    @Test
    fun aNewerEntryThatIsNotOnTheDeviceIsAnUpdate() {
        val entries = listOf(
            state(0, downloaded = false, selected = false),
            state(1, downloaded = true, selected = true),
        )
        assertTrue(hasNewerThanSelected(entries))
    }

    @Test
    fun anEntryAlreadyOnTheDeviceIsAChoiceNotAnUpdate() {
        val entries = listOf(
            state(0, downloaded = true, selected = false),
            state(1, downloaded = true, selected = true),
        )
        assertFalse(hasNewerThanSelected(entries))
    }

    @Test
    fun theNewestSelectionHasNothingAboveIt() {
        val entries = listOf(
            state(0, downloaded = true, selected = true),
            state(1, downloaded = false, selected = false),
        )
        assertFalse(hasNewerThanSelected(entries))
    }

    /** The list is empty for the first frames, before the disk has been read. */
    @Test
    fun anEmptyListIsNotAnUpdate() {
        assertFalse(hasNewerThanSelected(emptyList()))
    }

    /**
     * *about* links to the model's own page, derived from its download URL. A wrong
     * one opens something plausible — an account, a 404 — rather than failing, so it
     * would survive review; pin it instead.
     */
    @Test
    fun everyEntryLinksToItsOwnRepository() {
        for (spec in ModelCatalog.entries) {
            assertFalse(spec.pageUrl.contains("/resolve/"), "${spec.id} still points at a file")
            assertTrue(spec.url.startsWith(spec.pageUrl), "${spec.id} left its repository")
            // huggingface.co/<owner>/<repo>, and nothing after it.
            assertEquals(5, spec.pageUrl.split("/").size, "${spec.id} is not a repository page")
        }
    }
}
