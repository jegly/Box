package com.google.ai.edge.gallery.engine

import com.jegly.offlineLLM.smollm.SmolLM
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.takeWhile
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
                    var lastDisplayed = ""

                    // Stop generation at any of these tokens — covers ChatML (Qwen/Mistral),
                    // Llama-3 instruct, and generic EOS. Without this the model talks to itself.
                    val stopSequences = listOf(
                        "<|im_end|>",    // ChatML — Qwen, Mistral, etc.
                        "<|eot_id|>",    // Llama-3
                        "<|endoftext|>", // GPT-style EOS
                        "<|im_start|>",  // Model starting a new turn (should have stopped already)
                    )
                    var shouldStop = false

                    val duration = measureTime {
                        instance.getResponseAsFlow(query)
                            .takeWhile { !shouldStop }
                            .collect { piece ->
                                fullResponse += piece

                                // Detect the earliest stop sequence and truncate
                                val stopIdx = stopSequences
                                    .mapNotNull { seq -> fullResponse.indexOf(seq).takeIf { it >= 0 } }
                                    .minOrNull()
                                if (stopIdx != null) {
                                    fullResponse = fullResponse.substring(0, stopIdx)
                                    shouldStop = true
                                    return@collect
                                }

                                // Send only the NEW portion (delta) so the ViewModel can append
                                // correctly. Sending the full string caused "hello hello hello..."
                                val displayResponse = cleanModelOutput(fullResponse, isFinal = false)
                                val delta = if (displayResponse.length > lastDisplayed.length) {
                                    displayResponse.substring(lastDisplayed.length)
                                } else ""
                                lastDisplayed = displayResponse

                                if (delta.isNotEmpty()) {
                                    withContext(Dispatchers.Main) { onToken(delta) }
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
            // ChatML tokens (Qwen, Mistral, etc.)
            .replace(Regex("<\\|im_start\\|>(system|user|assistant)\\n?", RegexOption.IGNORE_CASE), "")
            .replace("<|im_end|>", "")
            .replace("<|endoftext|>", "")
            .replace("<|eot_id|>", "")
            // Thinking blocks
            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<think>.*", RegexOption.DOT_MATCHES_ALL), "")
            // Gemma turn tokens
            .replace(Regex("<start_of_turn>.*?\\n", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<end_of_turn>", RegexOption.IGNORE_CASE), "")
            // Misc
            .replace(Regex("<turn\\|.*?\\|>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<\\|turn_end\\|>", RegexOption.IGNORE_CASE), "")
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
