package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplerSettingsTest {
  @Test fun floatArrayOrderMatchesNative() {
    val a = SamplerSettings().toFloatArray()
    assertEquals(SamplerSettings.FLOAT_COUNT, a.size)
    assertEquals(0.82f, a[0], 0f) // temperatura
    assertEquals(0.5f, a[1], 0f) // rango de temp. dinámica
    assertEquals(40f, a[2], 0f) // top-k
    assertEquals(1f, a[3], 0f) // top-p
    assertEquals(0.1f, a[4], 0f) // min-p
    assertEquals(1.03f, a[7], 0f) // penalización de repetición
    assertEquals(0.8f, a[9], 0f) // DRY multiplicador
    assertEquals(1.75f, a[10], 0f) // DRY base
    assertEquals(2f, a[11], 0f) // DRY longitud permitida
  }

  @Test fun defaultMatchesLaylaValues() {
    val d = SamplerPresets.DEFAULT
    assertEquals(0.82f, d.temperature, 0f)
    assertEquals(40, d.topK)
    assertEquals(0.1f, d.minP, 0f)
    assertEquals(1.03f, d.repeatPenalty, 0f)
  }

  @Test fun jsonRoundTrip() {
    val s = SamplerPresets.CREATIVE
    assertEquals(s, SamplerSettings.decodeOrNull(SamplerSettings.encode(s)))
  }

  @Test fun brokenJsonGivesNull() {
    assertNull(SamplerSettings.decodeOrNull("{no"))
  }

  @Test fun sanitizeClampsBadValues() {
    val s = SamplerSettings(temperature = -3f, topP = 0f, minP = 9f, repeatPenalty = 0.2f, dryAllowedLength = 0, topK = -5).sanitized()
    assertEquals(0f, s.temperature, 0f)
    assertTrue(s.topP > 0f)
    assertEquals(1f, s.minP, 0f)
    assertEquals(1f, s.repeatPenalty, 0f)
    assertEquals(1, s.dryAllowedLength)
    assertEquals(0, s.topK)
  }

  @Test fun resolveUsesCustomOnlyForCustomMode() {
    val custom = SamplerSettings(temperature = 0.3f)
    assertEquals(custom, SamplerPresets.resolve(SamplerMode.CUSTOM, custom))
    assertEquals(SamplerPresets.DEFAULT, SamplerPresets.resolve(SamplerMode.CUSTOM, null))
    assertEquals(SamplerPresets.PRECISE, SamplerPresets.resolve(SamplerMode.PRECISE, custom))
  }

  @Test fun unknownModeIdFallsBackToDefault() {
    assertEquals(SamplerMode.DEFAULT, SamplerMode.fromId("zzz"))
    assertEquals(SamplerMode.CREATIVE, SamplerMode.fromId("creative"))
  }

  @Test fun presetsDiffer() {
    assertTrue(SamplerPresets.CREATIVE.temperature > SamplerPresets.PRECISE.temperature)
    assertEquals(0f, SamplerPresets.PRECISE.dryMultiplier, 0f)
  }
}
