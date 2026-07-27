package one.larkin.ilotoki

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher for blocking file I/O. `Dispatchers.IO` exists on both targets but is
 * not part of the common API surface, so it is bound per platform here.
 */
expect val ioDispatcher: CoroutineDispatcher

/**
 * Directory the downloaded model lives in. Must be app-private, survive restarts
 * and be excluded from cloud backups — the file is over 2 GB.
 */
expect fun modelsDirectory(): String

/**
 * Bytes still free on the volume the models live on, or 0 when it cannot be read.
 *
 * The storage plate reports this and nothing else — a bar against total capacity
 * would be meaningless when a model is one or two gigabytes on a device that has
 * a hundred and something free.
 */
expect fun freeDiskBytes(): Long

/** The version the about card shows, read from the platform's own bundle metadata. */
expect val appVersion: String

/** Opens [url] in whatever the system uses for links. Fails silently if nothing does. */
expect fun openUrl(url: String)
