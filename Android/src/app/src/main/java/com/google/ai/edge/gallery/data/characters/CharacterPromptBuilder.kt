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

    /**
     * Instrucción base cuando la tarjeta no trae system_prompt propio. Corta a propósito: los
     * modelos pequeños siguen mejor un prompt directo (como el de Layla) que uno largo.
     */
    const val DEFAULT_INSTRUCTION =
        "Write only {{char}}'s next reply. Never speak, think or act for {{user}}. " +
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
        // Bloque ya armado por MemoryPromptBuilder ("" = sin recuerdos).
        memory: String = "",
    ): BuiltPrompt {
        val charName = card.name.trim().ifEmpty { "Character" }
        val user = userName.trim().ifEmpty { DEFAULT_USER_NAME }
        fun resolve(text: String): String = applyMacros(text, charName, user).trim()

        // Texto plano, sin markdown (#, *, etc.): así lo arma Layla y los modelos pequeños lo
        // siguen mejor. Orden: [prompt propio de la tarjeta] info del usuario, descripción,
        // personalidad, escenario, recuerdos, ejemplos, y al final la frase de identidad.
        val customSystem = card.systemPrompt.trim()
        val hasCustom = customSystem.isNotEmpty()

        val parts = mutableListOf<String>()
        if (hasCustom) {
            parts += resolve(ORIGINAL.replace(customSystem) { defaultInstruction })
        }
        resolve(userDescription).takeIf { it.isNotEmpty() }
            ?.let { parts += "Information about $user: $it" }
        resolve(card.description).takeIf { it.isNotEmpty() }?.let { parts += it }
        // Etiquetas como en el story string por defecto de SillyTavern.
        resolve(card.personality).takeIf { it.isNotEmpty() }
            ?.let { parts += "$charName's personality: $it" }
        resolve(card.scenario).takeIf { it.isNotEmpty() }?.let { parts += "Scenario: $it" }
        memory.trim().takeIf { it.isNotEmpty() }?.let { parts += it }
        resolve(START_MARKER.replace(card.exampleMessages) { "\n" }).takeIf { it.isNotEmpty() }
            ?.let { parts += "[Example Chat]\n$it" }

        // Frase final: queda al final del prompt para que sea lo más reciente que lee el modelo.
        val identity = "The User is $user. You ARE $charName. " +
            "Embody the character and personality completely."
        parts += if (hasCustom) identity else "$identity ${resolve(defaultInstruction)}"

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
