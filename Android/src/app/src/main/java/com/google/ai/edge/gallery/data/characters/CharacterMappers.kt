package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** Convierte una tarjeta leída de un archivo en la fila lista para guardar en la base de datos. */
fun CharacterCard.toEntity(
    id: String = UUID.randomUUID().toString(),
    avatarPath: String = "",
): RoleplayCharacter =
    RoleplayCharacter(
        id = id,
        name = name,
        description = description,
        personality = personality,
        scenario = scenario,
        firstMessage = firstMessage,
        alternateGreetingsJson = alternateGreetings.toJsonArrayString(),
        exampleMessages = exampleMessages,
        systemPrompt = systemPrompt,
        postHistoryInstructions = postHistoryInstructions,
        creatorNotes = creatorNotes,
        creator = creator,
        characterVersion = characterVersion,
        tagsJson = tags.toJsonArrayString(),
        source = source,
        avatarPath = avatarPath,
        extensionsJson = extensionsJson,
        characterBookJson = characterBookJson,
    )

/** Convierte la fila de la base de datos a tarjeta (para armar el prompt o exportar). */
fun RoleplayCharacter.toCard(): CharacterCard =
    CharacterCard(
        name = name,
        description = description,
        personality = personality,
        scenario = scenario,
        firstMessage = firstMessage,
        alternateGreetings = alternateGreetingsJson.toStringList(),
        exampleMessages = exampleMessages,
        systemPrompt = systemPrompt,
        postHistoryInstructions = postHistoryInstructions,
        creatorNotes = creatorNotes,
        creator = creator,
        characterVersion = characterVersion,
        tags = tagsJson.toStringList(),
        source = source,
        extensionsJson = extensionsJson,
        characterBookJson = characterBookJson,
    )

internal fun List<String>.toJsonArrayString(): String =
    JsonArray(map { JsonPrimitive(it) }).toString()

internal fun String.toStringList(): List<String> =
    try {
        (Json.parseToJsonElement(this) as? JsonArray)
            ?.mapNotNull { item -> if (item is JsonPrimitive && item !is JsonNull) item.content else null }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
