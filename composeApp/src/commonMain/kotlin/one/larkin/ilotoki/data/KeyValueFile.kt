package one.larkin.ilotoki.data

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import one.larkin.ilotoki.modelsDirectory

/**
 * The app's whole persistence layer: a couple of small text files next to the
 * models directory.
 *
 * There are two things to keep — a handful of settings and a capped list of
 * translations — and neither is worth a database or the DataStore dependency,
 * which would also have to be bound per platform. Everything is written whole and
 * read whole; a corrupt or half-written file is discarded rather than repaired,
 * because losing the history is a smaller problem than failing to start.
 */
internal object TextFile {

    fun read(name: String): String? = runCatching {
        val path = Path(modelsDirectory(), name)
        if (SystemFileSystem.metadataOrNull(path) == null) return null
        SystemFileSystem.source(path).buffered().use { it.readString() }
    }.getOrNull()

    fun write(name: String, content: String) {
        runCatching {
            SystemFileSystem.sink(Path(modelsDirectory(), name)).buffered()
                .use { it.writeString(content) }
        }
    }
}

/**
 * Escapes the separators out of a field so a record survives a round trip.
 *
 * Translations routinely contain tabs and, since the input is a multi-line text
 * field, newlines — without this a single pasted paragraph would silently split
 * one history entry into several malformed ones.
 */
internal fun String.escapeField(): String = buildString(length) {
    for (character in this@escapeField) {
        when (character) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}

internal fun String.unescapeField(): String = buildString(length) {
    var index = 0
    while (index < this@unescapeField.length) {
        val character = this@unescapeField[index]
        if (character != '\\' || index == this@unescapeField.lastIndex) {
            append(character)
            index++
            continue
        }
        when (this@unescapeField[index + 1]) {
            'n' -> append('\n')
            'r' -> append('\r')
            't' -> append('\t')
            '\\' -> append('\\')
            else -> append(this@unescapeField[index + 1])
        }
        index += 2
    }
}
