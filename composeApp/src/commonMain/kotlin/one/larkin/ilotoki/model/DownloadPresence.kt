package one.larkin.ilotoki.model

/**
 * Keeps a transfer running, and visible, while the app is not on screen.
 *
 * The download itself stays where it is — one coroutine in [ModelRepository], the
 * same code on both platforms. This is only what tells the operating system that
 * the work is worth leaving alone, because otherwise it is not: Android freezes a
 * cached process, so backgrounding the app stopped a gigabyte download dead, with
 * no error and no notice — it simply had not moved when the app came back.
 *
 * Deliberately three calls rather than a wrapper around the download: whatever the
 * platform needs may outlive a single attempt (the loop retries a dropped
 * connection several times) and must be torn down exactly once at the end.
 */

/** A transfer of [spec] is starting. Safe to call again for a retry of the same one. */
expect fun downloadBegan(spec: ModelSpec, progress: DownloadProgress)

/** Newer figures for the transfer already announced by [downloadBegan]. */
expect fun downloadProgressed(progress: DownloadProgress)

/** The transfer finished, failed or was paused. Always called, exactly once. */
expect fun downloadEnded()

/**
 * A model that had to be fetched is now loaded and answering.
 *
 * Called only when this run actually downloaded something, so an ordinary launch
 * is silent, and it is the platform's job to keep quiet when the app is on screen
 * — someone watching the plate does not need to be told what it already says. The
 * progress notification takes itself away when the transfer ends, and without this
 * a download waited out in another app finishes to nothing at all.
 */
expect fun translatorReady(spec: ModelSpec)
