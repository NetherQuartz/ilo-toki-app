package one.larkin.ilotoki.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.larkin.ilotoki.Language
import one.larkin.ilotoki.ioDispatcher
import one.larkin.ilotoki.pairLabel

/**
 * One finished translation, kept so the phrase can be found again.
 *
 * [fromTokiPona] and [other] are stored instead of a rendered pair label so that
 * renaming a language, or adding one, does not turn old rows into stale strings —
 * and so that reusing a row can restore the exact direction it was made in.
 */
data class HistoryEntry(
    val id: Long,
    val fromTokiPona: Boolean,
    val other: Language,
    val source: String,
    val result: String,
    /** `olin` — marked. Survives «clear all» and can be filtered on. */
    val olin: Boolean = false,
) {
    val pair: String get() = pairLabel(fromTokiPona, other)

    /** Which script the result can be shown in; only toki pona has the other one. */
    val resultIsTokiPona: Boolean get() = !fromTokiPona
}

/**
 * The history stack, newest first.
 *
 * Capped rather than unbounded: this is a translator, not a notebook, and the file
 * is read whole on launch. Recording is gated on [AppSettings.keepHistory]; turning
 * the setting off stops new entries but does not delete what is already there,
 * which is the less surprising of the two behaviours.
 */
object HistoryRepository {
    private const val FILE = "history"
    private const val CAPACITY = 200
    private const val FIELD_COUNT = 6

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _entries = MutableStateFlow(emptyList<HistoryEntry>())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        scope.launch { _entries.value = read() }
    }

    fun record(fromTokiPona: Boolean, other: Language, source: String, result: String) {
        val trimmedSource = source.trim()
        val trimmedResult = result.trim()
        if (trimmedSource.isEmpty() || trimmedResult.isEmpty()) return
        mutate { current ->
            val entry = HistoryEntry(
                // Two translations finishing inside the same millisecond would
                // collide, so the id is only ever nudged forward past the newest.
                id = maxOf(nowMillis(), (current.firstOrNull()?.id ?: 0L) + 1),
                fromTokiPona = fromTokiPona,
                other = other,
                source = trimmedSource,
                result = trimmedResult,
            )
            (listOf(entry) + current).take(CAPACITY)
        }
    }

    fun toggleOlin(id: Long) = mutate { current ->
        current.map { if (it.id == id) it.copy(olin = !it.olin) else it }
    }

    fun remove(id: Long) = mutate { current -> current.filterNot { it.id == id } }

    /** Keeps whatever is marked with `olin`; that is the point of the mark. */
    fun clearUnmarked() = mutate { current -> current.filter { it.olin } }

    private fun mutate(transform: (List<HistoryEntry>) -> List<HistoryEntry>) {
        val next = transform(_entries.value)
        if (next == _entries.value) return
        _entries.value = next
        scope.launch { write(next) }
    }

    private fun read(): List<HistoryEntry> {
        val text = TextFile.read(FILE) ?: return emptyList()
        return text.lineSequence().mapNotNull(::parse).take(CAPACITY).toList()
    }

    private fun parse(line: String): HistoryEntry? {
        if (line.isBlank()) return null
        val fields = line.split('\t')
        if (fields.size != FIELD_COUNT) return null
        val id = fields[0].toLongOrNull() ?: return null
        val other = Language.entries.firstOrNull { it.name == fields[2] } ?: return null
        return HistoryEntry(
            id = id,
            fromTokiPona = fields[1] == "true",
            other = other,
            source = fields[3].unescapeField(),
            result = fields[4].unescapeField(),
            olin = fields[5] == "true",
        )
    }

    private fun write(entries: List<HistoryEntry>) = TextFile.write(
        FILE,
        entries.joinToString("\n") { entry ->
            listOf(
                entry.id.toString(),
                entry.fromTokiPona.toString(),
                entry.other.name,
                entry.source.escapeField(),
                entry.result.escapeField(),
                entry.olin.toString(),
            ).joinToString("\t")
        },
    )
}

/** Wall clock in milliseconds; only ever used to order and identify entries. */
internal expect fun nowMillis(): Long
