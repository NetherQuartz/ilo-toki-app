/*
 * Implementation of the plain-C llama.cpp façade declared in ilotoki_llm.h.
 */

#include "ilotoki_llm.h"

#include "llama.h"

#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <new>
#include <string>
#include <thread>
#include <vector>

#if defined(__ANDROID__)
#include <android/log.h>
#define ILOTOKI_TAG "ilotoki-llm"
#define ILOTOKI_LOGI(...) __android_log_print(ANDROID_LOG_INFO, ILOTOKI_TAG, __VA_ARGS__)
#define ILOTOKI_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, ILOTOKI_TAG, __VA_ARGS__)
#else
#define ILOTOKI_LOGI(...)                                                                                              \
    do {                                                                                                               \
        fprintf(stderr, "[ilotoki-llm] " __VA_ARGS__);                                                                 \
        fputc('\n', stderr);                                                                                           \
    } while (0)
#define ILOTOKI_LOGE(...) ILOTOKI_LOGI(__VA_ARGS__)
#endif

#if defined(__APPLE__)
#include <sys/sysctl.h>
#endif

namespace {

constexpr uint32_t kDefaultContextSize = 2048;
constexpr int32_t  kDefaultMaxTokens   = 512;

void set_err(char* err, size_t err_size, const char* message) {
    if (err == nullptr || err_size == 0) {
        return;
    }
    std::snprintf(err, err_size, "%s", message);
}

/*
 * llama.cpp writes to its own log sink; without a callback it goes to stderr,
 * which is invisible on Android. Route warnings and errors to the platform log
 * and drop the (very chatty) info stream unless this is a debug build.
 */
void log_callback(ggml_log_level level, const char* text, void* /*user_data*/) {
    if (text == nullptr) {
        return;
    }
#ifdef NDEBUG
    if (level < GGML_LOG_LEVEL_WARN) {
        return;
    }
#endif
    // GGML_LOG_LEVEL_CONT continues the previous message and sorts above ERROR
    // numerically, so it has to be excluded explicitly.
    if (level == GGML_LOG_LEVEL_ERROR) {
        ILOTOKI_LOGE("%s", text);
    } else {
        ILOTOKI_LOGI("%s", text);
    }
}

std::once_flag g_init_flag;

void init_backend_once(const char* backend_dir) {
    std::call_once(g_init_flag, [backend_dir] {
        llama_log_set(log_callback, nullptr);
        // With GGML_BACKEND_DL the CPU kernels live in separate shared objects,
        // one per CPU baseline; ggml probes them and keeps the best match. Without
        // a directory the backends are linked in statically and self-register.
        if (backend_dir != nullptr && backend_dir[0] != '\0') {
            ggml_backend_load_all_from_path(backend_dir);
        } else {
            ggml_backend_load_all();
        }
        llama_backend_init();
    });
}

#if defined(__linux__)
/*
 * Number of cores worth decoding on, read from cpufreq.
 *
 * Android SoCs are big.LITTLE. Decoding is split evenly across the thread pool and
 * synchronised at every layer, so a thread on a little core holds all the others
 * back — but only the little cores are that much slower. Everything above the
 * slowest frequency group counts, which keeps mid clusters such as Tensor's
 * Cortex-A76 pair (2.25 GHz next to the X1's 2.8 GHz) in play; a percentage
 * threshold would have dropped them.
 */
int32_t performance_core_count() {
    long khz[64] = {0};
    int  found   = 0;
    long slowest = 0;
    long fastest = 0;

    for (int cpu = 0; cpu < 64; cpu++) {
        char path[128];
        std::snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", cpu);
        FILE* file = std::fopen(path, "r");
        if (file == nullptr) {
            continue;
        }
        long value = 0;
        if (std::fscanf(file, "%ld", &value) == 1 && value > 0) {
            khz[found++] = value;
            if (value > fastest) {
                fastest = value;
            }
            if (slowest == 0 || value < slowest) {
                slowest = value;
            }
        }
        std::fclose(file);
    }

    if (found == 0 || fastest == 0) {
        return 0;
    }
    if (slowest == fastest) {
        // Every core runs at the same clock — no little cluster to leave out.
        return found;
    }

    int32_t count = 0;
    for (int i = 0; i < found; i++) {
        if (khz[i] > slowest) {
            count++;
        }
    }
    return count;
}
#endif

/*
 * Threads for CPU decoding. Using every core is counter-productive on the
 * big.LITTLE layouts these apps run on, so prefer the performance cluster.
 */
int32_t default_thread_count() {
#if defined(__APPLE__)
    int32_t perf_cores = 0;
    size_t  size       = sizeof(perf_cores);
    if (sysctlbyname("hw.perflevel0.logicalcpu", &perf_cores, &size, nullptr, 0) == 0 && perf_cores > 0) {
        return perf_cores;
    }
#elif defined(__linux__)
    const int32_t big_cores = performance_core_count();
    if (big_cores > 0) {
        return big_cores > 8 ? 8 : big_cores;
    }
#endif
    // No cpufreq information (emulators, unusual kernels): assume half the cores
    // are the fast ones, which is the common 4+4 layout.
    const unsigned int cores = std::thread::hardware_concurrency();
    if (cores == 0) {
        return 4;
    }
    const int32_t half = static_cast<int32_t>(cores) / 2;
    return half < 2 ? 2 : (half > 8 ? 8 : half);
}

/*
 * Length of the longest prefix of `s` that is complete, well-formed UTF-8.
 * A token piece can end mid-sequence (Cyrillic and emoji routinely split across
 * two tokens), so the tail is carried over to the next step instead of being
 * emitted as mojibake. Malformed lead/continuation bytes are passed through
 * rather than buffered, otherwise a single bad byte would stall the stream.
 */
size_t utf8_complete_len(const char* s, size_t n) {
    size_t i = 0;
    while (i < n) {
        const unsigned char c = static_cast<unsigned char>(s[i]);
        size_t              len;
        if ((c & 0x80) == 0x00) {
            len = 1;
        } else if ((c & 0xE0) == 0xC0) {
            len = 2;
        } else if ((c & 0xF0) == 0xE0) {
            len = 3;
        } else if ((c & 0xF8) == 0xF0) {
            len = 4;
        } else {
            i += 1;
            continue;
        }
        if (i + len > n) {
            break;
        }
        bool ok = true;
        for (size_t k = 1; k < len; k++) {
            if ((static_cast<unsigned char>(s[i + k]) & 0xC0) != 0x80) {
                ok = false;
                break;
            }
        }
        if (!ok) {
            i += 1;
            continue;
        }
        i += len;
    }
    return i;
}

std::string token_to_piece(const llama_vocab* vocab, llama_token token) {
    char    buf[256];
    int32_t n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, /*special=*/false);
    if (n >= 0) {
        return std::string(buf, static_cast<size_t>(n));
    }
    // The piece did not fit; -n is the size required.
    std::string out(static_cast<size_t>(-n), '\0');
    n = llama_token_to_piece(vocab, token, out.data(), static_cast<int32_t>(out.size()), 0, /*special=*/false);
    if (n < 0) {
        return std::string();
    }
    out.resize(static_cast<size_t>(n));
    return out;
}

} // namespace

struct ilotoki_llm {
    llama_model*   model   = nullptr;
    llama_context* ctx     = nullptr;
    llama_sampler* sampler = nullptr;

    /* Backing storage for the batch currently queued for llama_decode(). Both
     * must outlive the batch, which is a non-owning view over them. */
    std::vector<llama_token> prompt_tokens;
    llama_token              curr_token = 0;

    llama_batch batch  = {};
    bool        active = false;

    /* Returned to the caller by ilotoki_llm_next(); must stay alive until the
     * following call, so it lives here rather than on the stack. */
    std::string piece;
    /* Bytes of an incomplete UTF-8 sequence carried to the next step. */
    std::string utf8_tail;

    int32_t generated  = 0;
    int32_t max_tokens = kDefaultMaxTokens;
    int64_t decode_us  = 0;
};

void ilotoki_llm_init(const char* backend_dir) {
    init_backend_once(backend_dir);
}

ilotoki_llm_params ilotoki_llm_default_params(void) {
    ilotoki_llm_params params = {};
    params.temperature        = 0.0f; // greedy; see LlmParams for why
    params.min_p              = 0.1f;
    params.context_size       = kDefaultContextSize;
    params.n_threads          = 0;
    params.max_tokens         = kDefaultMaxTokens;
    params.use_mmap           = true;
    params.use_mlock          = false;
    params.use_gpu            = true;
    return params;
}

ilotoki_llm* ilotoki_llm_load(const char* model_path, const ilotoki_llm_params* params, char* err, size_t err_size) {
    if (model_path == nullptr) {
        set_err(err, err_size, "model path is null");
        return nullptr;
    }

    const ilotoki_llm_params p = params != nullptr ? *params : ilotoki_llm_default_params();

    // No-op when the caller already ran ilotoki_llm_init() with a backend directory.
    init_backend_once(nullptr);

    ilotoki_llm* llm = new (std::nothrow) ilotoki_llm();
    if (llm == nullptr) {
        set_err(err, err_size, "out of memory");
        return nullptr;
    }

    try {
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers       = p.use_gpu ? 999 : 0;
        // llama.cpp folded the use_mmap/use_mlock booleans into one enum; mlock
        // implies mmap, so it wins when both are asked for.
        model_params.load_mode = p.use_mlock  ? LLAMA_LOAD_MODE_MLOCK
                                 : p.use_mmap ? LLAMA_LOAD_MODE_MMAP
                                              : LLAMA_LOAD_MODE_NONE;

        llm->model = llama_model_load_from_file(model_path, model_params);
        if (llm->model == nullptr) {
            set_err(err, err_size, "failed to load the model file");
            ilotoki_llm_free(llm);
            return nullptr;
        }

        /* A translation prompt is a sentence, not a conversation. Capping the
         * context at the requested size instead of the model's training window
         * (8192 for gemma-2) keeps the KV cache from dwarfing the weights. */
        uint32_t context_size = p.context_size != 0 ? p.context_size : kDefaultContextSize;
        const int32_t n_ctx_train = llama_model_n_ctx_train(llm->model);
        if (n_ctx_train > 0 && context_size > static_cast<uint32_t>(n_ctx_train)) {
            context_size = static_cast<uint32_t>(n_ctx_train);
        }

        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx                = context_size;
        ctx_params.n_batch              = context_size;
        ctx_params.n_threads            = p.n_threads > 0 ? p.n_threads : default_thread_count();
        ctx_params.n_threads_batch      = ctx_params.n_threads;
        ctx_params.no_perf              = true;

        llm->ctx = llama_init_from_model(llm->model, ctx_params);
        if (llm->ctx == nullptr) {
            set_err(err, err_size, "failed to create the llama context");
            ilotoki_llm_free(llm);
            return nullptr;
        }

        llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
        sampler_params.no_perf                    = true;
        llm->sampler                              = llama_sampler_chain_init(sampler_params);
        if (llm->sampler == nullptr) {
            set_err(err, err_size, "failed to create the sampler");
            ilotoki_llm_free(llm);
            return nullptr;
        }
        if (p.temperature <= 0.0f) {
            llama_sampler_chain_add(llm->sampler, llama_sampler_init_greedy());
        } else {
            if (p.min_p > 0.0f) {
                llama_sampler_chain_add(llm->sampler, llama_sampler_init_min_p(p.min_p, 1));
            }
            llama_sampler_chain_add(llm->sampler, llama_sampler_init_temp(p.temperature));
            llama_sampler_chain_add(llm->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        }

        llm->max_tokens = p.max_tokens > 0 ? p.max_tokens : kDefaultMaxTokens;

        ILOTOKI_LOGI("model loaded: ctx=%u threads=%d gpu=%d", context_size, ctx_params.n_threads, p.use_gpu ? 1 : 0);
        return llm;
    } catch (const std::exception& e) {
        set_err(err, err_size, e.what());
        ilotoki_llm_free(llm);
        return nullptr;
    } catch (...) {
        set_err(err, err_size, "unknown error while loading the model");
        ilotoki_llm_free(llm);
        return nullptr;
    }
}

void ilotoki_llm_free(ilotoki_llm* llm) {
    if (llm == nullptr) {
        return;
    }
    if (llm->sampler != nullptr) {
        llama_sampler_free(llm->sampler);
    }
    if (llm->ctx != nullptr) {
        llama_free(llm->ctx);
    }
    if (llm->model != nullptr) {
        llama_model_free(llm->model);
    }
    delete llm;
}

int ilotoki_llm_start(ilotoki_llm* llm, const char* prompt, char* err, size_t err_size) {
    if (llm == nullptr || prompt == nullptr) {
        set_err(err, err_size, "engine or prompt is null");
        return ILOTOKI_LLM_ERROR;
    }

    try {
        // Each completion starts from a clean slate: no carry-over from the last one.
        ilotoki_llm_stop(llm);

        const llama_vocab* vocab      = llama_model_get_vocab(llm->model);
        const int32_t      prompt_len = static_cast<int32_t>(std::strlen(prompt));

        int32_t n_tokens = -llama_tokenize(vocab, prompt, prompt_len, nullptr, 0, /*add_special=*/true,
                                           /*parse_special=*/true);
        if (n_tokens <= 0) {
            set_err(err, err_size, "the prompt tokenized to nothing");
            return ILOTOKI_LLM_ERROR;
        }

        llm->prompt_tokens.resize(static_cast<size_t>(n_tokens));
        n_tokens = llama_tokenize(vocab, prompt, prompt_len, llm->prompt_tokens.data(), n_tokens,
                                  /*add_special=*/true, /*parse_special=*/true);
        if (n_tokens < 0) {
            set_err(err, err_size, "failed to tokenize the prompt");
            return ILOTOKI_LLM_ERROR;
        }
        llm->prompt_tokens.resize(static_cast<size_t>(n_tokens));

        const uint32_t n_ctx = llama_n_ctx(llm->ctx);
        if (static_cast<uint32_t>(n_tokens) >= n_ctx) {
            set_err(err, err_size, "the text is too long for the model's context window");
            return ILOTOKI_LLM_ERROR;
        }

        llm->batch     = llama_batch_get_one(llm->prompt_tokens.data(), n_tokens);
        llm->generated = 0;
        llm->decode_us = 0;
        llm->active    = true;
        return ILOTOKI_LLM_OK;
    } catch (const std::exception& e) {
        set_err(err, err_size, e.what());
        return ILOTOKI_LLM_ERROR;
    } catch (...) {
        set_err(err, err_size, "unknown error while starting the completion");
        return ILOTOKI_LLM_ERROR;
    }
}

int ilotoki_llm_next(ilotoki_llm* llm, const char** piece, char* err, size_t err_size) {
    if (llm == nullptr) {
        set_err(err, err_size, "engine is null");
        return ILOTOKI_LLM_ERROR;
    }
    if (piece != nullptr) {
        *piece = "";
    }
    if (!llm->active) {
        return ILOTOKI_LLM_EOG;
    }
    if (llm->generated >= llm->max_tokens) {
        llm->active = false;
        return ILOTOKI_LLM_EOG;
    }

    try {
        const uint32_t n_ctx = llama_n_ctx(llm->ctx);
        const int32_t  used  = llama_memory_seq_pos_max(llama_get_memory(llm->ctx), 0) + 1;
        if (static_cast<uint32_t>(used) + static_cast<uint32_t>(llm->batch.n_tokens) > n_ctx) {
            set_err(err, err_size, "the model ran out of context");
            llm->active = false;
            return ILOTOKI_LLM_ERROR;
        }

        const int64_t start = ggml_time_us();
        if (llama_decode(llm->ctx, llm->batch) != 0) {
            set_err(err, err_size, "llama_decode() failed");
            llm->active = false;
            return ILOTOKI_LLM_ERROR;
        }

        const llama_vocab* vocab = llama_model_get_vocab(llm->model);
        llm->curr_token          = llama_sampler_sample(llm->sampler, llm->ctx, -1);

        if (llama_vocab_is_eog(vocab, llm->curr_token)) {
            llm->active = false;
            return ILOTOKI_LLM_EOG;
        }

        llm->decode_us += ggml_time_us() - start;
        llm->generated += 1;

        // The next step decodes just the token we sampled; the rest is in the KV cache.
        llm->batch = llama_batch_get_one(&llm->curr_token, 1);

        llm->utf8_tail += token_to_piece(vocab, llm->curr_token);
        const size_t complete = utf8_complete_len(llm->utf8_tail.data(), llm->utf8_tail.size());
        llm->piece.assign(llm->utf8_tail, 0, complete);
        llm->utf8_tail.erase(0, complete);

        if (piece != nullptr) {
            *piece = llm->piece.c_str();
        }
        return ILOTOKI_LLM_TOKEN;
    } catch (const std::exception& e) {
        set_err(err, err_size, e.what());
        llm->active = false;
        return ILOTOKI_LLM_ERROR;
    } catch (...) {
        set_err(err, err_size, "unknown error during generation");
        llm->active = false;
        return ILOTOKI_LLM_ERROR;
    }
}

void ilotoki_llm_stop(ilotoki_llm* llm) {
    if (llm == nullptr) {
        return;
    }
    llm->active = false;
    llm->batch  = {};
    llm->prompt_tokens.clear();
    llm->piece.clear();
    llm->utf8_tail.clear();
    if (llm->ctx != nullptr) {
        llama_memory_clear(llama_get_memory(llm->ctx), true);
    }
}

float ilotoki_llm_tokens_per_second(const ilotoki_llm* llm) {
    if (llm == nullptr || llm->decode_us <= 0) {
        return 0.0f;
    }
    return static_cast<float>(llm->generated) / (static_cast<float>(llm->decode_us) / 1e6f);
}

uint32_t ilotoki_llm_context_used(const ilotoki_llm* llm) {
    if (llm == nullptr || llm->ctx == nullptr) {
        return 0;
    }
    const int32_t used = llama_memory_seq_pos_max(llama_get_memory(llm->ctx), 0) + 1;
    return used > 0 ? static_cast<uint32_t>(used) : 0;
}

uint32_t ilotoki_llm_context_size(const ilotoki_llm* llm) {
    if (llm == nullptr || llm->ctx == nullptr) {
        return 0;
    }
    return llama_n_ctx(llm->ctx);
}
