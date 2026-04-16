#include "LLMInference.h"
#include <android/log.h>
#include <cstring>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <cmath>
#include <algorithm>

#define TAG "[offlineLLM-Cpp]"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

void
LLMInference::loadModel(const char *model_path, float minP, float temperature, float topP, int topK,
                        float repeatPenalty, bool storeChats, long contextSize,
                        const char *chatTemplate, int nThreads, bool useMmap, bool useMlock) {
    LOGi("loading model with"
         "\n\tmodel_path = %s"
         "\n\tminP = %f"
         "\n\ttemperature = %f"
         "\n\ttopP = %f"
         "\n\ttopK = %d"
         "\n\trepeatPenalty = %f"
         "\n\tstoreChats = %d"
         "\n\tcontextSize = %li"
         "\n\tnThreads = %d"
         "\n\tuseMmap = %d"
         "\n\tuseMlock = %d",
         model_path, minP, temperature, topP, topK, repeatPenalty, storeChats, contextSize,
         nThreads, useMmap, useMlock);

    ggml_backend_load_all();

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = useMmap;
    model_params.use_mlock = useMlock;
    _model = llama_model_load_from_file(model_path, model_params);
    if (!_model) {
        LOGe("failed to load model from %s", model_path);
        throw std::runtime_error("loadModel() failed");
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_batch = contextSize;
    ctx_params.n_threads = nThreads;
    ctx_params.no_perf = true;
    _ctx = llama_init_from_model(_model, ctx_params);
    if (!_ctx) {
        LOGe("llama_new_context_with_model() returned null");
        throw std::runtime_error("llama_new_context_with_model() returned null");
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    _sampler = llama_sampler_chain_init(sampler_params);

    if (repeatPenalty > 1.0f) {
        llama_sampler_chain_add(_sampler, llama_sampler_init_penalties(256, repeatPenalty, 0.0f, 0.0f));
    }

    if (topK > 0) {
        llama_sampler_chain_add(_sampler, llama_sampler_init_top_k(topK));
    }

    if (topP < 1.0f) {
        llama_sampler_chain_add(_sampler, llama_sampler_init_top_p(topP, 1));
    }

    if (minP > 0.0f) {
        llama_sampler_chain_add(_sampler, llama_sampler_init_min_p(minP, 1));
    }

    llama_sampler_chain_add(_sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    _messages.clear();

    if (chatTemplate == nullptr || strlen(chatTemplate) == 0) {
        _chatTemplate = llama_model_chat_template(_model, nullptr);
    } else {
        _chatTemplate = strdup(chatTemplate);
    }
    
    if (_chatTemplate != nullptr) {
        std::string tmpl(_chatTemplate);
        if (tmpl.find("gemma") != std::string::npos || 
            tmpl.find("<start_of_turn>") != std::string::npos ||
            tmpl.find("<turn|") != std::string::npos) {
            _assistantRole = "model";
        } else {
            _assistantRole = "assistant";
        }
    }

    this->_storeChats = storeChats;
}

void
LLMInference::addChatMessage(const char *message, const char *role) {
    const char* actualRole = role;
    if (strcmp(role, "assistant") == 0) {
        actualRole = _assistantRole.c_str();
    }
    _messages.push_back({strdup(actualRole), strdup(message)});
}

float
LLMInference::getResponseGenerationTime() const {
    if (_responseGenerationTime == 0) return 0.0f;
    return (float) _responseNumTokens / (_responseGenerationTime / 1e6);
}

int
LLMInference::getContextSizeUsed() const {
    return _nCtxUsed;
}

void
LLMInference::startCompletion(const char *query) {
    if (!_storeChats) {
        _formattedMessages.clear();
        _formattedMessages = std::vector<char>(llama_n_ctx(_ctx));
    }
    _responseGenerationTime = 0;
    _responseNumTokens = 0;
    _response.clear();
    _cacheResponseTokens.clear();
    
    std::string queryString(query);
    if (queryString.find("<turn|") != std::string::npos || queryString.find("<start_of_turn>") != std::string::npos) {
         _promptTokens = common_tokenize(llama_model_get_vocab(_model), queryString, true, true);
    } else {
        addChatMessage(query, "user");

        int new_len = llama_chat_apply_template(
            _chatTemplate,
            _messages.data(),
            _messages.size(),
            true,
            _formattedMessages.data(),
            _formattedMessages.size()
        );
        if (new_len > (int)_formattedMessages.size()) {
            _formattedMessages.resize(new_len);
            new_len = llama_chat_apply_template(
                _chatTemplate,
                _messages.data(),
                _messages.size(),
                true,
                _formattedMessages.data(),
                _formattedMessages.size()
            );
        }
        
        if (new_len < 0) {
            LOGe("llama_chat_apply_template() failed, using fallback formatting");
            std::stringstream fallback;
            for (auto &msg : _messages) {
                fallback << msg.role << ": " << msg.content << "\n";
            }
            fallback << _assistantRole << ":";
            std::string prompt = fallback.str();
            _promptTokens = common_tokenize(llama_model_get_vocab(_model), prompt, true, true);
        } else {
            std::string prompt(_formattedMessages.begin(), _formattedMessages.begin() + new_len);
            _promptTokens = common_tokenize(llama_model_get_vocab(_model), prompt, true, true);
        }
    }

    _batch = new llama_batch();
    _batch->token = _promptTokens.data();
    _batch->n_tokens = _promptTokens.size();
}

bool
LLMInference::_isValidUtf8(const char *response) {
    if (!response) {
        return true;
    }
    const unsigned char *bytes = (const unsigned char *) response;
    int num;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

std::string
LLMInference::completionLoop() {
    uint32_t contextSize = llama_n_ctx(_ctx);
    _nCtxUsed = llama_memory_seq_pos_max(llama_get_memory(_ctx), 0) + 1;
    if (_nCtxUsed + _batch->n_tokens > contextSize) {
        throw std::runtime_error("context size reached");
    }

    auto start = ggml_time_us();
    if (llama_decode(_ctx, *_batch) < 0) {
        throw std::runtime_error("llama_decode() failed");
    }

    _currToken = llama_sampler_sample(_sampler, _ctx, -1);
    
    // Check if token is EOG or if the text so far contains stop markers
    bool is_eog = llama_vocab_is_eog(llama_model_get_vocab(_model), _currToken);
    
    std::string piece = common_token_to_piece(_ctx, _currToken, true);
    
    // Check for stop sequences in the cumulative response
    static const std::vector<std::string> stop_sequences = {
        "<turn|", "<|turn_end|>", "<turn_end|>", "<start_of_turn>", "<end_of_turn>", "###", "System instruction:"
    };

    std::string current_full = _response + _cacheResponseTokens + piece;
    for (const auto& stop : stop_sequences) {
        if (current_full.find(stop) != std::string::npos) {
            is_eog = true;
            break;
        }
    }

    if (is_eog) {
        addChatMessage(strdup(_response.data()), "assistant");
        _response.clear();
        return "[EOG]";
    }

    auto end = ggml_time_us();
    _responseGenerationTime += (end - start);
    _responseNumTokens += 1;
    _cacheResponseTokens += piece;

    _batch->token = &_currToken;
    _batch->n_tokens = 1;

    if (_isValidUtf8(_cacheResponseTokens.c_str())) {
        _response += _cacheResponseTokens;
        std::string valid_utf8_piece = _cacheResponseTokens;
        _cacheResponseTokens.clear();
        return valid_utf8_piece;
    }

    return "";
}

void
LLMInference::stopCompletion() {
    if (_storeChats && !_response.empty()) {
        addChatMessage(_response.c_str(), "assistant");
    }
    _response.clear();
    _cacheResponseTokens.clear();
}

LLMInference::~LLMInference() {
    for (llama_chat_message &message: _messages) {
        free(const_cast<char *>(message.role));
        free(const_cast<char *>(message.content));
    }
    if (_ctx) llama_free(_ctx);
    if (_model) llama_model_free(_model);
    delete _batch;
    if (_sampler) llama_sampler_free(_sampler);
}

std::string
LLMInference::benchModel(int pp, int tg, int pl, int nr) {
    g_batch     = llama_batch_init(pp, 0, pl);
    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;

    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        common_batch_clear(g_batch);
        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            common_batch_add(g_batch, 1, i, { 0 }, false);
        }
        g_batch.logits[g_batch.n_tokens - 1] = true;
        llama_memory_clear(llama_get_memory(this->_ctx), false);

        const auto t_pp_start = ggml_time_us();
        if (llama_decode(this->_ctx, g_batch) != 0) {
            LOGe("llama_decode() failed during prompt processing");
        }
        const auto t_pp_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(this->_ctx), false);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {
            common_batch_clear(g_batch);
            for (j = 0; j < pl; j++) {
                common_batch_add(g_batch, 0, i, { j }, true);
            }
            if (llama_decode(this->_ctx, g_batch) != 0) {
                LOGe("llama_decode() failed during text generation");
            }
        }
        const auto t_tg_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(this->_ctx), false);

        const auto t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const auto t_tg = double(t_tg_end - t_tg_start) / 1000000.0;
        const auto speed_pp = double(pp) / t_pp;
        const auto speed_tg = double(pl * tg) / t_tg;

        pp_avg += speed_pp;
        tg_avg += speed_tg;
        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;
    }

    llama_batch_free(g_batch);

    pp_avg /= double(nr);
    tg_avg /= double(nr);

    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    } else {
        pp_std = 0;
        tg_std = 0;
    }

    char model_desc[128];
    llama_model_desc(this->_model, model_desc, sizeof(model_desc));
    const auto model_size     = double(llama_model_size(this->_model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(this->_model)) / 1e9;

    std::stringstream result;
    result << std::setprecision(3);
    result << "Model: " << model_desc << " | " << model_size << " GiB | " << model_n_params << "B params\n";
    result << "PP " << pp << ": " << pp_avg << " +/- " << pp_std << " t/s\n";
    result << "TG " << tg << ": " << tg_avg << " +/- " << tg_std << " t/s\n";
    return result.str();
}
