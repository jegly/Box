package com.google.ai.edge.gallery.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM character_memories WHERE characterId = :characterId ORDER BY createdAt DESC")
    fun observe(characterId: String): Flow<List<CharacterMemory>>

    @Query("SELECT * FROM character_memories WHERE characterId = :characterId ORDER BY createdAt DESC")
    suspend fun getAll(characterId: String): List<CharacterMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: CharacterMemory)

    @Delete
    suspend fun delete(memory: CharacterMemory)

    @Query("DELETE FROM character_memories WHERE characterId = :characterId")
    suspend fun deleteAll(characterId: String)
}
