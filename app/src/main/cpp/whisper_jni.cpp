#include <jni.h>
#include <string>
#include <algorithm>
#include <thread>

#include "whisper.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_clipmaster_ai_WhisperNative_initModel(
        JNIEnv *env,
        jobject thiz,
        jstring modelPath) {

    if (modelPath == nullptr) {
        return 0;
    }

    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    if (path == nullptr) {
        return 0;
    }

    whisper_context_params params =
            whisper_context_default_params();

    params.use_gpu = false;

    whisper_context *context =
            whisper_init_from_file_with_params(path, params);

    env->ReleaseStringUTFChars(modelPath, path);

    return reinterpret_cast<jlong>(context);
}


JNIEXPORT jstring JNICALL
Java_com_clipmaster_ai_WhisperNative_transcribe(
        JNIEnv *env,
        jobject thiz,
        jlong contextPointer,
        jfloatArray audioArray) {

    if (contextPointer == 0 || audioArray == nullptr) {
        return env->NewStringUTF("");
    }

    auto *context =
            reinterpret_cast<whisper_context *>(contextPointer);

    jsize length = env->GetArrayLength(audioArray);

    if (length <= 0) {
        return env->NewStringUTF("");
    }

    jfloat *audio =
            env->GetFloatArrayElements(audioArray, nullptr);

    if (audio == nullptr) {
        return env->NewStringUTF("");
    }

    whisper_full_params params =
            whisper_full_default_params(
                    WHISPER_SAMPLING_GREEDY
            );

    unsigned int hardwareThreads =
            std::thread::hardware_concurrency();

    params.n_threads = std::max(
            1,
            std::min(
                    4,
                    static_cast<int>(
                            hardwareThreads == 0 ? 2 : hardwareThreads
                    )
            )
    );

    params.language = "id";
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.suppress_blank = true;
    params.suppress_nst = true;
    params.temperature = 0.0f;

    int result = whisper_full(
            context,
            params,
            audio,
            static_cast<int>(length)
    );

    env->ReleaseFloatArrayElements(
            audioArray,
            audio,
            JNI_ABORT
    );

    if (result != 0) {
        return env->NewStringUTF("");
    }

    std::string transcript;

    int segmentCount =
            whisper_full_n_segments(context);

    for (int i = 0; i < segmentCount; i++) {
        const char *segment =
                whisper_full_get_segment_text(context, i);

        if (segment != nullptr) {
            transcript += segment;
            transcript += " ";
        }
    }

    return env->NewStringUTF(transcript.c_str());
}


JNIEXPORT void JNICALL
Java_com_clipmaster_ai_WhisperNative_freeModel(
        JNIEnv *env,
        jobject thiz,
        jlong contextPointer) {

    if (contextPointer == 0) {
        return;
    }

    auto *context =
            reinterpret_cast<whisper_context *>(contextPointer);

    whisper_free(context);
}

}
