#ifndef SAMPLERSETTINGS_H
#define SAMPLERSETTINGS_H

// Ajustes de muestreo estilo Layla/SillyTavern. Se pasan desde Kotlin como un arreglo de floats
// (ver SamplerSettings.toFloatArray en Kotlin: el orden debe ser el mismo).
struct SamplerSettings {
    float temperature = 0.7f;
    float dynTempRange = 0.0f;     // > 0: temperatura dinamica (entropia), temp +/- rango
    int   topK = 40;               // 0 = desactivado
    float topP = 1.0f;             // 1 = desactivado
    float minP = 0.05f;            // 0 = desactivado
    float xtcProbability = 0.0f;   // 0 = desactivado
    float xtcThreshold = 0.1f;
    float repeatPenalty = 1.0f;    // 1 = desactivado
    int   repeatLastN = 64;
    float dryMultiplier = 0.0f;    // 0 = desactivado
    float dryBase = 1.75f;
    int   dryAllowedLength = 2;
    int   dryPenaltyLastN = -1;    // -1 = todo el contexto

    static constexpr int FLOAT_COUNT = 13;

    // Devuelve false si el arreglo no tiene el tamano esperado.
    bool fromArray(const float *a, int n) {
        if (a == nullptr || n < FLOAT_COUNT) return false;
        temperature = a[0];
        dynTempRange = a[1] < 0.0f ? 0.0f : a[1];
        topK = (int) a[2];
        topP = a[3];
        minP = a[4];
        xtcProbability = a[5];
        xtcThreshold = a[6];
        repeatPenalty = a[7];
        repeatLastN = (int) a[8];
        dryMultiplier = a[9];
        dryBase = a[10];
        dryAllowedLength = (int) a[11];
        dryPenaltyLastN = (int) a[12];
        if (temperature < 0.0f) temperature = 0.0f;
        return true;
    }
};

#endif
