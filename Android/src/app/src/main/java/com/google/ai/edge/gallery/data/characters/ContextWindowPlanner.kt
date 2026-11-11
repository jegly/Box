package com.google.ai.edge.gallery.data.characters

import kotlin.math.max

/**
 * Ventana deslizante de contexto para motores que guardan su propio historial (LiteRT-LM).
 * Misma idea que Layla: al pasar de un umbral alto del contexto se borran de golpe los mensajes
 * más viejos hasta bajar a un umbral bajo. Siempre se conservan el prompt del personaje, su
 * saludo (primer mensaje) y el mensaje nuevo. Lógica pura: no depende de Android.
 */
object ContextWindowPlanner {
    const val HIGH = 0.70f
    const val LOW = 0.45f

    /** Estimación conservadora (~3 caracteres por token en español, más el costo de cada turno). */
    fun estimateTokens(text: String): Int = (text.length + 2) / 3 + 6

    /**
     * Cuántos mensajes del historial hay que borrar (siempre en pares usuario/asistente), contando
     * desde `keep` (los primeros `keep` mensajes nunca se borran). 0 = no hace falta recortar.
     *
     * @param history mensajes anteriores, sin el que se envía ahora.
     */
    fun planDrop(
        systemTokens: Int,
        history: List<String>,
        inputTokens: Int,
        keep: Int,
        maxTokens: Int,
        high: Float = HIGH,
        low: Float = LOW,
    ): Int {
        val sizes = history.map { estimateTokens(it) }
        var total = systemTokens + sizes.sum() + inputTokens
        if (total <= (maxTokens * high).toInt()) return 0
        val protectedCount = keep.coerceIn(0, history.size)
        var drop = 0
        val lowLimit = (maxTokens * low).toInt()
        while (protectedCount + drop < history.size && total > lowLimit) {
            val step = if (protectedCount + drop + 2 <= history.size) 2 else 1
            for (i in 0 until step) total -= sizes[protectedCount + drop + i]
            drop += step
        }
        return max(0, drop)
    }
}
