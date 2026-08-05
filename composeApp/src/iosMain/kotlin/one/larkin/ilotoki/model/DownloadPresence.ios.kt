package one.larkin.ilotoki.model

/**
 * Not implemented yet, and deliberately not faked.
 *
 * iOS suspends a backgrounded app, so a transfer running on the app's own
 * coroutine stops there just as it did on Android — the difference is the cure.
 * Android only has to say «leave this process alone» and the same download
 * carries on; iOS wants the transfer handed to the system instead, through a
 * background `NSURLSession`, which continues out of process and survives the app
 * being killed. That is a different download rather than the same one kept alive,
 * so it cannot hide behind these three calls: it replaces the ktor path on this
 * platform and has to report progress back through a session delegate.
 *
 * `beginBackgroundTask` was considered and rejected: about thirty seconds of grace
 * covers switching apps and coming straight back, which would make the bug look
 * fixed while a gigabyte over a slow connection still fails exactly as before.
 */
actual fun downloadBegan(spec: ModelSpec, progress: DownloadProgress) = Unit

actual fun downloadProgressed(progress: DownloadProgress) = Unit

actual fun downloadEnded() = Unit
