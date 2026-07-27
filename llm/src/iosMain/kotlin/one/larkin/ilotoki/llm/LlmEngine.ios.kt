package one.larkin.ilotoki.llm

// The engine handle is an opaque forward-declared struct, which cinterop maps
// into `cnames.structs` rather than the .def's own package.
import cnames.structs.ilotoki_llm
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import one.larkin.ilotoki.llm.cinterop.ILOTOKI_LLM_ERROR
import one.larkin.ilotoki.llm.cinterop.ILOTOKI_LLM_TOKEN
import one.larkin.ilotoki.llm.cinterop.ilotoki_llm_free
import one.larkin.ilotoki.llm.cinterop.ilotoki_llm_load
import one.larkin.ilotoki.llm.cinterop.ilotoki_llm_next
import one.larkin.ilotoki.llm.cinterop.ilotoki_llm_params
import one.larkin.ilotoki.llm.cinterop.ilotoki_llm_start
import one.larkin.ilotoki.llm.cinterop.ilotoki_llm_stop
import one.larkin.ilotoki.llm.cinterop.ilotoki_llm_tokens_per_second

private const val ERR_SIZE = 256uL

/**
 * Decoding is blocking and CPU/GPU-bound, so it never touches the main dispatcher.
 * Parallelism is pinned to one because a llama context must not be entered twice
 * concurrently; the per-engine mutex enforces the same invariant at the API level.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val inferenceDispatcher = Dispatchers.Default.limitedParallelism(1)

@OptIn(ExperimentalForeignApi::class)
private class IosLlmEngine(handle: CPointer<ilotoki_llm>) : LlmEngine {
    private var handle: CPointer<ilotoki_llm>? = handle
    private val mutex = Mutex()

    override val tokensPerSecond: Float
        get() = handle?.let { ilotoki_llm_tokens_per_second(it) } ?: 0f

    override fun generate(prompt: String): Flow<String> =
        flow {
            mutex.withLock {
                val engine = handle ?: error("the model has been unloaded")

                memScoped {
                    val err = allocArray<ByteVar>(ERR_SIZE.toInt())
                    if (ilotoki_llm_start(engine, prompt, err, ERR_SIZE) == ILOTOKI_LLM_ERROR) {
                        error(err.toKString().ifEmpty { "failed to start the completion" })
                    }
                }

                try {
                    while (true) {
                        val piece = nextPiece(engine) ?: break
                        // Empty pieces mean a multi-byte character is still incomplete.
                        if (piece.isNotEmpty()) emit(piece)
                    }
                } finally {
                    ilotoki_llm_stop(engine)
                }
            }
        }.flowOn(inferenceDispatcher)

    /** One decode step: the decoded text, or null once generation has finished. */
    private fun nextPiece(engine: CPointer<ilotoki_llm>): String? = memScoped {
        val err = allocArray<ByteVar>(ERR_SIZE.toInt())
        val out = alloc<CPointerVar<ByteVar>>()
        when (ilotoki_llm_next(engine, out.ptr, err, ERR_SIZE)) {
            ILOTOKI_LLM_TOKEN -> out.value?.toKString().orEmpty()
            ILOTOKI_LLM_ERROR -> error(err.toKString().ifEmpty { "generation failed" })
            else -> null
        }
    }

    override suspend fun close() {
        mutex.withLock {
            handle?.let {
                handle = null
                ilotoki_llm_free(it)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun loadLlmEngine(modelPath: String, params: LlmParams): LlmEngine =
    withContext(inferenceDispatcher) {
        val handle = memScoped {
            val err = allocArray<ByteVar>(ERR_SIZE.toInt())
            val native = alloc<ilotoki_llm_params>().apply {
                temperature = params.temperature
                min_p = params.minP
                context_size = params.contextSize.toUInt()
                n_threads = params.threads
                max_tokens = params.maxTokens
                use_mmap = params.useMmap
                use_mlock = params.useMlock
                use_gpu = params.useGpu
            }
            ilotoki_llm_load(modelPath, native.ptr, err, ERR_SIZE)
                ?: error(err.toKString().ifEmpty { "failed to load the model" })
        }
        IosLlmEngine(handle)
    }
