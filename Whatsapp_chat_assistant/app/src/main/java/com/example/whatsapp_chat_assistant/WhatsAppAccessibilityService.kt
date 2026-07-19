package com.example.whatsapp_chat_assistant

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Rect

class WhatsAppAccessibilityService : AccessibilityService() {

    private var inferenceService: LlamaInferenceService? = null
    private var isBound = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastProcessedMessage: String = ""

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LlamaInferenceService.LocalBinder
            inferenceService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            inferenceService = null
            isBound = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Bind to your running local LLM service
        val intent = Intent(this, LlamaInferenceService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != "com.whatsapp") return

        val rootNode = rootInActiveWindow ?: return

        // Find the last visible text message bubble in the current WhatsApp window
        val lastMessageNode = findLastMessageNode(rootNode)
        val currentMessageText = lastMessageNode?.text?.toString()?.trim()

        if (!currentMessageText.isNullOrEmpty() && currentMessageText != lastProcessedMessage) {
            lastProcessedMessage = currentMessageText

            // Execute local model inference off the main UI thread
            if (isBound && inferenceService != null) {
                serviceScope.launch {
                    val prompt = """
                        <|im_start|>system
                        You are a casual WhatsApp user.<|im_end|>
                        <|im_start|>user
                        $currentMessageText<|im_end|>
                        <|im_start|>assistant
    
                    """.trimIndent()

                    // Call the safe suspension function you built in Phase 2
                    val aiResponse = withContext(Dispatchers.IO) {
                        inferenceService?.generateResponseSafely(prompt) ?: ""
                    }

                    if (aiResponse.isNotEmpty() && !aiResponse.contains("Error:")) {
                        copyToClipboard(aiResponse)
                    }
                }
            }
        }
        rootNode.recycle()
    }


    private fun findLastMessageNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val textNodes = mutableListOf<AccessibilityNodeInfo>()
        findTextNodesRecursive(rootNode, textNodes)

        val screenWidth = resources.displayMetrics.widthPixels
        return textNodes.lastOrNull { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val isIncomingMessage = rect.left < (screenWidth / 2)

            node.className == "android.widget.TextView" &&
                    !node.isEditable &&
                    isIncomingMessage
        }
    }

    private fun findTextNodesRecursive(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return

        if (!node.text.isNullOrEmpty() && node.className == "android.widget.TextView") {
            result.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            findTextNodesRecursive(node.getChild(i), result)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Suggestion", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onInterrupt() {
        // pass
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}