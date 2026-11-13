package com.google.ai.edge.gallery.data.local

import com.google.ai.edge.gallery.data.characters.Swipes
import com.google.ai.edge.gallery.data.local.dao.CharacterDao
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
    private val characterDao: CharacterDao,
) {
    /** Roleplay: JSON del lorebook (character_book) del personaje, o "" si no tiene. */
    suspend fun getCharacterBookJson(characterId: String): String =
        characterDao.getById(characterId)?.characterBookJson ?: ""

    // --- Conversations ---

    fun getAllConversations(): Flow<List<Conversation>> = conversationDao.getAllConversations()

    suspend fun getAllConversationsSync(): List<Conversation> = conversationDao.getAllConversationsSync()

    fun getConversationsByTask(taskType: String): Flow<List<Conversation>> =
        conversationDao.getConversationsByTask(taskType)

    suspend fun getConversationById(id: String): Conversation? =
        conversationDao.getConversationById(id)

    suspend fun createConversation(
        title: String = "New Chat",
        taskType: String = "",
        modelName: String = "",
        systemPrompt: String = "",
        characterId: String = "",
        personaId: String = "",
    ): Conversation {
        val conversation = Conversation(
            title = title,
            taskType = taskType,
            modelName = modelName,
            systemPrompt = systemPrompt,
            characterId = characterId,
            personaId = personaId,
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

    // --- Swipes y edición (roleplay) ---

    /** Guarda una respuesta nueva como otra versión de la última respuesta del asistente. */
    suspend fun addSwipe(conversationId: String, text: String, latencyMs: Long = 0): Message? {
        val last = messageDao.getLastAssistant(conversationId) ?: return null
        val state = Swipes.withNew(Swipes.decode(last.alternatives, last.content, last.activeIndex), text)
        val updated = last.copy(
            content = text,
            alternatives = Swipes.encode(state.texts),
            activeIndex = state.index,
            latencyMs = latencyMs,
        )
        messageDao.update(updated)
        return updated
    }

    /** (versión activa, total) de la última respuesta del asistente. */
    suspend fun swipeInfo(conversationId: String): Pair<Int, Int>? {
        val last = messageDao.getLastAssistant(conversationId) ?: return null
        val state = Swipes.decode(last.alternatives, last.content, last.activeIndex)
        return state.index to state.count
    }

    /** Cambia a la versión anterior (-1) o siguiente (+1). Devuelve el mensaje ya actualizado. */
    suspend fun moveSwipe(conversationId: String, delta: Int): Message? {
        val last = messageDao.getLastAssistant(conversationId) ?: return null
        val state = Swipes.move(Swipes.decode(last.alternatives, last.content, last.activeIndex), delta)
        val updated = last.copy(content = state.active, activeIndex = state.index)
        messageDao.update(updated)
        return updated
    }

    /**
     * Edita el texto del mensaje número [position] (contando solo mensajes de usuario y asistente,
     * en orden). Si el rol no coincide con [expectedRole] no toca nada.
     */
    suspend fun editMessageAt(conversationId: String, position: Int, expectedRole: String, newText: String): Boolean {
        val chat = messageDao.getMessagesForConversationSync(conversationId)
            .filter { it.role == "user" || it.role == "assistant" }
        val target = chat.getOrNull(position) ?: return false
        if (target.role != expectedRole) return false
        val state = Swipes.replaceActive(Swipes.decode(target.alternatives, target.content, target.activeIndex), newText)
        messageDao.update(target.copy(content = newText, alternatives = Swipes.encode(state.texts)))
        return true
    }
}
