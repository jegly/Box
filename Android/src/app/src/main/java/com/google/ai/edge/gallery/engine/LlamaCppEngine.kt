package com.google.ai.edge.gallery.engine

import com.jegly.offlineLLM.smollm.SmolLM
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.measureTime

/**
 * Box: llama.cpp inference engine wrapper.
 * Provides the same lifecycle (load → generate → unload) as LiteRT
 * but backed by llama.cpp via the smollm JNI module.
 */
class LlamaCppEngine {

    private var instance = SmolLM()
    private val stateLock = ReentrantLock()

    // Track load params for resetConversation
    var lastLoadParams: SmolLM.InferenceParams? = null
        private set
    var lastModelPath: String? = null
        private set
    var lastSystemPrompt: String = ""
        private set

    @Volatile
    private var generationJob: Job? = null

    @Volatile
    private var loadJob: Job? = null

    val isModelLoaded = AtomicBoolean(false)

    @Volatile
    var isGenerating = false
        private set

    data class GenerationResult(
        val response: String,
        val tokensPerSecond: Float,
        val durationSeconds: Int,
        val contextLengthUsed: Int,
    )

    fun loadModel(
        modelPath: String,
        params: SmolLM.InferenceParams = SmolLM.InferenceParams(),
        systemPrompt: String = "",
        conversationHistory: List<Pair<String, String>> = emptyList(),
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        // Track for resetConversation
        lastLoadParams = params
        lastModelPath = modelPath
        lastSystemPrompt = systemPrompt

        stateLock.withLock {
            loadJob?.cancel()

            loadJob = CoroutineScope(Dispatchers.Default).launch {
                try {
                    instance.load(modelPath, params)

                    if (systemPrompt.isNotBlank()) {
                        instance.addSystemPrompt(systemPrompt)
                    }

                    for ((role, content) in conversationHistory) {
                        instance.addChatMessage(role, content)
                    }

                    withContext(Dispatchers.Main) {
                        isModelLoaded.set(true)
                        onSuccess()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                }
            }
        }
    }

    fun unloadModel() {
        stateLock.withLock {
            generationJob?.cancel()
            loadJob?.cancel()
            isModelLoaded.set(false)
            isGenerating = false
            try {
                instance.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Box: Reset conversation while keeping the model loaded.
     * Closes the current llama.cpp context and re-opens with the same params.
     * This clears the KV cache and chat history without reloading weights from disk.
     */
    fun resetConversation(
        modelPath: String,
        params: SmolLM.InferenceParams = lastLoadParams ?: SmolLM.InferenceParams(),
        systemPrompt: String = "",
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {},
    ) {
        stateLock.withLock {
            generationJob?.cancel()
            isGenerating = false

            CoroutineScope(Dispatchers.Default).launch {
                try {
                    // Close and reopen — this clears the KV cache and message history
                    // but the OS keeps model pages in memory so reload is fast
                    instance.close()
                    instance = SmolLM()
                    instance.load(modelPath, params)

                    if (systemPrompt.isNotBlank()) {
                        instance.addSystemPrompt(systemPrompt)
                    }

                    isModelLoaded.set(true)
                    withContext(Dispatchers.Main) { onSuccess() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    isModelLoaded.set(false)
                    withContext(Dispatchers.Main) { onError(e) }
                }
            }
        }
    }

    fun generateResponse(
        query: String,
        onToken: (String) -> Unit,
        onComplete: (GenerationResult) -> Unit,
        onCancelled: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        stateLock.withLock {
            if (!isModelLoaded.get()) {
                onError(IllegalStateException("Model not loaded"))
                return
            }

            generationJob?.cancel()

            generationJob = CoroutineScope(Dispatchers.Default).launch {
                try {
                    isGenerating = true
                    var fullResponse = ""

                    val duration = measureTime {
                        instance.getResponseAsFlow(query).collect { piece ->
                            fullResponse += piece
                            val displayResponse = cleanModelOutput(fullResponse, isFinal = false)
                            if (displayResponse.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    onToken(displayResponse)
                                }
                            }
                        }
                    }

                    val finalResponse = cleanModelOutput(fullResponse, isFinal = true).ifBlank {
                        if (fullResponse.isNotBlank()) "(No visible content produced)" else "(Empty response)"
                    }

                    withContext(Dispatchers.Main) {
                        isGenerating = false
                        onComplete(
                            GenerationResult(
                                response = finalResponse,
                                tokensPerSecond = instance.getResponseGenerationSpeed(),
                                durationSeconds = duration.inWholeSeconds.toInt(),
                                contextLengthUsed = instance.getContextLengthUsed(),
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    isGenerating = false
                    withContext(Dispatchers.Main) { onCancelled() }
                } catch (e: Exception) {
                    isGenerating = false
                    withContext(Dispatchers.Main) { onError(e) }
                }
            }
        }
    }

    private fun cleanModelOutput(raw: String, isFinal: Boolean): String {
        var cleaned = raw
            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<think>.*", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<turn\\|.*?\\|>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<\\|turn_end\\|>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<start_of_turn>.*?\\n", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<end_of_turn>", RegexOption.IGNORE_CASE), "")
            .replace("System instruction:", "")

        if (isFinal) {
            cleaned = cleaned.replace(Regex("<.*$", RegexOption.IGNORE_CASE), "")
        }

        return cleaned.trim()
    }

    fun stopGeneration() {
        stateLock.withLock {
            generationJob?.cancel()
            isGenerating = false
        }
    }

    fun benchModel(pp: Int = 512, tg: Int = 128, pl: Int = 1, nr: Int = 3): String {
        return if (isModelLoaded.get()) {
            instance.benchModel(pp, tg, pl, nr)
        } else {
            "Model not loaded"
        }
    }

    fun isReady(): Boolean = isModelLoaded.get() && !isGenerating
}
