package com.google.ai.edge.gallery.data.characters

/** Lo mínimo de un personaje que hace falta para buscar y filtrar. */
data class SearchableCharacter(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>,
)

/** Búsqueda y filtro por etiqueta de la biblioteca de personajes. Lógica pura con pruebas. */
object CharacterSearch {
    /** Etiquetas de todos los personajes, sin repetir (ignora mayúsculas) y ordenadas. */
    fun allTags(items: List<SearchableCharacter>): List<String> =
        items.flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    /**
     * Ids que cumplen la búsqueda y la etiqueta, en el mismo orden de entrada. La búsqueda mira
     * nombre, descripción y etiquetas, sin importar mayúsculas; cada palabra debe aparecer.
     * [tag] null o vacío = sin filtro de etiqueta.
     */
    fun filterIds(items: List<SearchableCharacter>, query: String, tag: String?): List<String> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val wantedTag = tag?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return items.filter { c ->
            val tagOk = wantedTag == null || c.tags.any { it.trim().lowercase() == wantedTag }
            if (!tagOk) return@filter false
            val haystack = (listOf(c.name, c.description) + c.tags).joinToString(" ").lowercase()
            words.all { haystack.contains(it) }
        }.map { it.id }
    }
}
