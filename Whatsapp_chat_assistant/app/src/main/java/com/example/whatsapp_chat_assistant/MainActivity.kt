package com.example.whatsapp_chat_assistant

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.*
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.Switch
import android.os.Build
import java.io.File
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import android.util.Log

class MainActivity : AppCompatActivity() {
    private val TAG = "AssistantMainActivity"
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, notifications will show
        } else {
            // Permission denied, worker will download silently (caught by your try-catch)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (true) {
            Log.d(TAG, "isModelReady check bypassed (true), proceeding to app")
            proceedToApp()
        } else {
            Log.d(TAG, "Starting model download...")
            startModelDownload(this)

            WorkManager.getInstance(this)
                .getWorkInfosForUniqueWorkLiveData("model_download")
                .observe(this) { workInfoList ->
                    val workInfo = workInfoList.firstOrNull() ?: return@observe

                    when (workInfo.state) {
                        WorkInfo.State.ENQUEUED -> {
                            val statusText = findViewById<TextView>(R.id.statusTextView)
                            statusText.text = "Waiting for Wi-Fi connection to download model..."
                        }
                        WorkInfo.State.RUNNING -> showLoadingScreen()
                        WorkInfo.State.SUCCEEDED -> proceedToApp()
                        WorkInfo.State.FAILED -> showError(getString(R.string.file_corrupted_error))
                        else -> {}
                    }
                }
        }
    }
    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedId = ComponentName(context, service).flattenToString()
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val isEnabled = enabledServices.split(':').any { it.equals(expectedId,ignoreCase=true) }
        Log.d(TAG, "isAccessibilityServiceEnabled: $isEnabled for $expectedId")
        return isEnabled
    }
    private fun isModelReady(): Boolean {
        val modelFile = File(filesDir, "hinglish-qwen-3b-unsloth-Q4_K_M.gguf")
        val exists = modelFile.exists()
        val length = if (exists) modelFile.length() else 0L
        Log.d(TAG, "isModelReady: exists=$exists, length=$length")
        return exists && length >= 1_825_361_100L
    }

    fun startModelDownload(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "model_download",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )
    }

    private fun showLoadingScreen() {
        val statusText = findViewById<TextView>(R.id.statusTextView)
        val progressBar = findViewById<ProgressBar>(R.id.loadingProgressBar)
        val assistantToggle = findViewById<Switch>(R.id.assistantToggle)

        assistantToggle.visibility = View.GONE

        statusText.setText(R.string.downloading_model)
        progressBar.visibility = View.VISIBLE
    }

    private fun proceedToApp() {
        Log.d(TAG, "proceedToApp called")
        val statusText = findViewById<TextView>(R.id.statusTextView)
        val progressBar = findViewById<ProgressBar>(R.id.loadingProgressBar)
        val assistantToggle = findViewById<Switch>(R.id.assistantToggle)

        statusText.setText(R.string.assistant_ready)
        progressBar.visibility = View.GONE


        assistantToggle.visibility = View.VISIBLE


        assistantToggle.setOnCheckedChangeListener { buttonView, isChecked ->
            Log.d(TAG, "Assistant toggle changed: $isChecked, isPressed: ${buttonView.isPressed}")
            val serviceIntent = Intent(this, LlamaInferenceService::class.java)

            if (buttonView.isPressed){
               if(isChecked){
                   val hasPermission=isAccessibilityServiceEnabled(this, WhatsAppAccessibilityService::class.java)
                   Log.d(TAG, "Has accessibility permission: $hasPermission")
                   if(!hasPermission){
                       Log.d(TAG, "Redirecting to accessibility settings")
                       assistantToggle.isChecked = false
                       val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                       startActivity(intent)
                   }
                   else{
                       Log.d(TAG, "Starting LlamaInferenceService")
                       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                           startForegroundService(serviceIntent)
                       } else {
                           startService(serviceIntent)
                       }
                   }
               }
               else{
                   Log.d(TAG, "Stopping LlamaInferenceService")
                   stopService(serviceIntent)
               }
            }

        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, getString(R.string.error_message, message), Toast.LENGTH_LONG).show()

        val statusText = findViewById<TextView>(R.id.statusTextView)
        val progressBar = findViewById<ProgressBar>(R.id.loadingProgressBar)
        val assistantToggle = findViewById<Switch>(R.id.assistantToggle)

        statusText.text = getString(R.string.error_message, message)
        progressBar.visibility = View.GONE
        assistantToggle.visibility = View.GONE
    }
}