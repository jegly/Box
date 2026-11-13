package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipesTest {
  @Test fun noAlternativesIsSingleVersion() {
    val s = Swipes.decode("", "hola", 0)
    assertEquals(listOf("hola"), s.texts)
    assertEquals(0, s.index)
    assertEquals("", Swipes.encode(s.texts))
  }

  @Test fun addingNewMakesItActiveAndRoundTrips() {
    val s = Swipes.withNew(Swipes.decode("", "uno", 0), "dos")
    assertEquals(2, s.count)
    assertEquals(1, s.index)
    assertEquals("dos", s.active)
    val back = Swipes.decode(Swipes.encode(s.texts), s.active, s.index)
    assertEquals(s, back)
  }

  @Test fun textWithQuotesAndNewlinesSurvives() {
    val texts = listOf("a \"b\"\nc", "ñandú — ok")
    assertEquals(texts, Swipes.decode(Swipes.encode(texts), "", 0).texts)
  }

  @Test fun moveStopsAtEnds() {
    val s = Swipes.State(listOf("a", "b", "c"), 1)
    assertEquals(0, Swipes.move(s, -1).index)
    assertEquals(0, Swipes.move(s, -5).index)
    assertEquals(2, Swipes.move(s, 5).index)
  }

  @Test fun replaceActiveOnlyChangesActive() {
    val s = Swipes.State(listOf("a", "b"), 1)
    assertEquals(listOf("a", "B"), Swipes.replaceActive(s, "B").texts)
  }

  @Test fun brokenJsonFallsBackToContent() {
    val s = Swipes.decode("{nope", "texto", 3)
    assertEquals(listOf("texto"), s.texts)
  }

  @Test fun indexIsClamped() {
    val s = Swipes.decode(Swipes.encode(listOf("a", "b")), "b", 9)
    assertEquals(1, s.index)
  }
}
