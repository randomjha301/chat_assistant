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

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (isModelReady()) {
            proceedToApp()
        } else {
            startModelDownload(this)

            WorkManager.getInstance(this)
                .getWorkInfosForUniqueWorkLiveData("model_download")
                .observe(this) { workInfoList ->
                    val workInfo = workInfoList.firstOrNull() ?: return@observe

                    when (workInfo.state) {
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

        return enabledServices.split(':').any { it.equals(expectedId,ignoreCase=true) }
    }
    private fun isModelReady(): Boolean {
        val modelFile = File(filesDir, "qwen3b-q4_k_m.gguf")
        return modelFile.exists()
    }

    fun startModelDownload(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
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
        val statusText = findViewById<TextView>(R.id.statusTextView)
        val progressBar = findViewById<ProgressBar>(R.id.loadingProgressBar)
        val assistantToggle = findViewById<Switch>(R.id.assistantToggle)

        statusText.setText(R.string.assistant_ready)
        progressBar.visibility = View.GONE


        assistantToggle.visibility = View.VISIBLE


        assistantToggle.setOnCheckedChangeListener { buttonView, isChecked ->
            val serviceIntent = Intent(this, LlamaInferenceService::class.java)

            if (buttonView.isPressed){
               if(isChecked){
                   val hasPermission=isAccessibilityServiceEnabled(this, WhatsAppAccessibilityService::class.java)
                   if(!hasPermission){
                       assistantToggle.isChecked = false
                       val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                       startActivity(intent)
                   }
                   else{
                       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                           startForegroundService(serviceIntent)
                       } else {
                           startService(serviceIntent)
                       }
                   }
               }
               else{
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