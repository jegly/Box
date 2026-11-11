package com.google.ai.edge.gallery.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Roleplay: perfil del usuario ("persona"). El nombre reemplaza a {{user}} y la descripción se
 * le da al modelo para que sepa quién eres. Solo una persona está activa a la vez.
 *
 * Si cambias columnas aquí, añade la migración en BoxChatDatabase.
 */
@Entity(tableName = "personas")
data class UserPersona(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
