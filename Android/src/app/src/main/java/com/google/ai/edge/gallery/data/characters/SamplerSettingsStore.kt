package com.google.ai.edge.gallery.data.characters

import android.content.Context

/** Guarda el modo de muestreo elegido y los valores personalizados (preferencias de la app). */
class SamplerSettingsStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("roleplay_sampler", Context.MODE_PRIVATE)

  fun mode(): SamplerMode = SamplerMode.fromId(prefs.getString(KEY_MODE, null))

  fun custom(): SamplerSettings? = prefs.getString(KEY_CUSTOM, null)?.let { SamplerSettings.decodeOrNull(it) }

  /** Ajustes que se usan ahora mismo. */
  fun current(): SamplerSettings = SamplerPresets.resolve(mode(), custom()).sanitized()

  fun save(mode: SamplerMode, custom: SamplerSettings?) {
    prefs.edit().putString(KEY_MODE, mode.id).apply {
      if (custom != null) putString(KEY_CUSTOM, SamplerSettings.encode(custom.sanitized()))
    }.apply()
  }

  private companion object {
    const val KEY_MODE = "mode"
    const val KEY_CUSTOM = "custom"
  }
}
