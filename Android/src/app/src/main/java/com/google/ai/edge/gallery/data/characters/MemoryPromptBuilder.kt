package com.google.ai.edge.gallery.data.characters

/** Un recuerdo ya cargado, sin depender de la base de datos. */
data class MemoryEntry(val content: String, val createdAt: Long)

/**
 * Arma el bloque "{char} remembers previously" que se añade al prompt del personaje.
 * Estilo Layla: texto plano, cada recuerdo con cuánto hace que ocurrió.
 */
object MemoryPromptBuilder {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** "just now", "5 minutes ago", "2 days ago", "3 weeks ago", "1 month ago"... */
    fun timeAgo(createdAt: Long, now: Long): String {
        val diff = (now - createdAt).coerceAtLeast(0)
        fun plural(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
        return when {
            diff < MINUTE -> "just now"
            diff < HOUR -> plural(diff / MINUTE, "minute")
            diff < DAY -> plural(diff / HOUR, "hour")
            diff < 7 * DAY -> plural(diff / DAY, "day")
            diff < 30 * DAY -> plural(diff / (7 * DAY), "week")
            diff < 365 * DAY -> plural(diff / (30 * DAY), "month")
            else -> plural(diff / (365 * DAY), "year")
        }
    }

    /**
     * Toma los [max] recuerdos más recientes (de más viejo a más nuevo). Devuelve "" si no hay
     * ninguno o si [max] es 0.
     */
    fun build(
        charName: String,
        memories: List<MemoryEntry>,
        max: Int,
        now: Long,
        showTime: Boolean = true,
    ): String {
        val picked = memories
            .filter { it.content.isNotBlank() }
            .sortedByDescending { it.createdAt }
            .take(max.coerceAtLeast(0))
            .sortedBy { it.createdAt }
        if (picked.isEmpty()) return ""
        val lines = picked.joinToString("\n") {
            val text = it.content.trim()
            if (showTime) "[${timeAgo(it.createdAt, now)}] $text" else text
        }
        return "$charName remembers previously:\n$lines"
    }
}
