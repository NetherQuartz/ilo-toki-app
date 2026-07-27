package one.larkin.ilotoki.model

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import one.larkin.ilotoki.ioDispatcher

/** Bytes fetched so far out of the total, or -1 when the server did not report a length. */
data class DownloadProgress(val downloaded: Long, val total: Long)

/** Where an in-flight download of [target] accumulates until it is complete. */
fun partialFileOf(target: Path): Path = Path("$target.part")

/** Bytes already on disk for an interrupted download of [target]. */
fun partialSizeOf(target: Path): Long =
    SystemFileSystem.metadataOrNull(partialFileOf(target))?.size ?: 0L

private const val BUFFER_SIZE = 1 shl 16

fun modelHttpClient(): HttpClient = HttpClient {
    install(HttpTimeout) {
        // A 2 GB body over a slow connection must not trip a request timeout, but a
        // stalled socket should still fail rather than hang forever.
        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        socketTimeoutMillis = 60_000
        connectTimeoutMillis = 30_000
    }
    followRedirects = true
}

/**
 * Downloads [url] to [target], resuming a previous attempt when possible.
 *
 * The body is written to a sibling `.part` file and only moved into place once it
 * is complete, so an interrupted download can never be mistaken for a usable model.
 * If the `.part` file already exists the transfer continues from its end via a Range
 * request; servers that ignore Range simply restart it.
 */
suspend fun downloadModel(
    client: HttpClient,
    url: String,
    target: Path,
    onProgress: (DownloadProgress) -> Unit,
): Unit = withContext(ioDispatcher) {
    val partial = partialFileOf(target)
    target.parent?.let { SystemFileSystem.createDirectories(it) }

    val alreadyHave = SystemFileSystem.metadataOrNull(partial)?.size ?: 0L

    client.prepareGet(url) {
        if (alreadyHave > 0) {
            header(HttpHeaders.Range, "bytes=$alreadyHave-")
        }
    }.execute { response ->
        val resuming = alreadyHave > 0 && response.status == HttpStatusCode.PartialContent
        var downloaded = if (resuming) alreadyHave else 0L
        val total = response.contentLength()?.let { it + downloaded } ?: -1L

        onProgress(DownloadProgress(downloaded, total))

        val channel = response.bodyAsChannel()
        // append only when the server honoured the Range request; otherwise the body
        // starts from byte zero again and the old partial data has to go.
        SystemFileSystem.sink(partial, append = resuming).buffered().use { sink ->
            val buffer = ByteArray(BUFFER_SIZE)
            var lastReported = downloaded
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == 0) continue
                sink.write(buffer, 0, read)
                downloaded += read
                // Reporting every chunk would swamp the UI; ~4 MB is smooth enough.
                if (downloaded - lastReported >= 4L * 1024 * 1024) {
                    lastReported = downloaded
                    onProgress(DownloadProgress(downloaded, total))
                }
            }
            sink.flush()
        }

        if (total > 0 && downloaded != total) {
            error("the download ended early: $downloaded of $total bytes")
        }
        onProgress(DownloadProgress(downloaded, total))
    }

    SystemFileSystem.atomicMove(partial, target)
}
