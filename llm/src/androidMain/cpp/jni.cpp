/*
 * JNI bridge between one.larkin.ilotoki.llm.NativeLlm and the shared C core.
 *
 * Errors from the core are surfaced as IllegalStateException so the Kotlin side
 * can report them without having to inspect return codes.
 */

#include "ilotoki_llm.h"

#include <jni.h>
#include <string>

namespace {

constexpr size_t kErrSize = 256;

void throw_illegal_state(JNIEnv* env, const char* message) {
    jclass clazz = env->FindClass("java/lang/IllegalStateException");
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
    }
}

ilotoki_llm* as_engine(jlong handle) {
    return reinterpret_cast<ilotoki_llm*>(handle);
}

} // namespace

extern "C" JNIEXPORT void JNICALL Java_one_larkin_ilotoki_llm_NativeLlm_init(JNIEnv* env, jobject /*thiz*/,
                                                                            jstring backendDir) {
    const char* dir = env->GetStringUTFChars(backendDir, nullptr);
    ilotoki_llm_init(dir);
    env->ReleaseStringUTFChars(backendDir, dir);
}

extern "C" JNIEXPORT jlong JNICALL Java_one_larkin_ilotoki_llm_NativeLlm_load(
    JNIEnv* env, jobject /*thiz*/, jstring modelPath, jfloat temperature, jfloat minP, jint contextSize,
    jint nThreads, jint maxTokens, jboolean useMmap, jboolean useMlock, jboolean useGpu) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) {
        throw_illegal_state(env, "could not read the model path");
        return 0;
    }

    ilotoki_llm_params params = ilotoki_llm_default_params();
    params.temperature        = temperature;
    params.min_p              = minP;
    params.context_size       = static_cast<uint32_t>(contextSize);
    params.n_threads          = nThreads;
    params.max_tokens         = maxTokens;
    params.use_mmap           = useMmap == JNI_TRUE;
    params.use_mlock          = useMlock == JNI_TRUE;
    params.use_gpu            = useGpu == JNI_TRUE;

    char         err[kErrSize] = {0};
    ilotoki_llm* llm           = ilotoki_llm_load(path, &params, err, sizeof(err));
    env->ReleaseStringUTFChars(modelPath, path);

    if (llm == nullptr) {
        throw_illegal_state(env, err[0] != '\0' ? err : "failed to load the model");
        return 0;
    }
    return reinterpret_cast<jlong>(llm);
}

extern "C" JNIEXPORT void JNICALL Java_one_larkin_ilotoki_llm_NativeLlm_free(JNIEnv* /*env*/, jobject /*thiz*/,
                                                                            jlong handle) {
    ilotoki_llm_free(as_engine(handle));
}

extern "C" JNIEXPORT void JNICALL Java_one_larkin_ilotoki_llm_NativeLlm_start(JNIEnv* env, jobject /*thiz*/,
                                                                             jlong handle, jstring prompt) {
    const char* text = env->GetStringUTFChars(prompt, nullptr);
    if (text == nullptr) {
        throw_illegal_state(env, "could not read the prompt");
        return;
    }
    char      err[kErrSize] = {0};
    const int rc            = ilotoki_llm_start(as_engine(handle), text, err, sizeof(err));
    env->ReleaseStringUTFChars(prompt, text);
    if (rc == ILOTOKI_LLM_ERROR) {
        throw_illegal_state(env, err[0] != '\0' ? err : "failed to start the completion");
    }
}

/** Returns the next piece of text, or null once generation has finished. */
extern "C" JNIEXPORT jstring JNICALL Java_one_larkin_ilotoki_llm_NativeLlm_next(JNIEnv* env, jobject /*thiz*/,
                                                                               jlong handle) {
    const char* piece           = nullptr;
    char        err[kErrSize]   = {0};
    const int   rc              = ilotoki_llm_next(as_engine(handle), &piece, err, sizeof(err));

    if (rc == ILOTOKI_LLM_ERROR) {
        throw_illegal_state(env, err[0] != '\0' ? err : "generation failed");
        return nullptr;
    }
    if (rc == ILOTOKI_LLM_EOG) {
        return nullptr;
    }
    return env->NewStringUTF(piece != nullptr ? piece : "");
}

extern "C" JNIEXPORT void JNICALL Java_one_larkin_ilotoki_llm_NativeLlm_stop(JNIEnv* /*env*/, jobject /*thiz*/,
                                                                            jlong handle) {
    ilotoki_llm_stop(as_engine(handle));
}

extern "C" JNIEXPORT jfloat JNICALL Java_one_larkin_ilotoki_llm_NativeLlm_tokensPerSecond(JNIEnv* /*env*/,
                                                                                          jobject /*thiz*/,
                                                                                          jlong handle) {
    return ilotoki_llm_tokens_per_second(as_engine(handle));
}

extern "C" JNIEXPORT jint JNICALL Java_one_larkin_ilotoki_llm_NativeLlm_contextUsed(JNIEnv* /*env*/, jobject /*thiz*/,
                                                                                    jlong handle) {
    return static_cast<jint>(ilotoki_llm_context_used(as_engine(handle)));
}

extern "C" JNIEXPORT jint JNICALL Java_one_larkin_ilotoki_llm_NativeLlm_contextSize(JNIEnv* /*env*/, jobject /*thiz*/,
                                                                                    jlong handle) {
    return static_cast<jint>(ilotoki_llm_context_size(as_engine(handle)));
}
