package com.example.whatsapp_chat_assistant

object LlamaBridge {
    init {

        System.loadLibrary("ggml-base")
        System.loadLibrary("ggml-cpu")
        System.loadLibrary("ggml")
        System.loadLibrary("llama")
        System.loadLibrary("llama-bridge")
    }


    external fun loadModel(modelPath: String): Long

    external fun generateResponse(
        modelPointer: Long,
        prompt: String,
        onTokenFired: (String) -> Unit
    )

    external fun freeModel(modelPointer: Long)
}