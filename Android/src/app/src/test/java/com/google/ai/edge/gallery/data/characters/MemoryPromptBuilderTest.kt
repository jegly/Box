package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPromptBuilderTest {
    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test fun timeAgoUnits() {
        assertEquals("just now", MemoryPromptBuilder.timeAgo(now - 5_000, now))
        assertEquals("1 minute ago", MemoryPromptBuilder.timeAgo(now - 60_000, now))
        assertEquals("5 hours ago", MemoryPromptBuilder.timeAgo(now - 5 * 3_600_000, now))
        assertEquals("1 day ago", MemoryPromptBuilder.timeAgo(now - day, now))
        assertEquals("3 days ago", MemoryPromptBuilder.timeAgo(now - 3 * day, now))
        assertEquals("2 weeks ago", MemoryPromptBuilder.timeAgo(now - 14 * day, now))
        assertEquals("2 months ago", MemoryPromptBuilder.timeAgo(now - 65 * day, now))
        assertEquals("1 year ago", MemoryPromptBuilder.timeAgo(now - 400 * day, now))
    }

    @Test fun futureTimestampIsJustNow() {
        assertEquals("just now", MemoryPromptBuilder.timeAgo(now + day, now))
    }

    @Test fun emptyGivesEmpty() {
        assertEquals("", MemoryPromptBuilder.build("Ana", emptyList(), 3, now))
        assertEquals("", MemoryPromptBuilder.build("Ana", listOf(MemoryEntry("x", now)), 0, now))
        assertEquals("", MemoryPromptBuilder.build("Ana", listOf(MemoryEntry("  ", now)), 3, now))
    }

    @Test fun picksMostRecentAndOrdersOldToNew() {
        val m = listOf(
            MemoryEntry("viejo", now - 30 * day),
            MemoryEntry("medio", now - 3 * day),
            MemoryEntry("nuevo", now - day),
        )
        val text = MemoryPromptBuilder.build("Ana", m, 2, now)
        assertEquals("Ana remembers previously:\n[3 days ago] medio\n[1 day ago] nuevo", text)
    }

    @Test fun canHideTime() {
        val text = MemoryPromptBuilder.build("Ana", listOf(MemoryEntry(" hola ", now - day)), 3, now, showTime = false)
        assertEquals("Ana remembers previously:\nhola", text)
    }

    @Test fun builderPlacesMemoryBeforeExamples() {
        val card = CharacterCard(name = "Ana", description = "Una chica", exampleMessages = "{{char}}: hola")
        val built = CharacterPromptBuilder.build(card, memory = "Ana remembers previously:\n[1 day ago] algo")
        val p = built.systemPrompt
        assertTrue(p.contains("Ana remembers previously:"))
        assertTrue(p.indexOf("remembers previously") < p.indexOf("Example dialogue"))
        assertTrue(p.indexOf("remembers previously") > p.indexOf("Una chica"))
    }

    @Test fun builderWithoutMemoryHasNoBlock() {
        val built = CharacterPromptBuilder.build(CharacterCard(name = "Ana"))
        assertTrue(!built.systemPrompt.contains("remembers previously"))
    }
}
