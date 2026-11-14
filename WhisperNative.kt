package com.clipmaster.ai

object WhisperNative {

    init {
        System.loadLibrary("whisper-jni")
    }

    external fun initModel(modelPath: String): Long

    external fun transcribe(
        context: Long,
        audio: FloatArray
    ): String

    // Menghasilkan subtitle SRT dengan timestamp per segmen Whisper.
    external fun transcribeSrt(
        context: Long,
        audio: FloatArray
    ): String

    external fun freeModel(context: Long)
}
