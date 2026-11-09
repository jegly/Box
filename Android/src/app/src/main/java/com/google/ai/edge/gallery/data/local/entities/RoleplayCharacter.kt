package com.google.ai.edge.gallery.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Roleplay: personaje guardado en la base de datos cifrada. Campos alineados con chara_card_v2
 * (SillyTavern / Layla). Las listas se guardan como texto JSON para no necesitar TypeConverters;
 * data/characters/CharacterMappers.kt convierte desde y hacia CharacterCard.
 *
 * Si cambias columnas aquí, añade la migración en BoxChatDatabase.
 */
@Entity(tableName = "characters")
data class RoleplayCharacter(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val alternateGreetingsJson: String = "[]",
    val exampleMessages: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    val creator: String = "",
    val characterVersion: String = "",
    val tagsJson: String = "[]",
    val source: String = "",
    // "Impresión" de Layla: resumen de lo que el personaje piensa del usuario (fase 2)
    val impression: String = "",
    // Ruta del PNG del avatar dentro del almacenamiento privado de la app ("" = sin avatar)
    val avatarPath: String = "",
    val extensionsJson: String = "{}",
    val characterBookJson: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
