#ifndef CHATFORMATS_H
#define CHATFORMATS_H

// Formatos de chat que el API "legacy" de llama.cpp (llama_chat_apply_template) no reconoce.
// Logica pura, sin llama.cpp, para poder probarla aparte.

#include <string>
#include <utility>
#include <vector>

namespace chatfmt {

inline std::string trimmed(const std::string &s) {
    size_t a = 0, b = s.size();
    while (a < b && (s[a] == ' ' || s[a] == '\n' || s[a] == '\r' || s[a] == '\t')) a++;
    while (b > a && (s[b - 1] == ' ' || s[b - 1] == '\n' || s[b - 1] == '\r' || s[b - 1] == '\t')) b--;
    return s.substr(a, b - a);
}

// Gemma 4 usa <|turn>rol ... <turn|> (Gemma 1-3 usaba <start_of_turn>). Sin este formato, el
// fallback "rol: texto" degrada mucho al modelo.
//
// Forma, tomada del prompt sin procesar de Layla:
//   <|turn>system\n{sistema}<turn|>\n
//   <|turn>user\n{mensaje}<turn|>\n
//   <|turn>model\n{respuesta}<turn|>\n
//   <|turn>model\n<|channel>thought\n<channel|>      <- el modelo empieza a escribir aqui
// (el canal de "thought" vacio le dice que no piense en voz alta).
// Si el primer mensaje tras el sistema es del asistente (el saludo del personaje), se coloca
// antes un turno de usuario vacio, como hace Layla, para que los roles alternen.
// El BOS lo agrega el tokenizador; no se escribe aqui.
//
// `enableThinking` replica la plantilla canonica de Gemma 4: con thinking activado se escribe el
// token <|think|> al principio del primer turno de sistema y se deja abierto el canal de
// pensamiento, para que el modelo razone antes de responder. Con thinking desactivado no se
// escribe <|think|> y el canal se cierra vacio (respuesta directa).
inline std::string formatGemma4(const std::vector<std::pair<std::string, std::string>> &messages,
                                bool enableThinking) {
    std::string out;
    bool lastWasUser = false;
    bool anyTurn = false;
    bool thinkInjected = false;
    for (const auto &m: messages) {
        const std::string &role = m.first;
        const std::string content = trimmed(m.second);
        if (role == "system") {
            out += "<|turn>system\n";
            if (enableThinking && !thinkInjected) {
                out += "<|think|>\n";
                thinkInjected = true;
            }
            out += content + "<turn|>\n";
        } else if (role == "user") {
            out += "<|turn>user\n" + content + "<turn|>\n";
            lastWasUser = true;
            anyTurn = true;
        } else { // "model" o "assistant"
            if (!lastWasUser) {
                out += "<|turn>user\n<turn|>\n";
            }
            out += "<|turn>model\n" + content + "<turn|>\n";
            lastWasUser = false;
            anyTurn = true;
        }
    }
    (void) anyTurn;
    // Si no habia turno de sistema, se crea uno vacio para poder inyectar <|think|>.
    if (enableThinking && !thinkInjected) {
        out = "<|turn>system\n<|think|>\n<turn|>\n" + out;
    }
    out += "<|turn>model\n";
    out += enableThinking ? "<|channel>thought\n" : "<|channel>thought\n<channel|>";
    return out;
}

} // namespace chatfmt

#endif // CHATFORMATS_H
