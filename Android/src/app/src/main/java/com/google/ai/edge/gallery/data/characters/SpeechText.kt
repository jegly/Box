package com.google.ai.edge.gallery.data.characters

/** Qué parte de un mensaje de roleplay se lee en voz alta. */
enum class SpeechScope {
    /** Todo el texto, sin los símbolos de formato. */
    ALL,

    /** Solo lo dicho entre comillas ("..." “...” «...»); las acciones (*...*) se omiten. */
    DIALOGUE_ONLY,

    /** Diálogo y narración, sin los asteriscos (igual que ALL, pero separa por bloques). */
    NARRATION_AND_DIALOGUE,
}

/**
 * Prepara un mensaje de roleplay para un motor de voz (TTS): elige qué se lee, quita el formato
 * markdown y lo parte en frases cortas para empezar a hablar mientras se sigue generando. Lógica
 * pura: no depende de Android ni de ningún motor concreto.
 */
object SpeechText {
    private val quoted = Regex("\"([^\"]+)\"|“([^”]+)”|«([^»]+)»")

    /** Texto que se debe leer según el alcance elegido (puede ser vacío). */
    fun extract(message: String, scope: SpeechScope): String {
        val text = message.replace("\r\n", "\n")
        val picked =
            when (scope) {
                SpeechScope.DIALOGUE_ONLY ->
                    quoted.findAll(text).joinToString(" ") { m ->
                        m.groupValues.drop(1).first { it.isNotEmpty() }
                    }
                SpeechScope.ALL, SpeechScope.NARRATION_AND_DIALOGUE -> text
            }
        return stripFormatting(picked)
    }

    /** Quita *, _, #, `, [], <...> y espacios repetidos. */
    fun stripFormatting(s: String): String =
        s.replace(Regex("<[^>]{1,40}>"), " ") // <|channel>, <channel|>, etiquetas
            .replace(Regex("[*_`#~]+"), "")
            .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1") // [texto](url) -> texto
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    /**
     * Parte el texto en frases de a lo sumo [maxLen] caracteres, cortando en . ! ? … o salto de
     * línea; si una frase es demasiado larga se corta en la coma o el espacio más cercano.
     */
    fun chunks(text: String, maxLen: Int = 220): List<String> {
        val limit = maxLen.coerceAtLeast(20)
        val out = mutableListOf<String>()
        val sentence = StringBuilder()
        fun flush() {
            val s = sentence.toString().trim()
            sentence.clear()
            if (s.isEmpty()) return
            var rest = s
            while (rest.length > limit) {
                var cut = rest.lastIndexOfAny(charArrayOf(',', ';', ':'), limit - 1)
                if (cut < limit / 3) cut = rest.lastIndexOf(' ', limit - 1)
                if (cut <= 0) cut = limit - 1
                out.add(rest.substring(0, cut + 1).trim())
                rest = rest.substring(cut + 1).trim()
            }
            if (rest.isNotEmpty()) out.add(rest)
        }
        for (ch in text) {
            sentence.append(ch)
            if (ch == '.' || ch == '!' || ch == '?' || ch == '…' || ch == '\n') flush()
        }
        flush()
        return out.filter { s -> s.any { it.isLetterOrDigit() } }
    }
}
