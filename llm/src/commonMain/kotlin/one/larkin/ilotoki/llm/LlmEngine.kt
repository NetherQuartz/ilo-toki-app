package one.larkin.ilotoki.llm

import kotlinx.coroutines.flow.Flow

/**
 * Tuning for a loaded model. The defaults mirror `ilotoki_llm_default_params()`.
 *
 * [contextSize] is deliberately far below gemma-2's 8192-token training window:
 * the app translates one sentence at a time, and the KV cache for the full window
 * would cost more memory than the weights themselves.
 */
data class LlmParams(
    /**
     * 0 selects greedy decoding, which is what a translator wants: there is one
     * right answer per input, sampling only ever walks away from it, and the same
     * sentence translating differently on a second try is a bug to a user. It is
     * also what the base model's authors recommend.
     */
    val temperature: Float = 0.0f,
    /** Ignored while [temperature] is 0 — greedy decoding has nothing to filter. */
    val minP: Float = 0.1f,
    val contextSize: Int = 2048,
    /** 0 lets the native side size the pool from the available performance cores. */
    val threads: Int = 0,
    val maxTokens: Int = 512,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false,
    /** Metal on iOS. Ignored on Android, where only the CPU backend is compiled in. */
    val useGpu: Boolean = true,
)

/** A loaded GGUF model, ready to complete prompts. */
interface LlmEngine {
    /**
     * Streams the completion for [prompt], one piece of decoded text at a time.
     *
     * The flow is cold: generation starts when collection starts, and stops as soon
     * as the collector is cancelled (at the next token boundary — a single decode
     * step cannot be interrupted). Concurrent collections are serialized rather than
     * allowed to corrupt the shared context.
     */
    fun generate(prompt: String): Flow<String>

    /** Generation speed of the most recent completion, in tokens per second. */
    val tokensPerSecond: Float

    /**
     * Unloads the model and frees its memory. Suspends until any generation in
     * flight has finished, so the native handle is never freed from under it.
     */
    suspend fun close()
}

/** Loads a GGUF model from [modelPath]. Throws [IllegalStateException] if it cannot be loaded. */
expect suspend fun loadLlmEngine(modelPath: String, params: LlmParams = LlmParams()): LlmEngine
