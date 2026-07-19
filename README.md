#WhatsApp Local AI Chat Assistant

An Android application that acts as a real-time WhatsApp chat assistant, powered entirely by an on-device Large Language Model (LLM). It intelligently reads incoming messages and generates contextual reply suggestions completely offline, copying them directly to your clipboard for quick pasting.

This project bridges Android's native Accessibility Services with the high-performance C++ `llama.cpp` inference engine to run a 3B parameter model directly on your smartphone.

---

##System Architecture

The application is built using a modular architecture separating background processing, UI, system observation, and C++ model inference.

### 1. **Model Download & Verification (`ModelDownloadWorker`)**
*   **Functionality:** A reliable, foreground `CoroutineWorker` responsible for downloading the heavy (~2GB) `hinglish-qwen-3b-unsloth-Q4_K_M.gguf` model from HuggingFace.
*   **Highlights:** Uses `OkHttp` with infinite timeouts, supports resumable downloads (Partial Content `206`), and performs a strict SHA-256 hash verification to prevent model corruption. It enforces unmetered network usage (Wi-Fi) to save cellular data.

### 2. **JNI Native Bridge (`LlamaBridge` & `llama-bridge.cpp`)**
*   **Functionality:** The core computational engine interface. It links Kotlin to the native C++ `llama.cpp` and `ggml` libraries.
*   **Highlights:** 
    *   Manages the lifecycle of the LLM (loading/freeing from memory).
    *   Tokenizes string prompts into integer ID vectors.
    *   Executes the C++ decoding and token generation loop.
    *   Applies advanced sampling parameters (Temperature, Min-P, Penalties) for high-quality text generation.
    *   Streams generated text back to Kotlin via a JNI callback boundary.

### 3. **Persistent Inference Engine (`LlamaInferenceService`)**
*   **Functionality:** A Foreground Service that keeps the loaded AI model hot in RAM.
*   **Highlights:** Binds the `LlamaBridge` so that the model doesn't suffer from "cold starts" every time a message is received. It exposes a thread-safe `generateResponseSafely` coroutine function utilizing `Mutex` locks to prevent parallel inference crashes.

### 4. **WhatsApp Observer (`WhatsAppAccessibilityService`)**
*   **Functionality:** An Android Accessibility Service that safely hooks into the WhatsApp UI without requiring root access.
*   **Highlights:** Scans the active window for the most recent incoming text bubble (identifying it via screen coordinates). When a new message arrives, it formulates a prompt, asks the `LlamaInferenceService` for a reply, and automatically copies the AI's output to the system clipboard.

### 5. **User Interface (`MainActivity`)**
*   **Functionality:** The control center for the user.
*   **Highlights:** Checks if the model exists and initiates the download workflow if it doesn't. Once ready, it offers a simple toggle switch to activate the assistant, guiding the user to the Android Accessibility settings if permissions are missing.

---

## Setup & Installation

### Prerequisites
*   **Android Studio** (Ladybug or later recommended)
*   **Android NDK & CMake** (installed via SDK Manager for building `llama.cpp`)
*   **Target Device:** Android 10+ (API 29+) with at least 6GB+ RAM (to comfortably hold the 2GB model in memory alongside the OS).

### Build Instructions
1.  **Clone the repository:**
    ```bash
    git clone https://github.com/yourusername/whatsapp-chat-assistant.git
    cd whatsapp-chat-assistant
    ```
2.  **Sync the project:** Open the project in Android Studio and let Gradle sync. Ensure the `jniLibs` directories containing pre-compiled `ggml` and `llama` `.so` files are correctly placed as referenced in the `CMakeLists.txt`.
3.  **Build C++:** Gradle will invoke CMake to compile `llama-bridge.cpp`.
4.  **Run:** Install the app on your physical Android device. (Emulators may struggle with LLM inference without heavy acceleration).

### User Setup (In-App)
1.  Launch the app. Grant the requested **Notification Permissions** so the download service can run in the foreground.
2.  Connect to Wi-Fi. The app will automatically begin downloading the Qwen-3B GGUF model (~2GB). You can track progress via the notification.
3.  Once the model is downloaded and verified, the **Enable WhatsApp Assistant** toggle will appear.
4.  Toggle it **ON**. You will be redirected to your device's Accessibility Settings. Find "WhatsApp Chat Assistant" and enable it.
5.  The AI Engine is now running in the background!

---

##How to Use

1.  Open **WhatsApp** and navigate to any chat.
2.  When a friend sends you a new message, the app silently detects it.
3.  The local AI generates a context-aware Hinglish reply in the background.
4.  Once generated, the text is automatically **copied to your clipboard**.
5.  Simply tap the text input box, hit **Paste**, and send!

---

##Tech Stack & Libraries
*   **Kotlin / Android SDK:** Services, WorkManager, Coroutines, AccessibilityService.
*   **llama.cpp & ggml:** C++ backend for CPU/GPU quantized tensor operations.
*   **OkHttp:** Robust networking for large file downloads.
*   **Model:** `Qwen 3B` (Quantized to `Q4_K_M` for mobile compatibility).
