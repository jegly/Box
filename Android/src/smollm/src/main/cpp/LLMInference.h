#ifndef LLMINFERENCE_H
#define LLMINFERENCE_H

#include "llama.h"
#include "common.h"
#include "ggml.h"
#include <string>
#include <vector>

class LLMInference {
public:
    void loadModel(const char *model_path, float minP, float temperature, float topP, int topK,
                   float repeatPenalty, bool storeChats, long contextSize, const char *chatTemplate,
                   int nThreads, bool useMmap, bool useMlock);
    void addChatMessage(const char *message, const char *role);
    float getResponseGenerationTime() const;
    int getContextSizeUsed() const;
    void startCompletion(const char *query);
    std::string completionLoop();
    void stopCompletion();
    std::string benchModel(int pp, int tg, int pl, int nr);
    ~LLMInference();

private:
    llama_model *_model = nullptr;
    llama_context *_ctx = nullptr;
    llama_sampler *_sampler = nullptr;
    llama_batch *_batch = nullptr;
    llama_token _currToken;

    std::vector<llama_chat_message> _messages;
    std::vector<char> _formattedMessages;
    std::vector<llama_token> _promptTokens;
    std::string _response;
    std::string _cacheResponseTokens;
    const char *_chatTemplate = nullptr;
    bool _storeChats = true;
    std::string _assistantRole = "assistant";

    int64_t _responseGenerationTime = 0;
    int _responseNumTokens = 0;
    int _nCtxUsed = 0;

    llama_batch g_batch;

    static bool _isValidUtf8(const char *response);
};

#endif // LLMINFERENCE_H
