package com.google.ai.edge.gallery.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.google.ai.edge.gallery.data.local.entities.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversationSync(conversationId: String): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<Message>)

    @Update
    suspend fun update(message: Message)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND role = 'assistant' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastAssistant(conversationId: String): Message?

    @Delete
    suspend fun delete(message: Message)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteAllForConversation(conversationId: String)
}
