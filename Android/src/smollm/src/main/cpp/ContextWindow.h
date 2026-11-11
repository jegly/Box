#ifndef CONTEXTWINDOW_H
#define CONTEXTWINDOW_H

// Logica pura (sin llama.cpp) de la ventana deslizante de contexto, para poder probarla aparte.
//
// Idea (igual que Layla): cuando el prompt pasa de un umbral alto del contexto (p. ej. 75 %),
// se borran de golpe los mensajes mas viejos hasta bajar a un umbral bajo (p. ej. 45 %).
// Hacerlo en bloque, y no un mensaje por turno, evita reprocesar el prompt en cada respuesta.
// Siempre se conserva el prompt de sistema (el personaje), su saludo inicial y el ultimo mensaje.

#include <algorithm>
#include <functional>
#include <string>
#include <vector>

namespace ctxwin {

// Cuantos mensajes del principio nunca se borran: el de sistema y, si le sigue, el saludo del
// asistente (primer mensaje del personaje).
inline size_t protectedPrefix(const std::vector<std::string> &roles, const std::string &assistantRole) {
    size_t keep = 0;
    if (!roles.empty() && roles[0] == "system") keep = 1;
    if (keep < roles.size() && roles[keep] == assistantRole) keep++;
    // el ultimo mensaje (la pregunta nueva) se protege aparte en computeDrop
    return keep;
}

// Devuelve cuantos mensajes (a partir de `keep`) hay que borrar. Siempre en pares usuario/asistente
// para no romper la alternancia de roles, y nunca el ultimo mensaje.
// tokensAfterDrop(d) = tokens del prompt si se borraran los primeros d mensajes tras `keep`.
inline size_t computeDrop(size_t total, size_t keep, int high, int low,
                          const std::function<int(size_t)> &tokensAfterDrop) {
    if (tokensAfterDrop(0) <= high) return 0;
    const size_t maxDrop = total > keep + 1 ? total - keep - 1 : 0;
    size_t drop = 0;
    while (drop < maxDrop && tokensAfterDrop(drop) > low) {
        drop = std::min(drop + 2, maxDrop);
    }
    return drop;
}

// Cuantos tokens del cache de KV se pueden reutilizar: el prefijo comun entre lo que ya esta
// procesado y el prompt nuevo. Siempre se deja al menos 1 token por procesar (hacen falta
// logits para generar).
inline size_t reusablePrefix(const std::vector<int> &cached, const std::vector<int> &prompt) {
    size_t common = 0;
    const size_t limit = std::min(cached.size(), prompt.size());
    while (common < limit && cached[common] == prompt[common]) common++;
    if (common == prompt.size() && common > 0) common--;
    return common;
}

} // namespace ctxwin

#endif // CONTEXTWINDOW_H
