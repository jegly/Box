package com.google.ai.edge.gallery.data.characters

/**
 * Separa el "pensamiento" de la respuesta en modelos Gemma 4 (GGUF). Algunos modelos (sobre todo
 * los abliterados) escriben su razonamiento en voz alta aunque el prompt traiga el canal de
 * pensamiento vacío, y lo cierran con el token `<channel|>`; sin este filtro el razonamiento sale
 * como si fuera la respuesta del personaje.
 *
 * El texto se retiene hasta ver `<channel|>`: lo anterior es el pensamiento (se entrega aparte,
 * para mostrarlo plegado) y lo posterior es la respuesta, que ya fluye en vivo. Si el modelo no
 * piensa (no aparece el marcador), el texto retenido se libera como respuesta al pasar `holdLimit`
 * caracteres o al terminar. Lógica pura: no depende de Android.
 */
class ThinkingSplitter(private val holdLimit: Int = 1500, private val thoughtLimit: Int = 12000) {

    /** Lo que hay que mostrar ahora. `thought` null = nada nuevo de pensamiento. */
    data class Out(val answer: String = "", val thought: String? = null) {
        val isEmpty: Boolean get() = answer.isEmpty() && thought == null
    }

    private var answering = false
    private val held = StringBuilder()

    fun feed(piece: String): Out {
        if (answering) return Out(answer = clean(piece))
        held.append(piece)
        val text = held.toString()
        val end = text.indexOf(CLOSE)
        if (end >= 0) {
            answering = true
            held.clear()
            val thought = clean(text.substring(0, end)).trim()
            val answer = clean(text.substring(end + CLOSE.length)).trimStart()
            return Out(answer = answer, thought = thought.ifEmpty { null })
        }
        val limit = if (text.contains(OPEN)) thoughtLimit else holdLimit
        if (text.length > limit) {
            answering = true
            held.clear()
            return Out(answer = clean(text))
        }
        return Out()
    }

    /** Al terminar: libera lo retenido (el modelo no usó el marcador). */
    fun finish(): Out {
        if (answering || held.isEmpty()) return Out()
        answering = true
        val text = held.toString()
        held.clear()
        return Out(answer = clean(text))
    }

    private fun clean(s: String): String =
        s.replace(OPEN, "").replace(CLOSE, "").replace(THOUGHT_HEADER, "")

    companion object {
        const val OPEN = "<|channel>"
        const val CLOSE = "<channel|>"
        private const val THOUGHT_HEADER = "thought\n"

        /** Solo Gemma 4 usa este canal; en otros modelos retener texto retrasaría el streaming. */
        fun appliesTo(modelName: String): Boolean {
            val n = modelName.lowercase()
            return n.contains("gemma-4") || n.contains("gemma4") || n.contains("gemma_4")
        }
    }
}
