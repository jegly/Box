package com.google.ai.edge.gallery.runtime

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.google.ai.edge.gallery.data.DEFAULT_TEMPERATURE
import com.google.ai.edge.gallery.data.DEFAULT_TOPK
import com.google.ai.edge.gallery.data.DEFAULT_TOPP
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.engine.LlamaCppEngine
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ToolProvider
import com.jegly.offlineLLM.smollm.SmolLM
import kotlinx.coroutines.CoroutineScope

private const val TAG = "BoxLlamaCppModelHelper"

/**
 * Box: LlmModelHelper implementation backed by llama.cpp for GGUF models.
 * Routes through the smollm JNI bridge.
 */
object LlamaCppModelHelper : LlmModelHelper {

    // Indexed by model name
    private val engines: MutableMap<String, LlamaCppEngine> = mutableMapOf()
    private var appContext: Context? = null

    override fun initialize(
        context: Context,
        model: Model,
        supportImage: Boolean,
        supportAudio: Boolean,
        onDone: (String) -> Unit,
        systemInstruction: Contents?,
        tools: List<ToolProvider>,
        enableConversationConstrainedDecoding: Boolean,
        coroutineScope: CoroutineScope?,
    ) {
        val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
        val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
        val temperature = model.getFloatConfigValue(
            key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE
        )

        val modelPath = model.getPath(context = context)
        Log.d(TAG, "Initializing llama.cpp engine for: $modelPath")

        val engine = LlamaCppEngine()
        engines[model.name] = engine
        appContext = context.applicationContext

        // El tamaño del contexto es el "Max tokens" del modelo (cualquier número). Antes se usaba
        // el máximo del modelo, que en Gemma es enorme y gasta mucha RAM.
        val contextSize = model.getIntConfigValue(key = ConfigKeys.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
            .coerceAtLeast(256)
            .toLong()

        val params = SmolLM.InferenceParams(
            temperature = temperature,
            topP = topP,
            topK = topK,
            contextSize = contextSize,
            numThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(8),
        )

        engine.loadModel(
            modelPath = modelPath,
            params = params,
            onSuccess = {
                // Store a marker so the ViewModel knows the model is ready
                model.instance = engine
                onDone("")
            },
            onError = { e ->
                Log.e(TAG, "Failed to load GGUF model", e)
                onDone(e.message ?: "Failed to load GGUF model")
            }
        )
    }

    /**
     * Chat normal de Box: vuelve a los ajustes del propio modelo (los que usa Box sin Roleplay),
     * por si este mismo modelo cargado venía de un chat de personaje.
     */
    fun resetSampler(model: Model) {
        val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
        val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
        val temperature = model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
        engines[model.name]?.applySampler(
            com.google.ai.edge.gallery.data.characters.SamplerSettings(
                temperature = temperature,
                dynTempRange = 0f,
                topK = topK,
                topP = topP,
                minP = 0.1f,
                repeatPenalty = 1.1f,
                repeatLastN = 256,
                dryMultiplier = 0f,
            )
        )
    }

    /** Roleplay: vuelve a leer el modo de muestreo guardado y lo aplica al modelo cargado. */
    fun refreshSampler(model: Model) {
        val ctx = appContext ?: return
        engines[model.name]?.applySampler(
            com.google.ai.edge.gallery.data.characters.SamplerSettingsStore(ctx).current()
        )
    }

    /** Roleplay: ver LlamaCppEngine.seedConversation. */
    fun seedConversation(model: Model, systemPrompt: String, history: List<Pair<String, String>>) {
        engines[model.name]?.seedConversation(systemPrompt, history)
    }

    override fun resetConversation(
        model: Model,
        supportImage: Boolean,
        supportAudio: Boolean,
        systemInstruction: Contents?,
        tools: List<ToolProvider>,
        enableConversationConstrainedDecoding: Boolean,
    ) {
        val engine = engines[model.name] ?: return
        val modelPath = engine.lastModelPath ?: return

        Log.d(TAG, "Resetting conversation for ${model.name} (keeping model loaded)")

        engine.resetConversation(
            modelPath = modelPath,
            params = engine.lastLoadParams ?: SmolLM.InferenceParams(),
            systemPrompt = engine.lastSystemPrompt,
            onSuccess = {
                // Update model instance reference
                model.instance = engine
                Log.d(TAG, "Conversation reset complete for ${model.name}")
            },
            onError = { e ->
                Log.e(TAG, "Failed to reset conversation for ${model.name}", e)
            },
        )
    }

    override fun cleanUp(model: Model, onDone: () -> Unit) {
        val engine = engines.remove(model.name)
        engine?.unloadModel()
        model.instance = null
        onDone()
        Log.d(TAG, "Clean up done for ${model.name}")
    }

    override fun stopResponse(model: Model) {
        val engine = engines[model.name]
        engine?.stopGeneration()
    }

    override fun runInference(
        model: Model,
        input: String,
        resultListener: ResultListener,
        cleanUpListener: CleanUpListener,
        onError: (message: String) -> Unit,
        images: List<Bitmap>,
        audioClips: List<ByteArray>,
        coroutineScope: CoroutineScope?,
        extraContext: Map<String, String>?,
    ) {
        val engine = engines[model.name]
        if (engine == null || !engine.isModelLoaded.get()) {
            onError("llama.cpp engine not initialized for ${model.name}")
            return
        }

        // Note: llama.cpp text-only — images/audio not supported in this path
        if (images.isNotEmpty()) {
            Log.w(TAG, "Image input not supported with llama.cpp engine, ignoring ${images.size} images")
        }

        // Thinking: en LiteRT el flag viaja en extraContext; en llama.cpp hay que aplicarlo al
        // formatear el prompt (llama.cpp no recibe kwargs de plantilla en esta versión).
        val enableThinking = extraContext?.get("enable_thinking")?.toBoolean() == true
        engine.setThinking(enableThinking)
        Log.i(TAG, "GGUF thinking=${enableThinking} (model=${model.name})")

        // Gemma 4: separa el razonamiento en voz alta de la respuesta (ver ThinkingSplitter).
        val splitter =
            if (com.google.ai.edge.gallery.data.characters.ThinkingSplitter.appliesTo(model.name))
                com.google.ai.edge.gallery.data.characters.ThinkingSplitter()
            else null
        fun emit(out: com.google.ai.edge.gallery.data.characters.ThinkingSplitter.Out) {
            // Con thinking desactivado no se muestra el razonamiento aunque el modelo lo escriba:
            // se descarta y solo se entrega la respuesta.
            if (enableThinking) out.thought?.let { resultListener("", false, it) }
            if (out.answer.isNotEmpty()) resultListener(out.answer, false, null)
        }

        engine.generateResponse(
            query = input,
            onToken = { partialResponse ->
                if (splitter == null) resultListener(partialResponse, false, null)
                else emit(splitter.feed(partialResponse))
            },
            onComplete = { result ->
                splitter?.let { emit(it.finish()) }
                // Send the final delta (empty string) with done=true
                resultListener("", true, null)
            },
            onCancelled = {
                splitter?.let { emit(it.finish()) }
                resultListener("", true, null)
            },
            onError = { e ->
                Log.e(TAG, "Inference error", e)
                onError(e.message ?: "Inference error")
            }
        )
    }
}
