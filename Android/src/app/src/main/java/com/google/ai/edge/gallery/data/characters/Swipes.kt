package com.google.ai.edge.gallery.data.characters

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Swipes: varias respuestas posibles para el mismo mensaje del personaje (como SillyTavern).
 * El mensaje guarda todas las versiones en una lista JSON; el texto activo va en `content`.
 */
object Swipes {
  data class State(val texts: List<String>, val index: Int) {
    val count: Int get() = texts.size
    val active: String get() = texts.getOrElse(index) { "" }
  }

  private val json = Json { ignoreUnknownKeys = true }
  private val serializer = ListSerializer(String.serializer())

  /** Lista guardada -> estado. Sin lista guardada, el mensaje tiene una sola versión. */
  fun decode(alternativesJson: String, content: String, activeIndex: Int): State {
    val list =
      if (alternativesJson.isBlank()) emptyList()
      else runCatching { json.decodeFromString(serializer, alternativesJson) }.getOrDefault(emptyList())
    if (list.isEmpty()) return State(listOf(content), 0)
    return State(list, activeIndex.coerceIn(0, list.lastIndex))
  }

  /** Una sola versión no se guarda (la columna queda vacía). */
  fun encode(texts: List<String>): String =
    if (texts.size <= 1) "" else json.encodeToString(serializer, texts)

  /** Añade una versión nueva al final y la deja como activa. */
  fun withNew(state: State, text: String): State = State(state.texts + text, state.texts.size)

  /** Mueve la versión activa; se queda en los extremos. */
  fun move(state: State, delta: Int): State =
    state.copy(index = (state.index + delta).coerceIn(0, state.texts.lastIndex))

  /** Cambia el texto de la versión activa (editar mensaje). */
  fun replaceActive(state: State, text: String): State =
    state.copy(texts = state.texts.mapIndexed { i, t -> if (i == state.index) text else t })
}
