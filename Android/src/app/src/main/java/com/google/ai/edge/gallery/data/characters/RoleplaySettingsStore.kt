package com.google.ai.edge.gallery.data.characters

import android.content.Context

/** Ajustes propios de Roleplay (no afectan a los chats normales de Box). */
class RoleplaySettingsStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("roleplay_settings", Context.MODE_PRIVATE)

  /** Nombre del modelo con el que se abren los chats de personaje, o null si no se ha elegido. */
  fun defaultModel(): String? = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }

  fun setDefaultModel(name: String?) {
    prefs.edit().putString(KEY_MODEL, name ?: "").apply()
  }

  private companion object {
    const val KEY_MODEL = "default_model"
  }
}
