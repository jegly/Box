package com.google.ai.edge.gallery.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.local.ChatRepository
import com.google.ai.edge.gallery.data.local.entities.Conversation
import com.google.ai.edge.gallery.data.local.entities.Message
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.common.chat.ChatMessage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class ChatHistoryViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> = chatRepository
        .getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedMessages = MutableStateFlow<List<Message>>(emptyList())
    val selectedMessages: StateFlow<List<Message>> = _selectedMessages.asStateFlow()

    fun loadMessages(conversationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedMessages.value = chatRepository.getMessagesSync(conversationId)
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.deleteConversation(conversation)
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.deleteAllConversations()
        }
    }

    /**
     * Continue a conversation by loading its messages back into the active chat.
     * Returns the model and messages to be loaded into the chat screen.
     */
    fun continueChat(conversation: Conversation): Pair<Model?, List<ChatMessage>> {
        val messages = runBlocking {
            chatRepository.getMessagesSync(conversation.id)
        }
        val chatMessages = messages.map { message ->
            ChatMessageText(
                content = message.content,
                side = if (message.role == "user") ChatSide.USER else ChatSide.AGENT,
                latencyMs = message.latencyMs.toFloat()
            )
        }
        
        // Create a Model object for the conversation (simplified for continuation)
        val model = if (conversation.modelName.isNotEmpty()) {
            Model(
                name = conversation.modelName,
                url = "", // Not needed for continuation
                configs = emptyList(),
                sizeInBytes = 0,
                downloadFileName = "",
                showBenchmarkButton = false,
                showRunAgainButton = false,
                imported = true,
                llmSupportImage = false,
                llmSupportAudio = false,
                llmSupportTinyGarden = false,
                llmSupportMobileActions = false,
                llmSupportThinking = false,
                llmMaxToken = 0,
                accelerators = emptyList(),
                isLlm = true,
                runtimeType = com.google.ai.edge.gallery.data.RuntimeType.LITERT_LM
            )
        } else null
        
        return Pair(model, chatMessages)
    }
}
