package com.example.whatsapp_chat_assistant

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ModelDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val modelUrl = "https://your-server.com/qwen3b-q4_k_m.gguf"
        val expectedHash = "YOUR_EXPECTED_SHA256_HASH_HERE"

        // This is the absolute path where llama.cpp will mmap from later
        val outputFile = File(context.filesDir, "qwen3b-q4_k_m.gguf")

        val client = OkHttpClient()
        val request = Request.Builder().url(modelUrl).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.failure()

                val body = response.body ?: return Result.failure()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(outputFile)

                // Initialize hash calculator
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8 * 1024) // 8KB chunks
                var bytesRead: Int

                // The Streaming Loop
                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead) // Update hash on the fly
                        }
                    }
                }

                // Verify Integrity
                val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (calculatedHash == expectedHash) {
                    Result.success()
                } else {
                    outputFile.delete() // Delete corrupted file
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Result.retry() // Tells WorkManager to try again later if network drops
        }
    }
}