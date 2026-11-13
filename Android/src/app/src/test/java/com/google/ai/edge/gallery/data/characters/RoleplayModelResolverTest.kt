package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoleplayModelResolverTest {
  private val models = listOf("A", "B", "C")

  @Test fun characterModelWins() = assertEquals("B", RoleplayModelResolver.resolve("B", "A", models))
  @Test fun fallsBackToDefault() = assertEquals("A", RoleplayModelResolver.resolve(null, "A", models))
  @Test fun blankCharacterModelFallsBack() = assertEquals("A", RoleplayModelResolver.resolve("", "A", models))
  @Test fun missingCharacterModelFallsBack() = assertEquals("A", RoleplayModelResolver.resolve("Z", "A", models))
  @Test fun missingDefaultGivesNull() = assertNull(RoleplayModelResolver.resolve(null, "Z", models))
  @Test fun nothingSetGivesNull() = assertNull(RoleplayModelResolver.resolve(null, null, models))
  @Test fun noModelsGivesNull() = assertNull(RoleplayModelResolver.resolve("A", "A", emptyList()))
}
