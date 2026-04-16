package com.google.ai.edge.gallery.engine

/**
 * Box: Supported inference engines.
 * LiteRT = Google AI Edge (TFLite) — original Gallery engine.
 * LlamaCpp = llama.cpp via smollm JNI bridge — for GGUF models.
 */
enum class InferenceEngineType {
    LITE_RT,
    LLAMA_CPP;

    companion object {
        /**
         * Auto-detect engine from file extension.
         */
        fun fromModelPath(path: String): InferenceEngineType {
            return when {
                path.endsWith(".gguf", ignoreCase = true) -> LLAMA_CPP
                path.endsWith(".tflite", ignoreCase = true) -> LITE_RT
                path.endsWith(".bin", ignoreCase = true) -> LITE_RT
                path.endsWith(".task", ignoreCase = true) -> LITE_RT
                else -> LITE_RT // Default to LiteRT for backward compat
            }
        }
    }

    val displayName: String
        get() = when (this) {
            LITE_RT -> "LiteRT"
            LLAMA_CPP -> "llama.cpp"
        }
}
