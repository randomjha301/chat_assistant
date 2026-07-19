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
import android.util.Log

class WhatsAppAccessibilityService : AccessibilityService() {

    private val TAG = "WhatsAppAccService"
    private var inferenceService: LlamaInferenceService? = null
    private var isBound = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastProcessedMessage: String = ""
    private var isGenerating: Boolean = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected: LlamaInferenceService bound")
            val binder = service as LlamaInferenceService.LocalBinder
            inferenceService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected: LlamaInferenceService unbound")
            inferenceService = null
            isBound = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Binding to LlamaInferenceService")
        // Bind to your running local LLM service
        val intent = Intent(this, LlamaInferenceService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != "com.whatsapp") return

        val rootNode = rootInActiveWindow ?: run {
            Log.w(TAG, "onAccessibilityEvent: rootInActiveWindow is null")
            return
        }

        // Find the last visible text message bubble in the current WhatsApp window
        val lastMessageNode = findLastMessageNode(rootNode)
        val currentMessageText = lastMessageNode?.text?.toString()?.trim()

        if (!currentMessageText.isNullOrEmpty()) {
            // ADD isGenerating CHECK HERE
            if (currentMessageText != lastProcessedMessage && !isGenerating) {
                Log.d(TAG, "New message detected: \"$currentMessageText\"")
                lastProcessedMessage = currentMessageText

                if (isBound && inferenceService != null) {
                    Log.d(TAG, "Inference service bound, launching coroutine")
                    serviceScope.launch {

                        // LOCK THE GENERATION
                        isGenerating = true

                        try {
                            val prompt = """
                                <|im_start|>system
                                You are a casual WhatsApp user.<|im_end|>
                                <|im_start|>user
                                $currentMessageText<|im_end|>
                                <|im_start|>assistant
            
                            """.trimIndent()

                            Log.d(TAG, "Calling generateResponseSafely...")
                            val aiResponse = withContext(Dispatchers.IO) {
                                try {
                                    inferenceService?.generateResponseSafely(prompt) ?: ""
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error during inference: ${e.message}")
                                    ""
                                }
                            }

                            Log.d(TAG, "AI Response received: \"$aiResponse\"")
                            if (aiResponse.isNotEmpty() && !aiResponse.contains("Error:")) {
                                Log.d(TAG, "Copying response to clipboard")
                                copyToClipboard(aiResponse)
                            } else {
                                Log.w(TAG, "AI Response is empty or contains error")
                            }
                        } finally {
                            // UNLOCK THE GENERATION WHEN DONE
                            isGenerating = false
                        }
                    }
                } else {
                    Log.w(TAG, "Inference service NOT bound. isBound=$isBound, service=$inferenceService")
                }
            }
        }
        rootNode.recycle()
    }


    private fun findLastMessageNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val textNodes = mutableListOf<AccessibilityNodeInfo>()
        findTextNodesRecursive(rootNode, textNodes)

        val screenWidth = resources.displayMetrics.widthPixels
        val lastNode = textNodes.lastOrNull { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // NEW MATH: Check the center of the bubble, not just the left edge
            val bubbleCenterX = (rect.left + rect.right) / 2
            val isIncomingMessage = bubbleCenterX < (screenWidth / 2)

            node.className == "android.widget.TextView" &&
                    !node.isEditable &&
                    isIncomingMessage
        }
        Log.d(TAG, "findLastMessageNode: Found ${textNodes.size} text nodes, selected: ${lastNode?.text}")
        return lastNode
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