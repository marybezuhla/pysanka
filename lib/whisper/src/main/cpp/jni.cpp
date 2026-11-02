// Pysanka: the JNI side of dictation (see WhisperNative.kt). Loads a model from a file in Pysanka's
// own storage and turns 16 kHz mono audio into text. No logging of the text or the audio: whisper's
// own log output is switched off.
#include <jni.h>
#include <atomic>
#include <string>
#include "whisper.h"

namespace {

std::atomic<bool> g_abort{false};

bool abort_requested(void *) { return g_abort.load(); }

void quiet(enum ggml_log_level, const char *, void *) {}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_ua_pysanka_whisper_WhisperNative_init(JNIEnv * env, jobject, jstring path) {
    whisper_log_set(quiet, nullptr);
    const char * p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    params.flash_attn = true;
    whisper_context * ctx = whisper_init_from_file_with_params(p, params);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

// The text as UTF-8 bytes (decoded in Kotlin: NewStringUTF would garble emoji and broken bytes)
JNIEXPORT jbyteArray JNICALL
Java_ua_pysanka_whisper_WhisperNative_transcribe(
        JNIEnv * env, jobject, jlong handle, jfloatArray audio, jstring language, jint threads) {
    auto * ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) return nullptr;
    g_abort.store(false);

    const char * lang = env->GetStringUTFChars(language, nullptr);
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
    params.abort_callback = abort_requested;
    params.abort_callback_user_data = nullptr;

    const jsize n = env->GetArrayLength(audio);
    jfloat * samples = env->GetFloatArrayElements(audio, nullptr);
    const int rc = whisper_full(ctx, params, samples, n);
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);
    if (rc != 0) return nullptr;

    std::string text;
    const int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; ++i) {
        const char * s = whisper_full_get_segment_text(ctx, i);
        if (s != nullptr) text += s;
    }
    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    if (out != nullptr) env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return out;
}

JNIEXPORT void JNICALL
Java_ua_pysanka_whisper_WhisperNative_abort(JNIEnv *, jobject) {
    g_abort.store(true);
}

JNIEXPORT void JNICALL
Java_ua_pysanka_whisper_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    auto * ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}

}  // extern "C"
