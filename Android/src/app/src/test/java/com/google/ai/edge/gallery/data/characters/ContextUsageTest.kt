package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextUsageTest {
    private val long = "x".repeat(300) // ~106 tokens

    @Test fun smallChatIsNotActive() {
        val u = ContextUsage.compute("", listOf("hola", "mundo"), keep = 1, dropped = 0, maxTokens = 2048)
        assertFalse(u.windowActive)
        assertTrue(u.percent < 5)
    }

    @Test fun droppedMessagesDoNotCount() {
        val texts = List(40) { long }
        val all = ContextUsage.compute("", texts, keep = 1, dropped = 0, maxTokens = 100000)
        val windowed = ContextUsage.compute("", texts, keep = 1, dropped = 30, maxTokens = 100000)
        assertTrue(windowed.usedTokens < all.usedTokens)
        assertTrue(windowed.windowActive)
        assertFalse(all.windowActive)
    }

    @Test fun overflowIsClampedAndMarkedActive() {
        val u = ContextUsage.compute("", List(100) { long }, keep = 1, dropped = 0, maxTokens = 2048)
        assertEquals(2048, u.usedTokens)
        assertEquals(100, u.percent)
        assertTrue(u.windowActive)
    }

    @Test fun promptCountsAndBadInputsAreSafe() {
        val u = ContextUsage.compute(long, emptyList(), keep = 5, dropped = 9, maxTokens = 0)
        assertEquals(1, u.maxTokens)
        assertTrue(u.usedTokens >= 0)
        val v = ContextUsage.compute(long, listOf("a"), keep = 1, dropped = 99, maxTokens = 2048)
        assertTrue(v.usedTokens > 100)
    }
}
