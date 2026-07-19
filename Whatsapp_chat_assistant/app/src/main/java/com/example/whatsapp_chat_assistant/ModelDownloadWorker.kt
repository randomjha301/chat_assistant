package com.example.whatsapp_chat_assistant

import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 1. Enforce Foreground Service to prevent OS from killing the 2GB download
        setForeground(createForegroundInfo())

        val modelUrl = "https://your-server.com/qwen3b-q4_k_m.gguf"
        val expectedHash = "d415787f61aa2fa68037f8dd03c7c67f4f13740f9cf30ad0bf357defa8f3c982"
        val outputFile = File(context.filesDir, "qwen3b-q4_k_m.gguf")

        // 2. Configure OkHttpClient with infinite timeouts for large files
        val client = OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        // 3. Handle Partial Content (Resumable Downloads)
        val downloadedBytes = if (outputFile.exists()) outputFile.length() else 0L
        val request = Request.Builder()
            .url(modelUrl)
            .header("Range", "bytes=$downloadedBytes-")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                // 200 OK means full file from start, 206 Partial Content means resumed
                if (!response.isSuccessful && response.code != 206) return Result.failure()

                val body = response.body ?: return Result.failure()
                val inputStream = body.byteStream()

                // If the server supports resuming (206), we append. Otherwise, we overwrite.
                val isPartial = response.code == 206
                val append = isPartial && downloadedBytes > 0
                val outputStream = FileOutputStream(outputFile, append)

                val buffer = ByteArray(8 * 1024) // 8KB chunks
                var bytesRead: Int

                // The Streaming Loop
                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }

                // Verify Integrity after the file is fully assembled
                if (verifyFileHash(outputFile, expectedHash)) {
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

    private fun verifyFileHash(file: File, expectedHash: String): Boolean {
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8 * 1024)

        FileInputStream(file).use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }
        return calculatedHash == expectedHash
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "model_download_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the NotificationChannel required for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Model Download",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Downloading AI Model")
            .setContentText("Downloading large model file ...")
            .setSmallIcon(R.drawable.ic_notification_circle)
            .setOngoing(true)
            .build()

        val notificationId = 1001
        return ForegroundInfo(notificationId, notification)
    }
}