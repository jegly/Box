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
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.local.ChatRepository
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
  private fun persistAssistantMessage(model: Model, content: String, latencyMs: Long = 0) {
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
          
          try {
            repo.saveMessage(
              conversationId = convId,
              role = "assistant",
              content = content,
              latencyMs = latencyMs,
            )
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
  ) {
    val accelerator = model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = "")
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(true)
      setPreparing(true)

      // Box: Persist user message to encrypted DB
      if (input.isNotEmpty()) {
        persistUserMessage(model, input)
      }

      // Loading.
      addMessage(model = model, message = ChatMessageLoading(accelerator = accelerator))

      // Wait for instance to be initialized.
      while (model.instance == null) {
        delay(100)
      }
      delay(500)

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
                  persistAssistantMessage(model, assistantMsg.content, assistantMsg.latencyMs.toLong())
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
