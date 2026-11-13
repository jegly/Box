/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.google.ai.edge.gallery.data.characters.ContextWindowPlanner
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.local.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.gallery.security.SecurityUtils
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageError
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageLoading
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageThinking
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageType
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageWarning
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.chat.ChatViewModel
import com.google.ai.edge.gallery.ui.common.chat.SwipeState
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ToolProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "AGLlmChatViewModel"

@OptIn(ExperimentalApi::class)
open class LlmChatViewModelBase() : ChatViewModel() {

  // Box: Chat persistence — will be injected by Hilt in concrete subclasses
  override lateinit var chatRepository: ChatRepository
  private var currentConversationId: String? = null
  private val conversationMutex = Mutex()

  // ---------------------------------------------------------------------------------------------
  // Roleplay: regenerar, swipes y editar mensajes.
  // ---------------------------------------------------------------------------------------------
  override val supportsMessageTools: Boolean = true

  private val _swipeState = MutableStateFlow(SwipeState())
  override val swipeState: StateFlow<SwipeState> = _swipeState.asStateFlow()

  // Para regenerar con los mismos ajustes del último envío.
  private var lastAllowThinking = false

  override fun refreshMessageTools() {
    val convId = currentConversationId ?: return
    val repo = chatRepository ?: return
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val info = repo.swipeInfo(convId)
        _swipeState.value = if (info == null) SwipeState() else SwipeState(info.first, info.second)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to read swipe info", e)
      }
    }
  }

  /** Índice del último mensaje de texto del usuario en pantalla, o -1. */
  private fun lastUserTextIndex(model: Model): Int =
    (uiState.value.messagesByModel[model.name] ?: emptyList()).indexOfLast {
      it.type == ChatMessageType.TEXT && it.side == ChatSide.USER
    }

  override fun regenerateLast(model: Model, onError: (String) -> Unit) {
    if (uiState.value.inProgress) return
    val messages = uiState.value.messagesByModel[model.name] ?: return
    val userIndex = lastUserTextIndex(model)
    if (userIndex < 0) return // Solo hay saludo: no hay nada que regenerar.
    val userMessage = messages[userIndex] as? ChatMessageText ?: return
    // Quita todo lo que vino después del último mensaje del usuario (respuesta, pensamiento, errores).
    while ((uiState.value.messagesByModel[model.name]?.size ?: 0) > userIndex + 1) {
      removeLastMessage(model = model)
    }
    // El motor debe volver a verse "desde el principio del turno": se invalida su estado.
    liteRtWindows.remove(model.name)
    generateResponse(
      model = model,
      input = userMessage.content,
      onError = onError,
      allowThinking = lastAllowThinking,
      persistInput = false,
      regenerate = true,
    )
  }

  override fun swipeLast(model: Model, delta: Int, onError: (String) -> Unit) {
    if (uiState.value.inProgress) return
    val state = _swipeState.value
    // Pasar de la última versión hacia la derecha genera una nueva.
    if (delta > 0 && state.index >= state.count - 1) {
      regenerateLast(model, onError)
      return
    }
    if (delta < 0 && state.index <= 0) return
    val convId = currentConversationId ?: return
    val repo = chatRepository ?: return
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val updated = conversationMutex.withLock { repo.moveSwipe(convId, delta) } ?: return@launch
        val messages = uiState.value.messagesByModel[model.name] ?: return@launch
        val index = messages.indexOfLast { it.type == ChatMessageType.TEXT && it.side != ChatSide.SYSTEM }
        val old = messages.getOrNull(index) as? ChatMessageText ?: return@launch
        if (old.side != ChatSide.AGENT) return@launch
        replaceMessage(
          model = model,
          index = index,
          message =
            ChatMessageText(
              content = updated.content,
              side = ChatSide.AGENT,
              latencyMs = updated.latencyMs.toFloat(),
              accelerator = old.accelerator,
              isMarkdown = old.isMarkdown,
              hideSenderLabel = old.hideSenderLabel,
            ),
        )
        liteRtWindows.remove(model.name)
        refreshMessageTools()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to swipe", e)
      }
    }
  }

  override fun editMessage(model: Model, message: ChatMessageText, newText: String) {
    val text = newText.trim()
    if (text.isEmpty() || text == message.content || uiState.value.inProgress) return
    val messages = uiState.value.messagesByModel[model.name] ?: return
    val index = messages.indexOf(message)
    if (index < 0) return
    // Posición entre los mensajes de usuario/asistente, para encontrar el mismo en la base de datos.
    val position =
      messages.subList(0, index).count { it.type == ChatMessageType.TEXT && it.side != ChatSide.SYSTEM }
    replaceMessage(
      model = model,
      index = index,
      message =
        ChatMessageText(
          content = text,
          side = message.side,
          latencyMs = message.latencyMs,
          accelerator = message.accelerator,
          isMarkdown = message.isMarkdown,
          hideSenderLabel = message.hideSenderLabel,
        ),
    )
    // Para GGUF el historial se rearma desde la pantalla en cada turno; LiteRT necesita invalidar.
    liteRtWindows.remove(model.name)
    val convId = currentConversationId ?: return
    val repo = chatRepository ?: return
    val role = if (message.side == ChatSide.USER) "user" else "assistant"
    viewModelScope.launch(Dispatchers.IO) {
      try {
        conversationMutex.withLock { repo.editMessageAt(convId, position, role, text) }
        refreshMessageTools()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to persist edited message", e)
      }
    }
  }

  // true si el chat abierto es de un personaje de Roleplay.
  private var isRoleplayChat = false

  // Lorebook del personaje (entradas activadas por palabras clave) y cuántas se permiten por mensaje.
  @Volatile private var loreEntries: List<com.google.ai.edge.gallery.data.characters.LoreEntry> = emptyList()
  private var loreMaxEntries = 3

  fun setRoleplayChat(value: Boolean, characterId: String = "", loreMaxEntries: Int = 3) {
    isRoleplayChat = value
    this.loreMaxEntries = loreMaxEntries
    loreEntries = emptyList()
    if (value && characterId.isNotBlank()) {
      viewModelScope.launch(Dispatchers.IO) {
        try {
          loreEntries =
            com.google.ai.edge.gallery.data.characters.LorebookMatcher.parse(
              chatRepository.getCharacterBookJson(characterId)
            )
        } catch (e: Exception) {
          Log.e(TAG, "Failed to load lorebook", e)
        }
      }
    }
  }

  private val _currentSystemPrompt = MutableStateFlow("")
  val currentSystemPrompt: StateFlow<String> = _currentSystemPrompt.asStateFlow()

  fun setCurrentSystemPrompt(prompt: String) {
    _currentSystemPrompt.value = prompt
  }

  fun updateSystemPrompt(prompt: String) {
    _currentSystemPrompt.value = prompt
    val convId = currentConversationId ?: return
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val conv = chatRepository.getConversationById(convId) ?: return@launch
        chatRepository.updateConversation(conv.copy(systemPrompt = prompt))
      } catch (e: Exception) {
        Log.e(TAG, "Failed to persist system prompt", e)
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Roleplay: prompt de sistema, historial y ventana deslizante de contexto.
  // Box guardaba el prompt de sistema pero no se lo pasaba al modelo; aquí se aplica antes de cada
  // respuesta. La ventana (como la de Layla) borra los mensajes más viejos cuando el contexto se
  // llena, para que el chat pueda seguir "infinito".
  // ---------------------------------------------------------------------------------------------

  /** Estado de la ventana de LiteRT para un modelo: qué ve el motor ahora mismo. */
  private class LiteRtWindowState(
    val instance: Any,
    val prompt: String,
    val dropped: Int,
    val previousSize: Int,
  )

  private val liteRtWindows = mutableMapOf<String, LiteRtWindowState>()

  /** Uso real del contexto (lo que el motor ve ahora), para el indicador de la pantalla. */
  fun contextUsage(model: Model): com.google.ai.edge.gallery.data.characters.ContextUsage {
    val texts =
      (uiState.value.messagesByModel[model.name] ?: emptyList())
        .filter { it.type == ChatMessageType.TEXT && it.side != ChatSide.SYSTEM }
        .filterIsInstance<ChatMessageText>()
    val keep = if (texts.firstOrNull()?.side == ChatSide.AGENT) 1 else 0
    val maxTokens =
      model.getIntConfigValue(key = ConfigKeys.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    return com.google.ai.edge.gallery.data.characters.ContextUsage.compute(
      promptText = _currentSystemPrompt.value,
      texts = texts.map { it.content },
      keep = keep,
      dropped = liteRtWindows[model.name]?.dropped ?: 0,
      maxTokens = maxTokens,
    )
  }

  /** Mensajes de texto anteriores al que se envía ahora (sin el último mensaje del usuario). */
  private fun previousTexts(model: Model): List<ChatMessageText> {
    val texts =
      (uiState.value.messagesByModel[model.name] ?: emptyList())
        .filter { it.type == ChatMessageType.TEXT && it.side != ChatSide.SYSTEM }
        .filterIsInstance<ChatMessageText>()
    return if (texts.lastOrNull()?.side == ChatSide.USER) texts.dropLast(1) else texts
  }

  private fun applySystemPromptIfNeeded(model: Model, input: String) {
    val helper = model.runtimeHelper
    val basePrompt = _currentSystemPrompt.value
    try {
      val previous = previousTexts(model)
      // Lorebook: las entradas cuyas palabras clave aparecen en las últimas charlas o en el
      // mensaje nuevo se añaden al prompt de este turno (solo en chats de personaje).
      val prompt =
        if (isRoleplayChat && loreEntries.isNotEmpty()) {
          com.google.ai.edge.gallery.data.characters.LorebookMatcher.applyToPrompt(
            prompt = basePrompt,
            entries = loreEntries,
            scanTexts = previous.takeLast(3).map { it.content } + input,
            maxEntries = loreMaxEntries,
          )
        } else basePrompt

      // GGUF (llama.cpp): el historial nativo se deja igual al de la pantalla; la ventana
      // deslizante y la caché de prefijo trabajan dentro del motor nativo.
      if (helper is com.google.ai.edge.gallery.runtime.LlamaCppModelHelper) {
        val history =
          previous.map { (if (it.side == ChatSide.USER) "user" else "assistant") to it.content }
        // El muestreo de Roleplay solo vale en chats de personaje; los normales usan el del modelo.
        if (isRoleplayChat) helper.refreshSampler(model) else helper.resetSampler(model)
        helper.seedConversation(model, prompt, history)
        return
      }

      // LiteRT-LM: la conversación vive dentro del motor y no se puede recortar, así que cuando
      // se llena se reinicia con el prompt del personaje + los mensajes más recientes.
      if (helper !is LlmChatModelHelper) return
      val instance = model.instance ?: return
      val maxTokens =
        model.getIntConfigValue(key = ConfigKeys.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)

      val previousState = liteRtWindows[model.name]
      // Está sincronizado si es el mismo motor y prompt y el motor vio justo el turno anterior.
      val inSync =
        previousState != null &&
          previousState.instance === instance &&
          previousState.prompt == prompt &&
          previous.size == previousState.previousSize + 2
      val alreadyDropped = if (inSync) previousState!!.dropped else 0

      // El saludo del personaje (primer mensaje del asistente) nunca se borra.
      val keep = if (previous.firstOrNull()?.side == ChatSide.AGENT) 1 else 0
      val visible = previous.take(keep) + previous.drop(keep + alreadyDropped)
      val extraDrop =
        ContextWindowPlanner.planDrop(
          systemTokens = ContextWindowPlanner.estimateTokens(prompt),
          history = visible.map { it.content },
          inputTokens = ContextWindowPlanner.estimateTokens(input),
          keep = keep,
          maxTokens = maxTokens,
        )
      val dropped = alreadyDropped + extraDrop
      val needsReset = extraDrop > 0 || (!inSync && (prompt.isNotBlank() || previous.isNotEmpty()))

      if (needsReset) {
        val window = previous.take(keep) + previous.drop(keep + dropped)
        helper.resetConversationWithHistory(
          model = model,
          supportImage = model.llmSupportImage,
          supportAudio = model.llmSupportAudio,
          systemInstruction = if (prompt.isBlank()) null else Contents.of(prompt),
          history = window.map { (if (it.side == ChatSide.USER) "user" else "assistant") to it.content },
        )
        if (extraDrop > 0) {
          Log.d(TAG, "Context window: dropped $extraDrop old messages (total dropped $dropped)")
        }
      }
      liteRtWindows[model.name] =
        LiteRtWindowState(
          instance = instance,
          prompt = prompt,
          dropped = dropped,
          previousSize = previous.size,
        )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to prepare the conversation", e)
    }
  }

  suspend fun getConversationById(conversationId: String) =
    chatRepository.getConversationById(conversationId)

  /**
   * Box: Set the current conversation ID for continuing an existing conversation
   */
  fun setCurrentConversationId(conversationId: String) {
    currentConversationId = conversationId
  }

  /**
   * Box: Look up the most recent conversation for a model (for auto-resume).
   */
  suspend fun getLatestConversationForModel(modelName: String): com.google.ai.edge.gallery.data.local.entities.Conversation? {
    return try {
      chatRepository.getLatestConversationForModel(modelName)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to get latest conversation for model", e)
      null
    }
  }

  /**
   * Box: Load conversation history for continuing a conversation
   */
  suspend fun loadConversationHistory(conversationId: String): List<com.google.ai.edge.gallery.data.local.entities.Message>? {
    val repo = chatRepository ?: return null
    return try {
      repo.getMessagesSync(conversationId)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load conversation history", e)
      null
    }
  }

  /**
   * Box: Persist a user message to the encrypted database.
   */
  private fun persistUserMessage(model: Model, content: String) {
    val repo = chatRepository ?: return
    Log.d(TAG, "Attempting to persist user message for model: ${model.name}")
    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Use Mutex to ensure thread-safe conversation creation
        conversationMutex.withLock {
          // Get or create conversation ID
          val convId = if (currentConversationId == null) {
            Log.d(TAG, "Creating new conversation for model: ${model.name}")
            val conv = repo.createConversation(
              title = content.take(50),
              taskType = "llm_chat",
              modelName = model.name,
              systemPrompt = _currentSystemPrompt.value,
            )
            currentConversationId = conv.id
            Log.d(TAG, "Created conversation with ID: ${conv.id}")
            conv.id
          } else {
            currentConversationId!!
          }
          
          // Now save the message with the guaranteed conversation ID
          try {
            repo.saveMessage(
              conversationId = convId,
              role = "user",
              content = SecurityUtils.sanitizePrompt(content),
            )
            Log.d(TAG, "Successfully persisted user message to conversation: $convId")
          } catch (fkException: android.database.sqlite.SQLiteConstraintException) {
            Log.w(TAG, "Foreign key constraint failed, conversation might not be committed yet. Retrying...", fkException)
            // Retry after a short delay to ensure conversation is committed
            kotlinx.coroutines.delay(100)
            repo.saveMessage(
              conversationId = convId,
              role = "user",
              content = SecurityUtils.sanitizePrompt(content),
            )
            Log.d(TAG, "Successfully persisted user message to conversation on retry: $convId")
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to persist user message", e)
      }
    }
  }

  /**
   * Box: Persist an assistant response to the encrypted database.
   */
  private fun persistAssistantMessage(
    model: Model,
    content: String,
    latencyMs: Long = 0,
    asSwipe: Boolean = false,
  ) {
    val repo = chatRepository ?: return
    Log.d(TAG, "Attempting to persist assistant message for model: ${model.name}")
    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Use Mutex to ensure thread-safe message saving
        conversationMutex.withLock {
          val convId = currentConversationId
          if (convId == null) {
            Log.w(TAG, "No conversation ID available for assistant message, skipping persistence")
            return@withLock
          }
          
          if (asSwipe) {
            // Regeneración: la respuesta nueva es otra versión de la última respuesta guardada.
            if (repo.addSwipe(convId, content, latencyMs) != null) {
              refreshMessageTools()
              return@withLock
            }
          }

          try {
            repo.saveMessage(
              conversationId = convId,
              role = "assistant",
              content = content,
              latencyMs = latencyMs,
            )
            refreshMessageTools()
            Log.d(TAG, "Successfully persisted assistant message to conversation: $convId")
          } catch (fkException: android.database.sqlite.SQLiteConstraintException) {
            Log.w(TAG, "Foreign key constraint failed for assistant message, conversation might not be committed yet. Retrying...", fkException)
            // Retry after a short delay to ensure conversation is committed
            kotlinx.coroutines.delay(100)
            repo.saveMessage(
              conversationId = convId,
              role = "assistant",
              content = content,
              latencyMs = latencyMs,
            )
            Log.d(TAG, "Successfully persisted assistant message to conversation on retry: $convId")
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to persist assistant message", e)
      }
    }
  }

  fun generateResponse(
    model: Model,
    input: String,
    images: List<Bitmap> = listOf(),
    audioMessages: List<ChatMessageAudioClip> = listOf(),
    onFirstToken: (Model) -> Unit = {},
    onDone: () -> Unit = {},
    onError: (String) -> Unit,
    allowThinking: Boolean = false,
    // Regenerar: el mensaje del usuario ya existe (no se guarda otra vez) y la respuesta es un swipe.
    persistInput: Boolean = true,
    regenerate: Boolean = false,
  ) {
    val accelerator = model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = "")
    lastAllowThinking = allowThinking
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(true)
      setPreparing(true)

      // Box: Persist user message to encrypted DB
      if (persistInput && input.isNotEmpty()) {
        persistUserMessage(model, input)
      }

      // Loading.
      addMessage(model = model, message = ChatMessageLoading(accelerator = accelerator))

      // Wait for instance to be initialized.
      while (model.instance == null) {
        delay(100)
      }
      delay(500)

      applySystemPromptIfNeeded(model, input)

      // Run inference.
      val audioClips: MutableList<ByteArray> = mutableListOf()
      for (audioMessage in audioMessages) {
        audioClips.add(audioMessage.genByteArrayForWav())
      }

      var firstRun = true
      val start = System.currentTimeMillis()

      try {
        val resultListener: (String, Boolean, String?) -> Unit =
          { partialResult, done, partialThinkingResult ->
            if (partialResult.startsWith("<ctrl")) {
              // Do nothing. Ignore control tokens.
            } else {
              // Remove the last message if it is a "loading" message.
              // This will only be done once.
              val lastMessage = getLastMessage(model = model)
              val wasLoading = lastMessage?.type == ChatMessageType.LOADING
              if (wasLoading) {
                removeLastMessage(model = model)
              }

              val thinkingText = partialThinkingResult
              val isThinking = thinkingText != null && thinkingText.isNotEmpty()
              var currentLastMessage = getLastMessage(model = model)

              // If thinking is enabled, add a thinking message.
              if (isThinking) {
                if (currentLastMessage?.type != ChatMessageType.THINKING) {
                  addMessage(
                    model = model,
                    message =
                      ChatMessageThinking(
                        content = "",
                        inProgress = true,
                        side = ChatSide.AGENT,
                        accelerator = accelerator,
                        hideSenderLabel =
                          currentLastMessage?.type == ChatMessageType.COLLAPSABLE_PROGRESS_PANEL,
                      ),
                  )
                }
                updateLastThinkingMessageContentIncrementally(
                  model = model,
                  partialContent = thinkingText!!,
                )
              } else {
                if (currentLastMessage?.type == ChatMessageType.THINKING) {
                  val thinkingMsg = currentLastMessage as ChatMessageThinking
                  if (thinkingMsg.inProgress) {
                    replaceLastMessage(
                      model = model,
                      message =
                        ChatMessageThinking(
                          content = thinkingMsg.content,
                          inProgress = false,
                          side = thinkingMsg.side,
                          accelerator = thinkingMsg.accelerator,
                          hideSenderLabel = thinkingMsg.hideSenderLabel,
                        ),
                      type = ChatMessageType.THINKING,
                    )
                  }
                }
                currentLastMessage = getLastMessage(model = model)
                if (
                  currentLastMessage?.type != ChatMessageType.TEXT ||
                    currentLastMessage.side != ChatSide.AGENT
                ) {
                  // Add an empty message that will receive streaming results.
                  addMessage(
                    model = model,
                    message =
                      ChatMessageText(
                        content = "",
                        side = ChatSide.AGENT,
                        accelerator = accelerator,
                        hideSenderLabel =
                          currentLastMessage?.type == ChatMessageType.COLLAPSABLE_PROGRESS_PANEL ||
                            currentLastMessage?.type == ChatMessageType.THINKING,
                      ),
                  )
                }

                // Incrementally update the streamed partial results.
                val latencyMs: Long = if (done) System.currentTimeMillis() - start else -1
                if (partialResult.isNotEmpty() || wasLoading || done) {
                  updateLastTextMessageContentIncrementally(
                    model = model,
                    partialContent = partialResult,
                    latencyMs = latencyMs.toFloat(),
                  )
                }
              }

              if (firstRun) {
                firstRun = false
                setPreparing(false)
                onFirstToken(model)
              }

              if (done) {
                val finalLastMessage = getLastMessage(model = model)
                if (finalLastMessage?.type == ChatMessageType.THINKING) {
                  val thinkingMsg = finalLastMessage as ChatMessageThinking
                  if (thinkingMsg.inProgress) {
                    replaceLastMessage(
                      model = model,
                      message =
                        ChatMessageThinking(
                          content = thinkingMsg.content,
                          inProgress = false,
                          side = thinkingMsg.side,
                          accelerator = thinkingMsg.accelerator,
                          hideSenderLabel = thinkingMsg.hideSenderLabel,
                        ),
                      type = ChatMessageType.THINKING,
                    )
                  }
                }
                setInProgress(false)
                onDone()

                // Box: Persist assistant response to encrypted DB
                val assistantMsg = getLastMessageWithTypeAndSide(model, ChatMessageType.TEXT, ChatSide.AGENT)
                if (assistantMsg is ChatMessageText && assistantMsg.content.isNotEmpty()) {
                  persistAssistantMessage(
                    model,
                    assistantMsg.content,
                    assistantMsg.latencyMs.toLong(),
                    asSwipe = regenerate,
                  )
                }
              }
            }
          }

        val cleanUpListener: () -> Unit = {
          setInProgress(false)
          setPreparing(false)
        }

        val errorListener: (String) -> Unit = { message ->
          Log.e(TAG, "Error occurred while running inference")
          setInProgress(false)
          setPreparing(false)
          onError(message)
        }

        val enableThinking =
          allowThinking &&
            model.getBooleanConfigValue(key = ConfigKeys.ENABLE_THINKING, defaultValue = false)
        val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

        model.runtimeHelper.runInference(
          model = model,
          input = input,
          images = images,
          audioClips = audioClips,
          resultListener = resultListener,
          cleanUpListener = cleanUpListener,
          onError = errorListener,
          coroutineScope = viewModelScope,
          extraContext = extraContext,
        )
      } catch (e: Exception) {
        Log.e(TAG, "Error occurred while running inference", e)
        setInProgress(false)
        setPreparing(false)
        onError(e.message ?: "")
      }
    }
  }

  fun stopResponse(model: Model) {
    Log.d(TAG, "Stopping response for model ${model.name}...")
    if (getLastMessage(model = model) is ChatMessageLoading) {
      removeLastMessage(model = model)
    }
    setInProgress(false)
    model.runtimeHelper.stopResponse(model)
    Log.d(TAG, "Done stopping response")
  }

  fun resetSession(
    task: Task,
    model: Model,
    systemInstruction: Contents? = null,
    tools: List<ToolProvider> = listOf(),
    supportImage: Boolean = false,
    supportAudio: Boolean = false,
    onDone: () -> Unit = {},
    enableConversationConstrainedDecoding: Boolean = false,
  ) {
    viewModelScope.launch(Dispatchers.Default) {
      setIsResettingSession(true)
      clearAllMessages(model = model)
      stopResponse(model = model)

      while (true) {
        try {
          model.runtimeHelper.resetConversation(
            model = model,
            supportImage = supportImage,
            supportAudio = supportAudio,
            systemInstruction = systemInstruction,
            tools = tools,
            enableConversationConstrainedDecoding = enableConversationConstrainedDecoding,
          )
          break
        } catch (e: Exception) {
          Log.d(TAG, "Failed to reset session. Trying again")
        }
        delay(200)
      }
      setIsResettingSession(false)
      onDone()
    }
  }

  fun runAgain(
    model: Model,
    message: ChatMessageText,
    onError: (String) -> Unit,
    allowThinking: Boolean = false,
  ) {
    viewModelScope.launch(Dispatchers.Default) {
      // Wait for model to be initialized.
      while (model.instance == null) {
        delay(100)
      }

      // Clone the clicked message and add it.
      addMessage(model = model, message = message.clone())

      // Run inference.
      generateResponse(
        model = model,
        input = message.content,
        onError = onError,
        allowThinking = allowThinking,
      )
    }
  }

  fun handleError(
    context: Context,
    task: Task,
    model: Model,
    modelManagerViewModel: ModelManagerViewModel,
    errorMessage: String,
  ) {
    // Remove the "loading" message.
    if (getLastMessage(model = model) is ChatMessageLoading) {
      removeLastMessage(model = model)
    }

    // Show error message.
    addMessage(model = model, message = ChatMessageError(content = errorMessage))

    // Clean up and re-initialize.
    viewModelScope.launch(Dispatchers.Default) {
      modelManagerViewModel.cleanupModel(
        context = context,
        task = task,
        model = model,
        onDone = {
          modelManagerViewModel.initializeModel(context = context, task = task, model = model)

          // Add a warning message for re-initializing the session.
          addMessage(
            model = model,
            message = ChatMessageWarning(content = "Session re-initialized"),
          )
        },
      )
    }
  }
}

@HiltViewModel class LlmChatViewModel @Inject constructor(
  repo: ChatRepository
) : LlmChatViewModelBase() {
  init { chatRepository = repo }
}

@HiltViewModel class LlmAskImageViewModel @Inject constructor(
  repo: ChatRepository
) : LlmChatViewModelBase() {
  init { chatRepository = repo }
}

@HiltViewModel class LlmAskAudioViewModel @Inject constructor(
  repo: ChatRepository
) : LlmChatViewModelBase() {
  init { chatRepository = repo }
}
