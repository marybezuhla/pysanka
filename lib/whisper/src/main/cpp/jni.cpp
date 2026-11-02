// Pysanka: the JNI side of dictation (see WhisperNative.kt). Loads a model from a file in Pysanka's
// own storage and turns 16 kHz mono audio into text. No logging of the text or the audio: whisper's
// (and ggml's) log output is switched off.
#include <jni.h>
#include <atomic>
#include <cstdlib>
#include <string>
#include "whisper.h"

namespace {

// Every run has its number; a stop cancels every run up to the number given (never undone, so a
// stop that comes before its run starts still counts)
std::atomic<long long> g_cancelled{0};

bool abort_requested(void * gen) {
    return g_cancelled.load() >= *static_cast<long long *>(gen);
}

void quiet(enum ggml_log_level, const char *, void *) {}

}  // namespace

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *, void *) {
    // On a fatal error ggml would fork the process to print a backtrace (and could hang there)
    setenv("GGML_NO_BACKTRACE", "1", 1);
    whisper_log_set(quiet, nullptr);
    return JNI_VERSION_1_6;
}

JNIEXPORT jlong JNICALL
Java_ua_pysanka_whisper_WhisperNative_init(JNIEnv * env, jobject, jstring path) {
    const char * p = env->GetStringUTFChars(path, nullptr);
    if (p == nullptr) return 0;
    whisper_context * ctx = nullptr;
    try {
        whisper_context_params params = whisper_context_default_params();
        params.use_gpu = false;
        params.flash_attn = true;
        ctx = whisper_init_from_file_with_params(p, params);
    } catch (...) {
        ctx = nullptr;
    }
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

// The text as UTF-8 bytes (decoded in Kotlin: NewStringUTF would garble emoji and broken bytes)
JNIEXPORT jbyteArray JNICALL
Java_ua_pysanka_whisper_WhisperNative_transcribe(
        JNIEnv * env, jobject, jlong handle, jfloatArray audio, jstring language, jstring prompt, jint threads, jlong gen) {
    auto * ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) return nullptr;
    const char * lang = env->GetStringUTFChars(language, nullptr);
    if (lang == nullptr) return nullptr;
    const char * hint = env->GetStringUTFChars(prompt, nullptr);
    if (hint == nullptr) {
        env->ReleaseStringUTFChars(language, lang);
        return nullptr;
    }
    jfloat * samples = env->GetFloatArrayElements(audio, nullptr);
    if (samples == nullptr) {
        env->ReleaseStringUTFChars(prompt, hint);
        env->ReleaseStringUTFChars(language, lang);
        return nullptr;
    }
    const jsize n = env->GetArrayLength(audio);
    long long run = gen;

    int rc = -1;
    std::string text;
    try {
        whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = threads;
        // A language Whisper does not know: it finds out by itself
        params.language = whisper_lang_id(lang) >= 0 ? lang : "auto";
        params.detect_language = false;
        params.translate = false;
        params.no_context = true;
        params.no_timestamps = true;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_special = false;
        params.print_timestamps = false;
        params.suppress_blank = true;
        params.suppress_nst = true;
        // Pysanka: a sample of well-punctuated text in the language, so commas follow the grammar
        // (an empty one: none)
        params.initial_prompt = hint[0] != '\0' ? hint : nullptr;
        params.abort_callback = abort_requested;
        params.abort_callback_user_data = &run;
        rc = whisper_full(ctx, params, samples, n);
        if (rc == 0 && !abort_requested(&run)) {
            const int segments = whisper_full_n_segments(ctx);
            for (int i = 0; i < segments; ++i) {
                const char * s = whisper_full_get_segment_text(ctx, i);
                if (s != nullptr) text += s;
            }
        } else {
            rc = -1;
        }
    } catch (...) {
        rc = -1;
    }
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(prompt, hint);
    env->ReleaseStringUTFChars(language, lang);
    if (rc != 0) return nullptr;

    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    if (out != nullptr) env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return out;
}

JNIEXPORT void JNICALL
Java_ua_pysanka_whisper_WhisperNative_nativeStop(JNIEnv *, jobject, jlong gen) {
    long long current = g_cancelled.load();
    while (current < gen && !g_cancelled.compare_exchange_weak(current, gen)) {}
}

JNIEXPORT void JNICALL
Java_ua_pysanka_whisper_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    auto * ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}

}  // extern "C"
