package one.larkin.ilotoki.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Points ggml at the backend libraries shipped in the APK. Call once at startup,
 * before any model is loaded.
 *
 * The backends are dlopen'ed by directory scan, which is why the app packages its
 * native libraries the extracted way rather than reading them out of the APK.
 */
fun initLlmBackends(context: Context) {
    NativeLlm.init(context.applicationInfo.nativeLibraryDir)
}

/**
 * JNI entry points into the shared C core.
 *
 * ggml's CPU kernels ship as one shared object per CPU baseline; ggml probes them
 * at startup and keeps the best match for this device, so nothing here has to pick
 * a variant by hand.
 */
internal object NativeLlm {
    init {
        System.loadLibrary("ilotoki_llm")
    }

    /** Loads the dlopen-able ggml backends from the app's native library directory. */
    external fun init(backendDir: String)

    external fun load(
        modelPath: String,
        temperature: Float,
        minP: Float,
        contextSize: Int,
        nThreads: Int,
        maxTokens: Int,
        useMmap: Boolean,
        useMlock: Boolean,
        useGpu: Boolean,
    ): Long

    external fun free(handle: Long)

    external fun start(handle: Long, prompt: String)

    /** Next piece of decoded text, or null once generation has finished. */
    external fun next(handle: Long): String?

    external fun stop(handle: Long)

    external fun tokensPerSecond(handle: Long): Float

    external fun contextUsed(handle: Long): Int

    external fun contextSize(handle: Long): Int
}

private class AndroidLlmEngine(private var handle: Long) : LlmEngine {
    /** Serializes generation and unloading against the single native context. */
    private val mutex = Mutex()

    override val tokensPerSecond: Float
        get() = handle.let { if (it == 0L) 0f else NativeLlm.tokensPerSecond(it) }

    override fun generate(prompt: String): Flow<String> =
        flow {
            mutex.withLock {
                val handle = handle
                check(handle != 0L) { "the model has been unloaded" }

                NativeLlm.start(handle, prompt)
                try {
                    while (true) {
                        val piece = NativeLlm.next(handle) ?: break
                        // Empty pieces mean a multi-byte character is still incomplete.
                        if (piece.isNotEmpty()) emit(piece)
                    }
                } finally {
                    NativeLlm.stop(handle)
                }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun close() {
        mutex.withLock {
            val handle = handle
            if (handle != 0L) {
                this.handle = 0L
                NativeLlm.free(handle)
            }
        }
    }
}

actual suspend fun loadLlmEngine(modelPath: String, params: LlmParams): LlmEngine =
    withContext(Dispatchers.IO) {
        val handle = NativeLlm.load(
            modelPath = modelPath,
            temperature = params.temperature,
            minP = params.minP,
            contextSize = params.contextSize,
            nThreads = params.threads,
            maxTokens = params.maxTokens,
            useMmap = params.useMmap,
            useMlock = params.useMlock,
            // Android builds ship the CPU backend only; there is no GPU to offload to.
            useGpu = false,
        )
        AndroidLlmEngine(handle)
    }
