package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LorebookMatcherTest {
    private val book = """
        {"entries":[
          {"keys":["dragón","dragon"],"content":"El dragón se llama Ignis.","insertion_order":2},
          {"keys":["castillo"],"content":"El castillo está en ruinas.","insertion_order":1,"priority":5},
          {"keys":[],"content":"Siempre es de noche.","constant":true,"insertion_order":0},
          {"keys":["espada"],"content":"Oculta.","enabled":false},
          {"keys":["rey"],"secondary_keys":["corona"],"selective":true,"content":"El rey perdió su corona.","insertion_order":3},
          {"keys":["Sol"],"case_sensitive":true,"content":"El Sol es un dios.","insertion_order":4},
          {"keys":["x"],"content":""}
        ]}
    """.trimIndent()

    private val entries get() = LorebookMatcher.parse(book)

    @Test fun parsesAndSkipsEmptyContent() {
        assertEquals(6, entries.size)
        assertEquals(listOf("dragón", "dragon"), entries[0].keys)
        assertTrue(entries[2].constant)
        assertFalse(entries[3].enabled)
    }

    @Test fun brokenOrMissingBookGivesEmpty() {
        assertTrue(LorebookMatcher.parse("").isEmpty())
        assertTrue(LorebookMatcher.parse("{no").isEmpty())
        assertTrue(LorebookMatcher.parse("{}").isEmpty())
    }

    @Test fun mapStyleEntriesAreAccepted() {
        val e = LorebookMatcher.parse("""{"entries":{"0":{"keys":["a"],"content":"uno"}}}""")
        assertEquals(1, e.size)
    }

    @Test fun keywordMatchIsCaseInsensitiveByDefault() {
        val sel = LorebookMatcher.select(entries, "Vi un DRAGÓN enorme", 5)
        assertTrue(sel.any { it.content.contains("Ignis") })
    }

    @Test fun constantAlwaysEntersDisabledNever() {
        val sel = LorebookMatcher.select(entries, "hola espada", 5)
        assertEquals(listOf("Siempre es de noche."), sel.map { it.content })
    }

    @Test fun selectiveNeedsSecondaryKey() {
        assertFalse(LorebookMatcher.select(entries, "el rey habló", 9).any { it.content.contains("corona") })
        assertTrue(LorebookMatcher.select(entries, "el rey y su corona", 9).any { it.content.contains("corona") })
    }

    @Test fun caseSensitiveEntry() {
        assertFalse(LorebookMatcher.select(entries, "el sol sale", 9).any { it.content.contains("dios") })
        assertTrue(LorebookMatcher.select(entries, "el Sol sale", 9).any { it.content.contains("dios") })
    }

    @Test fun capKeepsHighestPriorityThenSortsByInsertionOrder() {
        // constante (prio 0, orden 0), castillo (prio 5, orden 1), dragón (prio 0, orden 2)
        val sel = LorebookMatcher.select(entries, "castillo y dragón", 2)
        assertEquals(listOf("El castillo está en ruinas.", "Siempre es de noche."), sel.map { it.content }.sortedBy { it })
        assertEquals(2, sel.size)
        assertTrue(sel.any { it.content.contains("castillo") })
    }

    @Test fun zeroMaxGivesNothing() {
        assertTrue(LorebookMatcher.select(entries, "dragón", 0).isEmpty())
    }

    @Test fun injectGoesBeforeLastParagraph() {
        val p = "Descripción.\n\nThe User is Ana. You ARE Bo."
        assertEquals("Descripción.\n\nLORE\n\nThe User is Ana. You ARE Bo.", LorebookMatcher.injectIntoPrompt(p, "LORE"))
        assertEquals("sola\n\nLORE", LorebookMatcher.injectIntoPrompt("sola", "LORE"))
        assertEquals(p, LorebookMatcher.injectIntoPrompt(p, "  "))
    }

    @Test fun applyToPromptEndToEnd() {
        val p = "Base.\n\nIdentidad."
        val out = LorebookMatcher.applyToPrompt(p, entries, listOf("hola", "¿viste el dragón?"), 5)
        assertTrue(out.contains("Ignis"))
        assertTrue(out.contains("Siempre es de noche."))
        assertTrue(out.endsWith("Identidad."))
        assertEquals(p, LorebookMatcher.applyToPrompt(p, emptyList(), listOf("dragón"), 5))
    }
}
