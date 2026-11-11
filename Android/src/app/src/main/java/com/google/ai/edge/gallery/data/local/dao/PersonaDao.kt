package com.google.ai.edge.gallery.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.google.ai.edge.gallery.data.local.entities.UserPersona
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<UserPersona>>

    @Query("SELECT * FROM personas WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<UserPersona?>

    @Query("SELECT * FROM personas WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): UserPersona?

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getById(id: String): UserPersona?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(persona: UserPersona)

    @Delete
    suspend fun delete(persona: UserPersona)

    /** Deja activa solo la persona con ese id (una sola consulta, así nunca quedan dos activas). */
    @Query("UPDATE personas SET isActive = (id = :id)")
    suspend fun setActiveOnly(id: String)
}
