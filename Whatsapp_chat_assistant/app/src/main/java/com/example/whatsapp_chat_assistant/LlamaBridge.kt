package com.example.whatsapp_chat_assistant

import android.util.Log

object LlamaBridge {
    init {
        try {
            Log.d("LlamaBridge", "Loading libraries...")
            System.loadLibrary("ggml-base")
            System.loadLibrary("ggml-cpu")
            System.loadLibrary("ggml")
            System.loadLibrary("llama")
            System.loadLibrary("llama-bridge")
            Log.d("LlamaBridge", "Libraries loaded successfully")
        } catch (e: Exception) {
            Log.e("LlamaBridge", "Error loading libraries: ${e.message}")
        }
    }


    external fun loadModel(modelPath: String): Long

    external fun generateResponse(
        modelPointer: Long,
        prompt: String,
        onTokenFired: (String) -> Unit
    )

    external fun freeModel(modelPointer: Long)
}