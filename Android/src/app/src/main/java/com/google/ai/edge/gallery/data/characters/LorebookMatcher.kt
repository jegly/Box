package com.google.ai.edge.gallery.data.characters

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Una entrada del lorebook de la tarjeta (character_book de chara_card v2/v3). */
data class LoreEntry(
    val keys: List<String>,
    val secondaryKeys: List<String> = emptyList(),
    val content: String,
    val enabled: Boolean = true,
    /** Constante: entra siempre, sin necesitar palabras clave. */
    val constant: Boolean = false,
    /** Selectiva: además de una clave principal, debe aparecer una secundaria. */
    val selective: Boolean = false,
    val caseSensitive: Boolean = false,
    val insertionOrder: Int = 0,
    val priority: Int = 0,
    /** Título de la entrada (campo `comment` o `name` de las tarjetas); solo para mostrar. */
    val comment: String = "",
)

/**
 * Lorebook: entradas de información del mundo que se añaden al prompt solo cuando la charla
 * menciona sus palabras clave. Lógica pura (sin Android) con pruebas unitarias.
 */
object LorebookMatcher {
    private val json = Json { ignoreUnknownKeys = true }

    /** Lee las entradas del JSON de "character_book". Devuelve vacío si falta o está roto. */
    fun parse(characterBookJson: String): List<LoreEntry> {
        if (characterBookJson.isBlank()) return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(characterBookJson).jsonObject
            val entries = root["entries"]
            val list: List<JsonElement> = when (entries) {
                is JsonArray -> entries
                is JsonObject -> entries.values.toList() // algunos formatos usan un mapa
                else -> emptyList()
            }
            list.mapNotNull { (it as? JsonObject)?.toEntry() }
        }.getOrDefault(emptyList())
    }

    private fun JsonObject.toEntry(): LoreEntry? {
        val content = (this["content"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (content.isEmpty()) return null
        fun strings(key: String): List<String> =
            ((this[key] as? JsonArray) ?: JsonArray(emptyList()))
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                .filter { it.isNotEmpty() }
        fun bool(key: String, def: Boolean) = (this[key] as? JsonPrimitive)?.booleanOrNull ?: def
        fun int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull ?: 0
        return LoreEntry(
            keys = strings("keys"),
            secondaryKeys = strings("secondary_keys"),
            content = content,
            enabled = bool("enabled", true),
            constant = bool("constant", false),
            selective = bool("selective", false),
            caseSensitive = bool("case_sensitive", false),
            insertionOrder = int("insertion_order"),
            priority = int("priority"),
            comment = ((this["comment"] ?: this["name"]) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(),
        )
    }

    /** "reina, castillo , rey" -> [reina, castillo, rey] */
    fun parseKeys(text: String): List<String> =
        text.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun formatKeys(keys: List<String>): String = keys.joinToString(", ")

    /**
     * Escribe las entradas como `character_book` (formato chara_card v2, compatible con
     * SillyTavern). Conserva los demás campos del libro original (nombre, descripción, etc.); los
     * campos extra de cada entrada que esta app no edita no se conservan.
     */
    fun encode(entries: List<LoreEntry>, originalJson: String = ""): String {
        val original: JsonObject? =
            runCatching { json.parseToJsonElement(originalJson).jsonObject }.getOrNull()
        val root = buildJsonObject {
            original?.forEach { (k, v) -> if (k != "entries") put(k, v) }
            put("entries", buildJsonArray {
                entries.forEachIndexed { i, e ->
                    add(buildJsonObject {
                        put("id", i)
                        put("keys", buildJsonArray { e.keys.forEach { add(JsonPrimitive(it)) } })
                        put("secondary_keys", buildJsonArray { e.secondaryKeys.forEach { add(JsonPrimitive(it)) } })
                        put("content", e.content)
                        put("comment", e.comment)
                        put("enabled", e.enabled)
                        put("constant", e.constant)
                        put("selective", e.selective)
                        put("case_sensitive", e.caseSensitive)
                        put("insertion_order", e.insertionOrder)
                        put("priority", e.priority)
                        put("position", "before_char")
                    })
                }
            })
        }
        return root.toString()
    }

    private fun contains(text: String, key: String, caseSensitive: Boolean) =
        text.contains(key, ignoreCase = !caseSensitive)

    /** ¿La entrada se activa con este texto? */
    fun matches(entry: LoreEntry, scanText: String): Boolean {
        if (!entry.enabled) return false
        if (entry.constant) return true
        if (entry.keys.none { contains(scanText, it, entry.caseSensitive) }) return false
        if (entry.selective && entry.secondaryKeys.isNotEmpty()) {
            return entry.secondaryKeys.any { contains(scanText, it, entry.caseSensitive) }
        }
        return true
    }

    /**
     * Elige las entradas activadas por [scanText]. Si hay más que [maxEntries], se quedan las de
     * mayor prioridad (a igual prioridad, las de menor orden de inserción). El resultado sale
     * ordenado por orden de inserción. Las constantes cuentan para el límite.
     */
    fun select(entries: List<LoreEntry>, scanText: String, maxEntries: Int): List<LoreEntry> {
        if (maxEntries <= 0) return emptyList()
        return entries
            .filter { matches(it, scanText) }
            .sortedWith(compareByDescending<LoreEntry> { it.priority }.thenBy { it.insertionOrder })
            .take(maxEntries)
            .sortedBy { it.insertionOrder }
    }

    /** Texto plano que se añade al prompt ("" si no hay entradas). */
    fun format(selected: List<LoreEntry>): String =
        selected.joinToString("\n") { it.content }

    /**
     * Mete el bloque de lore en el prompt, antes del último párrafo (la frase de identidad del
     * personaje), para que esa frase siga siendo lo último que lee el modelo.
     */
    fun injectIntoPrompt(prompt: String, loreBlock: String): String {
        if (loreBlock.isBlank()) return prompt
        val cut = prompt.lastIndexOf("\n\n")
        return if (cut < 0) "$prompt\n\n$loreBlock"
        else prompt.substring(0, cut) + "\n\n" + loreBlock + prompt.substring(cut)
    }

    /** Todo junto: texto de las últimas charlas + mensaje nuevo -> prompt con el lore activado. */
    fun applyToPrompt(
        prompt: String,
        entries: List<LoreEntry>,
        scanTexts: List<String>,
        maxEntries: Int,
    ): String {
        if (entries.isEmpty()) return prompt
        val chosen = select(entries, scanTexts.joinToString("\n"), maxEntries)
        return injectIntoPrompt(prompt, format(chosen))
    }
}
