package com.google.ai.edge.gallery.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val taskType: String = "", // "llm_chat", "ask_image", "ask_audio", etc.
    val modelName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val systemPrompt: String = "",
    // Roleplay: id del personaje con el que se creó este chat ("" = chat normal sin personaje)
    val characterId: String = "",
    // Roleplay: persona (perfil del usuario) con la que se creó este chat ("" = ninguna)
    val personaId: String = "",
)
