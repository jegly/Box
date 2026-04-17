package com.google.ai.edge.gallery.data.local

import com.google.ai.edge.gallery.data.local.dao.ConversationDao
import com.google.ai.edge.gallery.data.local.dao.MessageDao
import com.google.ai.edge.gallery.data.local.entities.Conversation
import com.google.ai.edge.gallery.data.local.entities.Message
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Box: Repository for encrypted chat persistence.
 * Bridges the in-memory ChatViewModel messages with the SQLCipher-encrypted Room database.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {
    // --- Conversations ---

    fun getAllConversations(): Flow<List<Conversation>> = conversationDao.getAllConversations()

    fun getConversationsByTask(taskType: String): Flow<List<Conversation>> =
        conversationDao.getConversationsByTask(taskType)

    suspend fun getConversationById(id: String): Conversation? =
        conversationDao.getConversationById(id)

    suspend fun createConversation(
        title: String = "New Chat",
        taskType: String = "",
        modelName: String = "",
    ): Conversation {
        val conversation = Conversation(
            title = title,
            taskType = taskType,
            modelName = modelName,
        )
        conversationDao.insert(conversation)
        return conversation
    }

    suspend fun updateConversation(conversation: Conversation) =
        conversationDao.update(conversation)

    suspend fun deleteConversation(conversation: Conversation) =
        conversationDao.delete(conversation)

    suspend fun deleteAllConversations() = conversationDao.deleteAll()

    suspend fun getLatestConversationForModel(modelName: String): Conversation? =
        conversationDao.getLatestForModel(modelName)

    // --- Messages ---

    fun getMessagesForConversation(conversationId: String): Flow<List<Message>> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun getMessagesSync(conversationId: String): List<Message> =
        messageDao.getMessagesForConversationSync(conversationId)

    suspend fun saveMessage(
        conversationId: String,
        role: String,
        content: String,
        tokenCount: Int = 0,
        latencyMs: Long = 0,
    ): Message {
        val message = Message(
            conversationId = conversationId,
            role = role,
            content = content,
            tokenCount = tokenCount,
            latencyMs = latencyMs,
        )
        messageDao.insert(message)

        // Update conversation metadata
        val conversation = conversationDao.getConversationById(conversationId)
        if (conversation != null) {
            conversationDao.update(
                conversation.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = conversation.messageCount + 1,
                    // Auto-title from first user message
                    title = if (conversation.messageCount == 0 && role == "user") {
                        content.take(50).let { if (content.length > 50) "$it…" else it }
                    } else {
                        conversation.title
                    }
                )
            )
        }
        return message
    }

    suspend fun deleteMessagesForConversation(conversationId: String) =
        messageDao.deleteAllForConversation(conversationId)
}
