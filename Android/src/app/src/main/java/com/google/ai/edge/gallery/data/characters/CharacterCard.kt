package com.google.ai.edge.gallery.data.characters

/**
 * Tarjeta de personaje en memoria, sin dependencias de Room ni de Android.
 * Cubre los campos de chara_card_v2 (SillyTavern / Layla). Al leer también se aceptan v1 y v3.
 */
data class CharacterCard(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val exampleMessages: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    val creator: String = "",
    val characterVersion: String = "",
    val tags: List<String> = emptyList(),
    val source: String = "",
    /** JSON crudo de "extensions". Se conserva tal cual para no perder datos al exportar. */
    val extensionsJson: String = "{}",
    /** JSON crudo de "character_book" (lorebook). Se conserva; el chat aún no lo usa. */
    val characterBookJson: String = "",
)

/** Resultado de leer un archivo: la tarjeta y, si venía en un PNG, la imagen original (avatar). */
class ParsedCharacterCard(val card: CharacterCard, val avatarPng: ByteArray?)

/** Error legible para mostrar al usuario cuando una tarjeta no se puede importar. */
class CharacterCardException(message: String, cause: Throwable? = null) : Exception(message, cause)
