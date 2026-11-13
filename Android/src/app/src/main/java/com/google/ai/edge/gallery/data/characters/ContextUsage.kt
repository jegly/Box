package com.google.ai.edge.gallery.data.characters

import kotlin.math.roundToInt

/**
 * Cuánto del contexto ocupa lo que el motor realmente ve ahora: prompt del personaje + saludo +
 * mensajes que siguen dentro de la ventana deslizante. Los mensajes ya borrados por la ventana
 * no cuentan (antes el contador sumaba TODO el chat y se quedaba pegado en el máximo, en rojo,
 * aunque el motor trabajara bien). Lógica pura: no depende de Android.
 */
data class ContextUsage(
    val usedTokens: Int,
    val maxTokens: Int,
    /** La ventana deslizante está recortando mensajes antiguos (no es un error). */
    val windowActive: Boolean,
) {
    val fraction: Float
        get() = if (maxTokens <= 0) 0f else (usedTokens.toFloat() / maxTokens).coerceIn(0f, 1f)

    val percent: Int
        get() = (fraction * 100f).roundToInt()

    companion object {
        /**
         * @param texts todos los mensajes de texto del chat, en orden.
         * @param keep cuántos mensajes iniciales nunca se borran (el saludo = 1).
         * @param dropped cuántos mensajes (después de `keep`) ya salieron de la ventana.
         */
        fun compute(
            promptText: String,
            texts: List<String>,
            keep: Int,
            dropped: Int,
            maxTokens: Int,
        ): ContextUsage {
            val k = keep.coerceIn(0, texts.size)
            val d = dropped.coerceIn(0, texts.size - k)
            val visible = texts.take(k) + texts.drop(k + d)
            val raw =
                (if (promptText.isBlank()) 0 else ContextWindowPlanner.estimateTokens(promptText)) +
                    visible.sumOf { ContextWindowPlanner.estimateTokens(it) }
            val max = if (maxTokens > 0) maxTokens else 1
            return ContextUsage(
                usedTokens = raw.coerceAtMost(max),
                maxTokens = max,
                windowActive = d > 0 || raw > max,
            )
        }
    }
}
