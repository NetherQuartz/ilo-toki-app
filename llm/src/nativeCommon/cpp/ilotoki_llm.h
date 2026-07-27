/*
 * Plain-C façade over llama.cpp, shared by the Android (JNI) and iOS (cinterop)
 * bindings of the :llm module.
 *
 * Deliberately narrow: the app runs one-shot translation prompts, so there is no
 * chat history, no templating and no server-style session state. Everything the
 * engine keeps between calls is the loaded model, the context and the KV cache,
 * which is cleared on every ilotoki_llm_start().
 *
 * No exception ever crosses this boundary — failures are reported through return
 * values plus an optional caller-owned error buffer.
 */

#ifndef ILOTOKI_LLM_H
#define ILOTOKI_LLM_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ilotoki_llm ilotoki_llm;

typedef struct ilotoki_llm_params {
    /** Sampling temperature. <= 0 selects greedy decoding. */
    float temperature;
    /** min-p sampling cutoff. <= 0 disables the min-p step. */
    float min_p;
    /** Context window in tokens. 0 takes the model's training context. */
    uint32_t context_size;
    /** Threads used for CPU decoding. <= 0 auto-detects the performance cores. */
    int32_t n_threads;
    /** Hard cap on tokens produced per completion, guarding against runaway loops. */
    int32_t max_tokens;
    bool use_mmap;
    bool use_mlock;
    /** Offload to the GPU where a backend is available (Metal on iOS). */
    bool use_gpu;
} ilotoki_llm_params;

ilotoki_llm_params ilotoki_llm_default_params(void);

/**
 * Prepares the ggml backends. Call once before ilotoki_llm_load().
 *
 * [backend_dir] is where the dlopen-able backend libraries live — on Android the
 * app's native library directory. Pass NULL when the backends are linked
 * statically, as they are on iOS.
 *
 * Calling it more than once is harmless; only the first call has an effect.
 */
void ilotoki_llm_init(const char* backend_dir);

/**
 * Loads a GGUF model. Returns NULL on failure and, when err_size > 0, writes a
 * nul-terminated description into err.
 */
ilotoki_llm* ilotoki_llm_load(const char* model_path, const ilotoki_llm_params* params, char* err, size_t err_size);

void ilotoki_llm_free(ilotoki_llm* llm);

/** Result codes for ilotoki_llm_start / ilotoki_llm_next. */
#define ILOTOKI_LLM_OK 0
#define ILOTOKI_LLM_TOKEN 1
#define ILOTOKI_LLM_EOG 0
#define ILOTOKI_LLM_ERROR (-1)

/**
 * Clears the KV cache and tokenizes prompt as the new completion.
 * Returns ILOTOKI_LLM_OK on success, ILOTOKI_LLM_ERROR on failure.
 */
int ilotoki_llm_start(ilotoki_llm* llm, const char* prompt, char* err, size_t err_size);

/**
 * Decodes one step.
 *
 * ILOTOKI_LLM_TOKEN: *piece points at the newly generated text, owned by llm and
 *                    valid until the next call on the same instance. It is empty
 *                    while a multi-byte UTF-8 sequence is still incomplete.
 * ILOTOKI_LLM_EOG:   generation finished (end-of-generation token or max_tokens).
 * ILOTOKI_LLM_ERROR: failed; err describes why.
 */
int ilotoki_llm_next(ilotoki_llm* llm, const char** piece, char* err, size_t err_size);

/** Ends the current completion and releases its KV cache. Safe to call at any time. */
void ilotoki_llm_stop(ilotoki_llm* llm);

/** Generation speed of the completion in progress (or the last one), in tokens/second. */
float ilotoki_llm_tokens_per_second(const ilotoki_llm* llm);

/** Tokens currently held in the context window. */
uint32_t ilotoki_llm_context_used(const ilotoki_llm* llm);

/** Size of the context window the model was loaded with. */
uint32_t ilotoki_llm_context_size(const ilotoki_llm* llm);

#ifdef __cplusplus
}
#endif

#endif // ILOTOKI_LLM_H
