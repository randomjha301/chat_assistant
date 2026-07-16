#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "LlamaBridge", __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_whatsapp_1chat_1assistant_LlamaBridge_loadModel(
        JNIEnv *env, jobject thiz, jstring model_path_j) {

    const char *model_path = env->GetStringUTFChars(model_path_j, nullptr);

    llama_model_params model_params = llama_model_default_params();

    llama_model *model = llama_load_model_from_file(model_path, model_params);

    env->ReleaseStringUTFChars(model_path_j, model_path);
    return reinterpret_cast<jlong>(model);
}


std::vector<llama_token> tokenize(const llama_model *model, const std::string &text, bool add_special) {
    // 1. Get the vocabulary mapping from the loaded model
    const struct llama_vocab * vocab = llama_model_get_vocab(model);

    // 2. Estimate maximum possible tokens.
    // A string will never have more tokens than it has characters, plus 1 for the special token (like BOS).
    int max_tokens = text.length() + (add_special ? 1 : 0);
    std::vector<llama_token> result(max_tokens);

    // 3. Perform the tokenization
    // parse_special = true ensures things like <|im_start|> or <s> are treated as commands, not raw text.
    int n_tokens = llama_tokenize(
            vocab,
            text.c_str(),
            text.length(),
            result.data(),
            result.size(),
            add_special,
            true          // parse_special
    );

    // 4. Handle buffer resizing (safety fallback)
    // If the buffer is too small, llama_tokenize returns the *negative* of the required size.
    if (n_tokens < 0) {
        result.resize(-n_tokens);
        n_tokens = llama_tokenize(
                vocab,
                text.c_str(),
                text.length(),
                result.data(),
                result.size(),
                add_special,
                true
        );
    }

    // 5. Shrink the vector down to the exact number of actual tokens
    if (n_tokens >= 0) {
        result.resize(n_tokens);
    }

    return result;
}




extern "C" JNIEXPORT void JNICALL
Java_com_example_whatsapp_1chat_1assistant_LlamaBridge_generateResponse(
        JNIEnv *env, jobject thiz, jlong model_ptr, jstring prompt_j, jobject callback) {

    llama_model *model = reinterpret_cast<llama_model *>(model_ptr);
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;

    llama_context *ctx = llama_new_context_with_model(model, ctx_params);

    const char *prompt_c = env->GetStringUTFChars(prompt_j, nullptr);
    std::string prompt(prompt_c);
    env->ReleaseStringUTFChars(prompt_j, prompt_c);


    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");

    // --- 1. Setup the Sampler ---
    // The sampler determines how we pick the next token from the model's probabilities.
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.8f)); // Temperature
    llama_sampler_chain_add(sampler, llama_sampler_init_min_p(0.05f, 1)); // Min-P sampling

    // --- 2. Tokenize the Prompt ---
    // You must convert the user's std::string prompt into a vector of integer Token IDs.
    // (Assuming you have a helper function `tokenize` that wraps llama_tokenize)
    std::vector<llama_token> prompt_tokens = tokenize(model, prompt, true);

    // --- 3. The Prefill Phase (Batching) ---
    // A batch holds the tokens we want the engine to evaluate.
    llama_batch batch = llama_batch_init(512, 0, 1);

    // Feed the entire prompt into the batch at once for parallel processing
    for (size_t i = 0; i < prompt_tokens.size(); i++) {
        // We only need the engine to calculate probabilities (logits) for the very last token
        bool request_logits = (i == prompt_tokens.size() - 1);
        llama_batch_add(batch, prompt_tokens[i], i, {0}, request_logits);
    }

    // Evaluate the initial prompt
    if (llama_decode(ctx, batch) != 0) {
        LOGI("llama_decode failed during prefill");
        llama_batch_free(batch);
        llama_sampler_free(sampler);
        return;
    }

    int n_cur = prompt_tokens.size(); // Track our position in the context window
    int n_predict = 512; // Maximum tokens to generate

    // --- 4. Token Generation & Buffering Logic ---
    std::string token_buffer = "";
    int flush_threshold = 4; // Only cross the JNI bridge every 4 tokens
    int tokens_buffered = 0;

    for (int i = 0; i < n_predict; i++) {
        // A. Sample the next token ID based on the last llama_decode
        llama_token new_token_id = llama_sampler_sample(sampler, ctx, -1);

        // B. Check if the model has decided the response is finished (End of Stream)
        const struct llama_vocab * vocab = llama_model_get_vocab(model);
        if (llama_vocab_is_eog(vocab, new_token_id)) {
        break;
    }

    // C. Convert the Token ID back into readable text
    char piece[32];
    llama_token_to_piece(vocab, new_token_id, piece, sizeof(piece), 0, true);

    // --- JNI Boundary ---
    token_buffer += piece;
    tokens_buffered++;

    if (tokens_buffered >= flush_threshold) {
        jstring j_token = env->NewStringUTF(token_buffer.c_str());
        env->CallObjectMethod(callback, invokeMethod, j_token);
        env->DeleteLocalRef(j_token); // Prevent JVM memory leaks

        token_buffer.clear();
        tokens_buffered = 0;
    }
    // --------------------

    // D. Prepare the engine for the next cycle
    llama_batch_clear(batch);

    // We only feed the newly generated token back into the engine, not the whole prompt
    llama_batch_add(batch, new_token_id, n_cur, {0}, true);

    // E. Evaluate the new token
    if (llama_decode(ctx, batch) != 0) {
        LOGI("llama_decode failed during generation");
        break;
    }

    n_cur++;
    }

    // Flush any remaining tokens at the end
    if (!token_buffer.empty()) {
        jstring j_token = env->NewStringUTF(token_buffer.c_str());
        env->CallObjectMethod(callback, invokeMethod, j_token);
        env->DeleteLocalRef(j_token);
    }

    // --- 5. Clean up C++ Memory ---
    llama_batch_free(batch);
    llama_sampler_free(sampler);
    llama_free(ctx);
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_whatsapp_1chat_1assistant_LlamaBridge_freeModel(
        JNIEnv *env, jobject thiz, jlong model_ptr) {
llama_free_model(reinterpret_cast<llama_model *>(model_ptr));
}