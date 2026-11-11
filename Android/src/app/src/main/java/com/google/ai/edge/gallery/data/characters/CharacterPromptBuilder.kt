package com.google.ai.edge.gallery.data.characters

import kotlin.random.Random

/**
 * Convierte una tarjeta de personaje en lo que necesita el chat: el prompt de sistema, las
 * instrucciones posteriores al historial y la lista de saludos posibles.
 *
 * Soporta los macros de SillyTavern {{char}} y {{user}} (también <BOT> y <USER>), el marcador
 * <START> de los diálogos de ejemplo y {{original}} dentro del prompt de sistema del personaje.
 */
object CharacterPromptBuilder {
    const val DEFAULT_USER_NAME = "User"

    /** Instrucción base cuando la tarjeta no trae system_prompt propio. */
    const val DEFAULT_INSTRUCTION =
        "Write {{char}}'s next reply in a never-ending roleplay between {{char}} and {{user}}. " +
            "Stay in character and write only as {{char}}; never speak, think or act for {{user}}. " +
            "Reply in the language {{user}} writes in."

    class BuiltPrompt(
        val systemPrompt: String,
        val postHistoryInstructions: String,
        val greetings: List<String>,
    )

    private val MACRO = Regex("""\{\{\s*(char|user)\s*\}\}|<(user|bot)>""", RegexOption.IGNORE_CASE)
    private val START_MARKER = Regex("""<START>""", RegexOption.IGNORE_CASE)
    private val ORIGINAL = Regex("""\{\{\s*original\s*\}\}""", RegexOption.IGNORE_CASE)

    /** Reemplaza {{char}}/{{user}} (y <BOT>/<USER>). Cualquier otro macro se deja como está. */
    fun applyMacros(text: String, charName: String, userName: String): String =
        MACRO.replace(text) { m ->
            when ((m.groups[1]?.value ?: m.groups[2]?.value ?: "").lowercase()) {
                "char", "bot" -> charName
                "user" -> userName
                else -> m.value
            }
        }

    fun build(
        card: CharacterCard,
        userName: String = DEFAULT_USER_NAME,
        defaultInstruction: String = DEFAULT_INSTRUCTION,
        userDescription: String = "",
    ): BuiltPrompt {
        val charName = card.name.trim().ifEmpty { "Character" }
        val user = userName.trim().ifEmpty { DEFAULT_USER_NAME }
        fun resolve(text: String): String = applyMacros(text, charName, user).trim()

        val customSystem = card.systemPrompt.trim()
        val main = if (customSystem.isEmpty()) {
            defaultInstruction
        } else {
            ORIGINAL.replace(customSystem) { defaultInstruction }
        }

        val parts = mutableListOf(resolve(main))
        resolve(card.description).takeIf { it.isNotEmpty() }
            ?.let { parts += "### Character: $charName\n$it" }
        resolve(card.personality).takeIf { it.isNotEmpty() }
            ?.let { parts += "### Personality\n$it" }
        resolve(card.scenario).takeIf { it.isNotEmpty() }
            ?.let { parts += "### Scenario\n$it" }
        resolve(userDescription).takeIf { it.isNotEmpty() }
            ?.let { parts += "### About $user\n$it" }
        resolve(START_MARKER.replace(card.exampleMessages) { "\n" }).takeIf { it.isNotEmpty() }
            ?.let { parts += "### Example dialogue\n$it" }

        val greetings = (listOf(card.firstMessage) + card.alternateGreetings)
            .map { resolve(it) }
            .filter { it.isNotEmpty() }
            .distinct()

        return BuiltPrompt(
            systemPrompt = parts.joinToString("\n\n"),
            postHistoryInstructions = resolve(card.postHistoryInstructions),
            greetings = greetings,
        )
    }

    /** Elige un saludo al azar. Null = no hay saludos, así que habla primero el usuario. */
    fun pickGreeting(greetings: List<String>, random: Random = Random.Default): String? =
        if (greetings.isEmpty()) null else greetings[random.nextInt(greetings.size)]
}
