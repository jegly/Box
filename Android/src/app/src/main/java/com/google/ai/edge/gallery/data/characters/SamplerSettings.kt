package com.google.ai.edge.gallery.data.characters

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ajustes de muestreo del modelo GGUF (estilo Layla/SillyTavern). El orden de [toFloatArray] debe
 * coincidir con SamplerSettings::fromArray en el código nativo.
 */
data class SamplerSettings(
  val temperature: Float = 0.82f,
  /** Temperatura dinámica: la temperatura sube/baja hasta este rango según la entropía. 0 = fija. */
  val dynTempRange: Float = 0.5f,
  val topK: Int = 40,
  val topP: Float = 1.0f,
  val minP: Float = 0.1f,
  /** XTC: probabilidad de excluir las opciones más probables. 0 = desactivado. */
  val xtcProbability: Float = 0.0f,
  val xtcThreshold: Float = 0.1f,
  val repeatPenalty: Float = 1.03f,
  val repeatLastN: Int = 64,
  /** DRY: penaliza repetir secuencias. Multiplicador 0 = desactivado. */
  val dryMultiplier: Float = 0.8f,
  val dryBase: Float = 1.75f,
  val dryAllowedLength: Int = 2,
  val dryPenaltyLastN: Int = -1,
) {
  fun toFloatArray(): FloatArray =
    floatArrayOf(
      temperature,
      dynTempRange,
      topK.toFloat(),
      topP,
      minP,
      xtcProbability,
      xtcThreshold,
      repeatPenalty,
      repeatLastN.toFloat(),
      dryMultiplier,
      dryBase,
      dryAllowedLength.toFloat(),
      dryPenaltyLastN.toFloat(),
    )

  /** Pone cada valor en un rango razonable, para que un número mal escrito no rompa la generación. */
  fun sanitized(): SamplerSettings =
    copy(
      temperature = temperature.coerceIn(0f, 5f),
      dynTempRange = dynTempRange.coerceIn(0f, 5f),
      topK = topK.coerceIn(0, 1000),
      topP = topP.coerceIn(0.01f, 1f),
      minP = minP.coerceIn(0f, 1f),
      xtcProbability = xtcProbability.coerceIn(0f, 1f),
      xtcThreshold = xtcThreshold.coerceIn(0f, 0.5f),
      repeatPenalty = repeatPenalty.coerceIn(1f, 2f),
      repeatLastN = repeatLastN.coerceIn(0, 4096),
      dryMultiplier = dryMultiplier.coerceIn(0f, 5f),
      dryBase = dryBase.coerceIn(1f, 4f),
      dryAllowedLength = dryAllowedLength.coerceIn(1, 64),
      dryPenaltyLastN = dryPenaltyLastN.coerceIn(-1, 8192),
    )

  companion object {
    const val FLOAT_COUNT = 13
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(settings: SamplerSettings): String =
      buildJsonObject {
          put("temperature", JsonPrimitive(settings.temperature))
          put("dynTempRange", JsonPrimitive(settings.dynTempRange))
          put("topK", JsonPrimitive(settings.topK))
          put("topP", JsonPrimitive(settings.topP))
          put("minP", JsonPrimitive(settings.minP))
          put("xtcProbability", JsonPrimitive(settings.xtcProbability))
          put("xtcThreshold", JsonPrimitive(settings.xtcThreshold))
          put("repeatPenalty", JsonPrimitive(settings.repeatPenalty))
          put("repeatLastN", JsonPrimitive(settings.repeatLastN))
          put("dryMultiplier", JsonPrimitive(settings.dryMultiplier))
          put("dryBase", JsonPrimitive(settings.dryBase))
          put("dryAllowedLength", JsonPrimitive(settings.dryAllowedLength))
          put("dryPenaltyLastN", JsonPrimitive(settings.dryPenaltyLastN))
        }
        .toString()

    /** Lee los valores guardados; los que falten se quedan con el valor por defecto. */
    fun decodeOrNull(text: String): SamplerSettings? =
      runCatching {
          val o: JsonObject = json.parseToJsonElement(text).jsonObject
          val d = SamplerSettings()
          fun f(k: String, def: Float) = o[k]?.jsonPrimitive?.float ?: def
          fun i(k: String, def: Int) = o[k]?.jsonPrimitive?.int ?: def
          SamplerSettings(
            temperature = f("temperature", d.temperature),
            dynTempRange = f("dynTempRange", d.dynTempRange),
            topK = i("topK", d.topK),
            topP = f("topP", d.topP),
            minP = f("minP", d.minP),
            xtcProbability = f("xtcProbability", d.xtcProbability),
            xtcThreshold = f("xtcThreshold", d.xtcThreshold),
            repeatPenalty = f("repeatPenalty", d.repeatPenalty),
            repeatLastN = i("repeatLastN", d.repeatLastN),
            dryMultiplier = f("dryMultiplier", d.dryMultiplier),
            dryBase = f("dryBase", d.dryBase),
            dryAllowedLength = i("dryAllowedLength", d.dryAllowedLength),
            dryPenaltyLastN = i("dryPenaltyLastN", d.dryPenaltyLastN),
          )
            .sanitized()
        }
        .getOrNull()
  }
}

/** Modos de muestreo. Default = tus valores de Layla; Creative y Precise son variantes. */
enum class SamplerMode(val id: String, val label: String) {
  DEFAULT("default", "Default"),
  CREATIVE("creative", "Creative"),
  PRECISE("precise", "Precise"),
  CUSTOM("custom", "Custom");

  companion object {
    fun fromId(id: String?): SamplerMode = entries.firstOrNull { it.id == id } ?: DEFAULT
  }
}

object SamplerPresets {
  val DEFAULT = SamplerSettings()

  val CREATIVE =
    SamplerSettings(
      temperature = 1.0f,
      dynTempRange = 0.7f,
      topK = 60,
      topP = 1.0f,
      minP = 0.06f,
      xtcProbability = 0.3f,
      xtcThreshold = 0.1f,
      repeatPenalty = 1.0f,
      dryMultiplier = 0.8f,
    )

  val PRECISE =
    SamplerSettings(
      temperature = 0.55f,
      dynTempRange = 0.0f,
      topK = 30,
      topP = 0.9f,
      minP = 0.05f,
      xtcProbability = 0.0f,
      repeatPenalty = 1.05f,
      dryMultiplier = 0.0f,
    )

  /** Valores de un modo; para CUSTOM se usan los guardados por el usuario. */
  fun resolve(mode: SamplerMode, custom: SamplerSettings?): SamplerSettings =
    when (mode) {
      SamplerMode.DEFAULT -> DEFAULT
      SamplerMode.CREATIVE -> CREATIVE
      SamplerMode.PRECISE -> PRECISE
      SamplerMode.CUSTOM -> custom ?: DEFAULT
    }
}
