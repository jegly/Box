package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LorebookEncodeTest {
    private val sample = listOf(
        LoreEntry(keys = listOf("reina", "castillo"), content = "La reina vive en el castillo.", comment = "Reina", insertionOrder = 0, priority = 5),
        LoreEntry(keys = emptyList(), content = "El mundo es oscuro.", constant = true, enabled = false, insertionOrder = 1),
        LoreEntry(keys = listOf("a"), secondaryKeys = listOf("b"), content = "x \"quote\"\nlinea", selective = true, caseSensitive = true, insertionOrder = 2),
    )

    @Test fun roundTrip() {
        assertEquals(sample, LorebookMatcher.parse(LorebookMatcher.encode(sample)))
    }

    @Test fun keepsRootFieldsOfOriginal() {
        val original = """{"name":"Mi mundo","description":"d","entries":[{"keys":["z"],"content":"viejo"}]}"""
        val out = LorebookMatcher.encode(sample, original)
        assertTrue(out.contains("\"name\":\"Mi mundo\""))
        assertEquals(3, LorebookMatcher.parse(out).size)
    }

    @Test fun emptyListAndBrokenOriginal() {
        assertEquals(emptyList<LoreEntry>(), LorebookMatcher.parse(LorebookMatcher.encode(emptyList(), "{no json")))
        assertTrue(LorebookMatcher.encode(emptyList()).contains("\"entries\":[]"))
    }

    @Test fun keysParsing() {
        assertEquals(listOf("reina", "castillo", "rey"), LorebookMatcher.parseKeys("reina, castillo ,\nrey,, "))
        assertEquals("a, b", LorebookMatcher.formatKeys(listOf("a", "b")))
    }
}
