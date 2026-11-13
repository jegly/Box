package com.google.ai.edge.gallery.data.characters

/**
 * Qué modelo usa un chat de roleplay. Prioridad: el modelo del personaje, luego el predeterminado
 * de Roleplay. Un nombre que ya no esté disponible (borrado, no descargado) se ignora.
 * Devuelve null si no hay ninguno: la app tiene que pedir que elijas uno.
 */
object RoleplayModelResolver {
  fun resolve(characterModel: String?, defaultModel: String?, available: List<String>): String? =
    listOf(characterModel, defaultModel).firstOrNull { !it.isNullOrBlank() && it in available }
}
