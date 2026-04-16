package com.google.ai.edge.gallery.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.local.ChatRepository
import com.google.ai.edge.gallery.data.local.entities.Conversation
import com.google.ai.edge.gallery.data.local.entities.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
}
