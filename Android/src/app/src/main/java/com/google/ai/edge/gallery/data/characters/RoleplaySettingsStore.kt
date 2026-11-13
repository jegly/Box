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

  /** Si el personaje usa su memoria a largo plazo al abrir un chat nuevo (por defecto sí). */
  fun memoryEnabled(characterId: String): Boolean = prefs.getBoolean("memory_$characterId", true)

  fun setMemoryEnabled(characterId: String, enabled: Boolean) {
    prefs.edit().putBoolean("memory_$characterId", enabled).apply()
  }

  /** Cuántos recuerdos (los más recientes) se inyectan en el prompt. */
  fun memoryMaxInjections(): Int = prefs.getInt(KEY_MEMORY_MAX, 3).coerceIn(0, 20)

  fun setMemoryMaxInjections(n: Int) {
    prefs.edit().putInt(KEY_MEMORY_MAX, n.coerceIn(0, 20)).apply()
  }

  /** Cuántas entradas del lorebook (como máximo) se añaden al prompt por mensaje. */
  fun loreMaxEntries(): Int = prefs.getInt(KEY_LORE_MAX, 3).coerceIn(0, 20)

  fun setLoreMaxEntries(n: Int) {
    prefs.edit().putInt(KEY_LORE_MAX, n.coerceIn(0, 20)).apply()
  }

  /** Decodificación especulativa de LiteRT-LM (más rápida si el modelo la trae; por defecto no). */
  fun speculativeDecoding(): Boolean = prefs.getBoolean(KEY_SPECULATIVE, false)

  fun setSpeculativeDecoding(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SPECULATIVE, enabled).apply()
  }

  private companion object {
    const val KEY_SPECULATIVE = "speculative_decoding"
    const val KEY_LORE_MAX = "lore_max_entries"
    const val KEY_MEMORY_MAX = "memory_max_injections"
    const val KEY_MODEL = "default_model"
  }
}
