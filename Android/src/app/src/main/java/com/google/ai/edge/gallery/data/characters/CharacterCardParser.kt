package com.google.ai.edge.gallery.data.characters

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Lee y escribe tarjetas de personaje.
 *
 * Lee: JSON (chara_card_v1, v2 y v3) y PNG con el JSON incrustado (chunk tEXt, zTXt o iTXt
 * con la clave "chara" o "ccv3", en base64), que es como circulan casi todas las tarjetas.
 * Escribe: JSON chara_card_v2.
 *
 * Es tolerante con campos ausentes, nulos o de tipo raro, porque las tarjetas reales vienen
 * de muchas herramientas distintas. Solo exige que tenga nombre.
 */
object CharacterCardParser {
    const val MAX_FILE_BYTES = 20 * 1024 * 1024
    private const val MAX_TEXT_BYTES = 8 * 1024 * 1024

    private val PNG_SIGNATURE =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private val json = Json { isLenient = true }

    /** Lee una tarjeta desde los bytes de un archivo .json o .png. */
    fun parse(bytes: ByteArray): ParsedCharacterCard {
        if (bytes.isEmpty()) throw CharacterCardException("El archivo está vacío")
        if (bytes.size > MAX_FILE_BYTES) throw CharacterCardException("El archivo es demasiado grande")
        return if (isPng(bytes)) {
            val text = extractPngCardJson(bytes)
                ?: throw CharacterCardException("El PNG no contiene datos de personaje")
            ParsedCharacterCard(parseJson(text), bytes)
        } else {
            ParsedCharacterCard(parseJson(bytes.toString(Charsets.UTF_8)), null)
        }
    }

    /** Lee una tarjeta desde texto JSON (v1 plano, o v2/v3 con el envoltorio "data"). */
    fun parseJson(text: String): CharacterCard {
        val root = try {
            json.parseToJsonElement(text.trim().removePrefix("﻿"))
        } catch (e: Exception) {
            throw CharacterCardException("El archivo no es un JSON válido", e)
        }
        val rootObj = root as? JsonObject
            ?: throw CharacterCardException("El JSON de la tarjeta debe ser un objeto")
        val data = (rootObj["data"] as? JsonObject) ?: rootObj

        val name = data.str("name").trim()
        if (name.isEmpty()) throw CharacterCardException("La tarjeta no tiene nombre")

        return CharacterCard(
            name = name,
            description = data.str("description"),
            personality = data.str("personality"),
            scenario = data.str("scenario"),
            firstMessage = data.str("first_mes"),
            alternateGreetings = data.strList("alternate_greetings"),
            exampleMessages = data.str("mes_example"),
            systemPrompt = data.str("system_prompt"),
            postHistoryInstructions = data.str("post_history_instructions"),
            creatorNotes = data.str("creator_notes"),
            creator = data.str("creator"),
            characterVersion = data.str("character_version"),
            tags = data.tagList("tags"),
            source = data.str("source").ifEmpty { data.strList("source").joinToString(", ") },
            extensionsJson = (data["extensions"] as? JsonObject)?.toString() ?: "{}",
            characterBookJson = (data["character_book"] as? JsonObject)?.toString() ?: "",
        )
    }

    /** Escribe la tarjeta como JSON chara_card_v2, listo para importar en SillyTavern o Layla. */
    fun encodeV2(card: CharacterCard): String {
        val data = buildJsonObject {
            put("name", card.name)
            put("description", card.description)
            put("personality", card.personality)
            put("scenario", card.scenario)
            put("first_mes", card.firstMessage)
            put("mes_example", card.exampleMessages)
            put("creator_notes", card.creatorNotes)
            put("system_prompt", card.systemPrompt)
            put("post_history_instructions", card.postHistoryInstructions)
            put("alternate_greetings", JsonArray(card.alternateGreetings.map { JsonPrimitive(it) }))
            put("tags", JsonArray(card.tags.map { JsonPrimitive(it) }))
            put("creator", card.creator)
            put("character_version", card.characterVersion)
            put("extensions", parseObjectOrEmpty(card.extensionsJson))
            if (card.source.isNotBlank()) put("source", card.source)
            if (card.characterBookJson.isNotBlank()) {
                put("character_book", parseObjectOrEmpty(card.characterBookJson))
            }
        }
        val root = buildJsonObject {
            put("spec", "chara_card_v2")
            put("spec_version", "2.0")
            put("data", data)
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), root)
    }

    // ---------------------------------------------------------------- PNG

    private fun isPng(b: ByteArray): Boolean =
        b.size >= 8 && PNG_SIGNATURE.indices.all { b[it] == PNG_SIGNATURE[it] }

    /** Recorre los chunks del PNG y devuelve el JSON de la tarjeta (prefiere ccv3 sobre chara). */
    private fun extractPngCardJson(png: ByteArray): String? {
        var charaV2: String? = null
        var charaV3: String? = null
        var pos = 8
        // Cada chunk: longitud(4) + tipo(4) + datos + CRC(4). No se valida el CRC a propósito.
        while (pos + 12 <= png.size) {
            val length = readInt(png, pos)
            val type = String(png, pos + 4, 4, Charsets.ISO_8859_1)
            val dataStart = pos + 8
            if (length < 0 || length > png.size - dataStart) break
            val dataEnd = dataStart + length
            if (type == "tEXt" || type == "zTXt" || type == "iTXt") {
                val entry = readTextChunk(type, png, dataStart, dataEnd)
                if (entry != null) {
                    when (entry.first.lowercase()) {
                        "ccv3" -> charaV3 = entry.second
                        "chara" -> charaV2 = entry.second
                    }
                }
            }
            if (type == "IEND") break
            pos = dataEnd + 4
        }
        val raw = charaV3 ?: charaV2 ?: return null
        return decodePayload(raw)
    }

    /** Devuelve (clave, texto) de un chunk tEXt/zTXt/iTXt, o null si está mal formado. */
    private fun readTextChunk(type: String, b: ByteArray, start: Int, end: Int): Pair<String, String>? {
        val keyEnd = indexOfZero(b, start, end)
        if (keyEnd < 0) return null
        val keyword = String(b, start, keyEnd - start, Charsets.ISO_8859_1)
        return try {
            when (type) {
                "tEXt" ->
                    Pair(keyword, String(b, keyEnd + 1, end - keyEnd - 1, Charsets.ISO_8859_1))
                "zTXt" -> {
                    // clave \0 método(1) datos comprimidos
                    if (keyEnd + 2 > end) return null
                    Pair(keyword, String(inflate(b, keyEnd + 2, end), Charsets.ISO_8859_1))
                }
                else -> {
                    // iTXt: clave \0 comprimido(1) método(1) idioma \0 clave traducida \0 texto
                    if (keyEnd + 3 > end) return null
                    val compressed = b[keyEnd + 1].toInt() != 0
                    val langEnd = indexOfZero(b, keyEnd + 3, end)
                    if (langEnd < 0) return null
                    val translatedEnd = indexOfZero(b, langEnd + 1, end)
                    if (translatedEnd < 0) return null
                    val textStart = translatedEnd + 1
                    val bytes =
                        if (compressed) inflate(b, textStart, end) else b.copyOfRange(textStart, end)
                    Pair(keyword, String(bytes, Charsets.UTF_8))
                }
            }
        } catch (e: DataFormatException) {
            null
        }
    }

    /** El contenido normal es base64 de un JSON en UTF-8; algunas herramientas guardan el JSON plano. */
    private fun decodePayload(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed
        return try {
            String(Base64.getMimeDecoder().decode(trimmed), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun inflate(data: ByteArray, from: Int, end: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data, from, end - from)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_TEXT_BYTES) {
                    throw CharacterCardException("Los datos del personaje son demasiado grandes")
                }
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun readInt(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or
            ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or
            (b[o + 3].toInt() and 0xFF)

    private fun indexOfZero(b: ByteArray, from: Int, end: Int): Int {
        for (i in from until end) if (b[i].toInt() == 0) return i
        return -1
    }

    // ---------------------------------------------------------------- JSON

    private fun JsonObject.str(key: String): String {
        val el = this[key]
        return if (el is JsonPrimitive && el !is JsonNull) el.content else ""
    }

    private fun JsonObject.strList(key: String): List<String> {
        val arr = (this[key] as? JsonArray) ?: return emptyList()
        return arr.mapNotNull { item ->
            if (item is JsonPrimitive && item !is JsonNull) item.content else null
        }
    }

    /** Las etiquetas suelen ser un arreglo, pero algunas tarjetas las traen separadas por comas. */
    private fun JsonObject.tagList(key: String): List<String> {
        val el = this[key]
        val raw = if (el is JsonPrimitive && el !is JsonNull) el.content.split(',') else strList(key)
        return raw.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseObjectOrEmpty(text: String): JsonObject =
        try {
            (json.parseToJsonElement(text) as? JsonObject) ?: JsonObject(emptyMap())
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
}
