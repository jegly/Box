#include "gguf.h"
#include <jni.h>
#include <string>

extern "C" JNIEXPORT jlong JNICALL
Java_com_jegly_offlineLLM_smollm_GGUFReader_getGGUFContextNativeHandle(JNIEnv* env, jobject thiz, jstring modelPath) {
    jboolean         isCopy        = true;
    const char*      modelPathCStr = env->GetStringUTFChars(modelPath, &isCopy);
    gguf_init_params initParams    = { .no_alloc = true, .ctx = nullptr };
    gguf_context*    ggufContext   = gguf_init_from_file(modelPathCStr, initParams);
    env->ReleaseStringUTFChars(modelPath, modelPathCStr);
    return reinterpret_cast<jlong>(ggufContext);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_jegly_offlineLLM_smollm_GGUFReader_getContextSize(JNIEnv* env, jobject thiz, jlong nativeHandle) {
    gguf_context* ggufContext       = reinterpret_cast<gguf_context*>(nativeHandle);
    int64_t       architectureKeyId = gguf_find_key(ggufContext, "general.architecture");
    if (architectureKeyId == -1)
        return -1;
    std::string architecture       = gguf_get_val_str(ggufContext, architectureKeyId);
    std::string contextLengthKey   = architecture + ".context_length";
    int64_t     contextLengthKeyId = gguf_find_key(ggufContext, contextLengthKey.c_str());
    if (contextLengthKeyId == -1)
        return -1;
    uint32_t contextLength = gguf_get_val_u32(ggufContext, contextLengthKeyId);
    return contextLength;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jegly_offlineLLM_smollm_GGUFReader_getChatTemplate(JNIEnv* env, jobject thiz, jlong nativeHandle) {
    gguf_context* ggufContext       = reinterpret_cast<gguf_context*>(nativeHandle);
    int64_t       chatTemplateKeyId = gguf_find_key(ggufContext, "tokenizer.chat_template");
    std::string   chatTemplate;
    if (chatTemplateKeyId == -1) {
        chatTemplate = "";
    } else {
        chatTemplate = gguf_get_val_str(ggufContext, chatTemplateKeyId);
    }
    return env->NewStringUTF(chatTemplate.c_str());
}
