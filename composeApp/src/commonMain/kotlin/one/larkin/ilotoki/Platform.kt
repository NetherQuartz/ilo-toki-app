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
