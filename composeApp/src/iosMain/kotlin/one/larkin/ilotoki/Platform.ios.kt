package one.larkin.ilotoki

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * `Dispatchers.IO` is not public on Kotlin/Native. A small slice of the default
 * pool is enough: the only blocking work here is writing download chunks to disk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(4)

/**
 * Application Support rather than Documents: the model is a re-downloadable cache
 * that the user should never see in the Files app. The backup-exclusion flag keeps
 * iCloud from trying to upload 2 GB, and applies to everything inside the folder.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun modelsDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val supportDirectory: NSURL = requireNotNull(
        fileManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ),
    ) { "could not resolve the Application Support directory" }

    val modelsDirectory = requireNotNull(
        supportDirectory.URLByAppendingPathComponent("models", isDirectory = true),
    ) { "could not build the models directory path" }

    fileManager.createDirectoryAtURL(
        url = modelsDirectory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    modelsDirectory.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)

    return requireNotNull(modelsDirectory.path) { "the models directory has no filesystem path" }
}
