package com.google.ai.edge.gallery.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<RoleplayCharacter>>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getById(id: String): RoleplayCharacter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(character: RoleplayCharacter)

    @Update
    suspend fun update(character: RoleplayCharacter)

    @Delete
    suspend fun delete(character: RoleplayCharacter)
}
