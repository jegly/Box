package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowPlannerTest {
    // Cada mensaje de 294 caracteres = 98 + 6 = 104 tokens estimados.
    private fun msg() = "x".repeat(294)

    @Test
    fun doesNothingBelowTheHighThreshold() {
        val history = List(4) { msg() }
        // 4*104 + sistema 200 + input 50 = 666  <=  0.70 * 2048 = 1433
        assertEquals(0, ContextWindowPlanner.planDrop(200, history, 50, keep = 1, maxTokens = 2048))
    }

    @Test
    fun dropsInPairsDownToTheLowThreshold() {
        val history = List(17) { msg() }       // greeting + 16 mensajes
        val drop = ContextWindowPlanner.planDrop(300, history, 50, keep = 1, maxTokens = 2048)
        assertTrue(drop > 0)
        assertEquals(0, drop % 2)
        val kept = history.size - drop
        val total = 300 + kept * 104 + 50
        assertTrue("debe quedar <= 45 %", total <= (2048 * 0.45).toInt())
        // y no se recorta de más: con 2 mensajes más ya pasaría del umbral bajo
        assertTrue(total + 2 * 104 > (2048 * 0.45).toInt())
    }

    @Test
    fun neverDropsTheProtectedPrefix() {
        val history = List(6) { msg() }
        val drop = ContextWindowPlanner.planDrop(1900, history, 50, keep = 2, maxTokens = 2048)
        assertTrue(drop <= history.size - 2)
    }

    @Test
    fun handlesEmptyAndTinyHistories() {
        assertEquals(0, ContextWindowPlanner.planDrop(5000, emptyList(), 50, keep = 0, maxTokens = 2048))
        assertEquals(0, ContextWindowPlanner.planDrop(5000, listOf("a"), 50, keep = 1, maxTokens = 2048))
    }

    @Test
    fun estimateIsMonotonicAndNeverZero() {
        assertTrue(ContextWindowPlanner.estimateTokens("") > 0)
        assertTrue(ContextWindowPlanner.estimateTokens("hola mundo") < ContextWindowPlanner.estimateTokens("hola mundo ".repeat(10)))
    }
}
