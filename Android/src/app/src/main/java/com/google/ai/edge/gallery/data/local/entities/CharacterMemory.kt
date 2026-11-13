package com.google.ai.edge.gallery.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Roleplay: un recuerdo a largo plazo de un personaje (por ejemplo, el resumen de una charla
 * anterior). Se inyecta en el prompt al abrir un chat nuevo con ese personaje.
 */
@Entity(tableName = "character_memories", indices = [Index("characterId")])
data class CharacterMemory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val characterId: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)
