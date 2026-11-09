package com.google.ai.edge.gallery.data.characters

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CharacterCardParserTest {

    // JSON con la misma forma que el esqueleto de Layla / SillyTavern.
    private val skeletonJson = """
        {
          "spec": "chara_card_v2",
          "spec_version": "2.0",
          "data": {
            "character_version": "1.0",
            "creator": "Info del creador",
            "creator_notes": "",
            "description": "Descripción física y trasfondo.",
            "extensions": {},
            "name": "Layla",
            "personality": "Curiosa y cálida.",
            "scenario": "Una cafetería de noche.",
            "system_prompt": "",
            "tags": ["Etiqueta1", "Etiqueta2"],
            "first_mes": "Hola, {{user}}.",
            "post_history_instructions": "",
            "mes_example": "<START>\n{{user}}: Hola\n{{char}}: Buenas.",
            "alternate_greetings": ["Segundo saludo", "Tercer saludo"],
            "source": "Serie X"
          }
        }
    """.trimIndent()

    // ------------------------------------------------------------ helpers de PNG

    private val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        val bos = ByteArrayOutputStream()
        val out = DataOutputStream(bos)
        out.writeInt(data.size)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.writeInt(crc.value.toInt())
        return bos.toByteArray()
    }

    private val ihdr = chunk("IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0))
    private val iend = chunk("IEND", ByteArray(0))

    private fun png(vararg chunks: ByteArray): ByteArray {
        var result = signature + ihdr
        for (c in chunks) result += c
        return result + iend
    }

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun textChunk(keyword: String, text: String): ByteArray =
        chunk(
            "tEXt",
            keyword.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + text.toByteArray(Charsets.ISO_8859_1),
        )

    private fun deflate(bytes: ByteArray): ByteArray {
        val d = Deflater()
        d.setInput(bytes)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun assertFails(block: () -> Unit): CharacterCardException {
        try {
            block()
        } catch (e: CharacterCardException) {
            return e
        }
        fail("Se esperaba CharacterCardException")
        throw IllegalStateException()
    }

    // ------------------------------------------------------------ JSON

    @Test
    fun parsesLaylaSkeletonV2() {
        val card = CharacterCardParser.parse(skeletonJson.toByteArray()).card
        assertEquals("Layla", card.name)
        assertEquals("Descripción física y trasfondo.", card.description)
        assertEquals("Curiosa y cálida.", card.personality)
        assertEquals("Una cafetería de noche.", card.scenario)
        assertEquals("Hola, {{user}}.", card.firstMessage)
        assertEquals(listOf("Segundo saludo", "Tercer saludo"), card.alternateGreetings)
        assertEquals(listOf("Etiqueta1", "Etiqueta2"), card.tags)
        assertEquals("Info del creador", card.creator)
        assertEquals("1.0", card.characterVersion)
        assertEquals("Serie X", card.source)
        assertEquals("{}", card.extensionsJson)
        assertEquals("", card.characterBookJson)
        assertTrue(card.exampleMessages.startsWith("<START>\n"))
    }

    @Test
    fun parsesV1FlatCard() {
        val card = CharacterCardParser.parseJson(
            """{"name":"Ana","description":"d","personality":"p","scenario":"s","first_mes":"hi","mes_example":"x"}""",
        )
        assertEquals("Ana", card.name)
        assertEquals("hi", card.firstMessage)
        assertEquals("x", card.exampleMessages)
        assertEquals(emptyList<String>(), card.alternateGreetings)
    }

    @Test
    fun parsesV3WithSourceListAndCommaSeparatedTags() {
        val card = CharacterCardParser.parseJson(
            """{"spec":"chara_card_v3","spec_version":"3.0","data":{"name":"Zed","source":["a","b"],"tags":"uno, dos ,"}}""",
        )
        assertEquals("Zed", card.name)
        assertEquals("a, b", card.source)
        assertEquals(listOf("uno", "dos"), card.tags)
    }

    @Test
    fun toleratesNullsAndWrongTypes() {
        val card = CharacterCardParser.parseJson(
            """{"name":"N","description":null,"alternate_greetings":[1,null,"x"],"extensions":[1],"character_version":2}""",
        )
        assertEquals("", card.description)
        assertEquals(listOf("1", "x"), card.alternateGreetings)
        assertEquals("{}", card.extensionsJson)
        assertEquals("2", card.characterVersion)
    }

    @Test
    fun acceptsByteOrderMark() {
        val card = CharacterCardParser.parse(("﻿" + skeletonJson).toByteArray()).card
        assertEquals("Layla", card.name)
    }

    @Test
    fun rejectsEmptyInvalidAndNamelessCards() {
        assertFails { CharacterCardParser.parse(ByteArray(0)) }
        assertFails { CharacterCardParser.parse("esto no es json".toByteArray()) }
        assertFails { CharacterCardParser.parseJson("[1,2,3]") }
        assertFails { CharacterCardParser.parseJson("""{"description":"sin nombre"}""") }
        assertFails { CharacterCardParser.parseJson("""{"data":{"name":"   "}}""") }
    }

    @Test
    fun rejectsOversizedFile() {
        val e = assertFails { CharacterCardParser.parse(ByteArray(CharacterCardParser.MAX_FILE_BYTES + 1)) }
        assertTrue(e.message!!.contains("grande"))
    }

    @Test
    fun exportKeepsAllFieldsIncludingExtensionsAndLorebook() {
        val original = CharacterCardParser.parseJson(
            """
            {"spec":"chara_card_v2","spec_version":"2.0","data":{
              "name":"Mía","description":"d","personality":"p","scenario":"s","first_mes":"hola",
              "alternate_greetings":["a","b"],"mes_example":"e","system_prompt":"sp",
              "post_history_instructions":"ph","creator_notes":"n","creator":"c","character_version":"2",
              "tags":["t1","t2"],"source":"fuente",
              "extensions":{"talkativeness":"0.5"},
              "character_book":{"entries":[{"keys":["k"],"content":"lore"}]}
            }}
            """.trimIndent(),
        )
        val json = CharacterCardParser.encodeV2(original)
        assertTrue(json.contains("chara_card_v2"))
        assertEquals(original, CharacterCardParser.parseJson(json))
        assertTrue(original.characterBookJson.contains("lore"))
        assertTrue(original.extensionsJson.contains("talkativeness"))
    }

    // ------------------------------------------------------------ PNG

    @Test
    fun readsCardFromPngTextChunkAndKeepsTheImageAsAvatar() {
        val bytes = png(textChunk("chara", b64(skeletonJson)))
        val parsed = CharacterCardParser.parse(bytes)
        assertEquals("Layla", parsed.card.name)
        assertNotNull(parsed.avatarPng)
        assertArrayEquals(bytes, parsed.avatarPng)
    }

    @Test
    fun jsonFilesHaveNoAvatar() {
        assertNull(CharacterCardParser.parse(skeletonJson.toByteArray()).avatarPng)
    }

    @Test
    fun pngPrefersCcv3OverChara() {
        val bytes = png(
            textChunk("chara", b64("""{"name":"Viejo"}""")),
            textChunk("ccv3", b64("""{"spec":"chara_card_v3","data":{"name":"Nuevo"}}""")),
        )
        assertEquals("Nuevo", CharacterCardParser.parse(bytes).card.name)
    }

    @Test
    fun readsCompressedZtxtChunk() {
        val payload = deflate(b64(skeletonJson).toByteArray(Charsets.ISO_8859_1))
        val data = "chara".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0) + payload
        assertEquals("Layla", CharacterCardParser.parse(png(chunk("zTXt", data))).card.name)
    }

    @Test
    fun readsItxtChunkPlainAndCompressed() {
        val text = b64(skeletonJson).toByteArray(Charsets.UTF_8)
        val key = "chara".toByteArray(Charsets.ISO_8859_1)
        val plain = chunk("iTXt", key + byteArrayOf(0, 0, 0, 0, 0) + text)
        val compressed = chunk("iTXt", key + byteArrayOf(0, 1, 0, 0, 0) + deflate(text))
        assertEquals("Layla", CharacterCardParser.parse(png(plain)).card.name)
        assertEquals("Layla", CharacterCardParser.parse(png(compressed)).card.name)
    }

    @Test
    fun acceptsPlainJsonInsideTheChunk() {
        val bytes = png(textChunk("chara", """{"name":"Plano"}"""))
        assertEquals("Plano", CharacterCardParser.parse(bytes).card.name)
    }

    @Test
    fun keepsAccentsAndEmojiThroughPngBase64() {
        val bytes = png(textChunk("chara", b64("""{"name":"Señorita Ñandú 🌙","description":"áéíóú"}""")))
        val card = CharacterCardParser.parse(bytes).card
        assertEquals("Señorita Ñandú 🌙", card.name)
        assertEquals("áéíóú", card.description)
    }

    @Test
    fun pngWithoutCardDataFailsCleanly() {
        val e = assertFails { CharacterCardParser.parse(png()) }
        assertTrue(e.message!!.contains("PNG"))
        assertFails { CharacterCardParser.parse(png(textChunk("Comment", "hola"))) }
    }

    @Test
    fun truncatedOrCorruptPngDoesNotCrash() {
        val good = png(textChunk("chara", b64(skeletonJson)))
        assertFails { CharacterCardParser.parse(good.copyOf(30)) }
        // Un chunk que declara ser enorme (~2 GB) pero el archivo sigue: no debe leer fuera del arreglo.
        val lyingChunk = signature + byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F, 0x74, 0x45, 0x58, 0x74) + ByteArray(16) { 1 }
        assertFails { CharacterCardParser.parse(lyingChunk) }
        assertFails { CharacterCardParser.parse(png(textChunk("chara", "%%%%no-es-base64%%%%"))) }
    }
}
