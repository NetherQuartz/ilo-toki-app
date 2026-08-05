package one.larkin.ilotoki.ui

private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0

/**
 * Two decimal places without `java.util.Locale`, which common code cannot reach.
 *
 * Every size in this app is a model, so gibibytes with two decimals is the only
 * unit needed — there is nothing here small enough to want another one.
 */
fun formatGiB(bytes: Long): String {
    val hundredths = ((bytes / BYTES_PER_GIB) * 100).toLong()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

fun gibLabel(bytes: Long): String = "${formatGiB(bytes)} GiB"

/** Whole gibibytes, for the free-space figure where the decimals are noise. */
fun roundGibLabel(bytes: Long): String = "${(bytes / BYTES_PER_GIB).toLong()} GiB"
