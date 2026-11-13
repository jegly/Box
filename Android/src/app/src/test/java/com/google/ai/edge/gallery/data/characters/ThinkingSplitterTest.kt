package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingSplitterTest {
    private fun run(vararg pieces: String, limit: Int = 1500): Pair<String, String> {
        val s = ThinkingSplitter(holdLimit = limit)
        val answer = StringBuilder()
        val thought = StringBuilder()
        for (p in pieces) {
            val o = s.feed(p)
            answer.append(o.answer)
            o.thought?.let { thought.append(it) }
        }
        val f = s.finish()
        answer.append(f.answer)
        f.thought?.let { thought.append(it) }
        return answer.toString() to thought.toString()
    }

    @Test fun splitsThoughtFromAnswer() {
        val (a, t) = run("Gwen's internal ", "state: annoyed.", "<channel|>", "Gwen te mira", " de reojo.")
        assertEquals("Gwen te mira de reojo.", a)
        assertEquals("Gwen's internal state: annoyed.", t)
    }

    @Test fun answerStreamsLiveAfterMarker() {
        val s = ThinkingSplitter()
        assertTrue(s.feed("pensando...").isEmpty)
        val o = s.feed("<channel|>Hola")
        assertEquals("Hola", o.answer)
        assertEquals("pensando...", o.thought)
        assertEquals(" mundo", s.feed(" mundo").answer)
        assertNull(s.feed("x").thought)
    }

    @Test fun noMarkerReleasesOnFinish() {
        val (a, t) = run("Hola, ", "¿qué tal?")
        assertEquals("Hola, ¿qué tal?", a)
        assertEquals("", t)
    }

    @Test fun noMarkerReleasesAfterLimit() {
        val s = ThinkingSplitter(holdLimit = 10)
        assertTrue(s.feed("12345").isEmpty)
        val o = s.feed("678901")
        assertEquals("12345678901", o.answer)
        assertEquals(" y más", s.feed(" y más").answer)
    }

    @Test fun nativeThoughtBlockIsHeldPastTheLimit() {
        val (a, t) = run("<|channel>", "thought\n", "x".repeat(200), "<channel|>", "Listo.", limit = 50)
        assertEquals("Listo.", a)
        assertEquals("x".repeat(200), t)
    }

    @Test fun strayMarkersAreRemovedFromAnswer() {
        val (a, _) = run("a<channel|>b", "<|channel>c<channel|>d")
        assertEquals("bcd", a)
    }

    @Test fun emptyThoughtGivesNullThought() {
        val s = ThinkingSplitter()
        val o = s.feed("<channel|>Respuesta")
        assertNull(o.thought)
        assertEquals("Respuesta", o.answer)
    }

    @Test fun appliesOnlyToGemma4() {
        assertTrue(ThinkingSplitter.appliesTo("gemma-4-E4B-PURE.gguf"))
        assertTrue(ThinkingSplitter.appliesTo("Gemma4-abliterated"))
        assertFalse(ThinkingSplitter.appliesTo("llama-3.2-3b.gguf"))
        assertFalse(ThinkingSplitter.appliesTo("gemma-3n-E2B"))
    }
}
