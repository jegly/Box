package com.google.ai.edge.gallery.ui.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.google.ai.edge.gallery.data.characters.SamplerMode
import com.google.ai.edge.gallery.data.characters.SamplerPresets
import com.google.ai.edge.gallery.data.characters.SamplerSettings
import com.google.ai.edge.gallery.data.characters.SamplerSettingsStore

/**
 * Ajustes de muestreo (solo modelos GGUF). Los modos Default/Creative/Precise traen valores fijos;
 * al tocar cualquier número se pasa a Custom y esos valores se guardan.
 */
@Composable
fun SamplerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SamplerSettingsStore(context) }
    var mode by remember { mutableStateOf(store.mode()) }
    var custom by remember { mutableStateOf(store.custom()) }
    val shown = SamplerPresets.resolve(mode, custom)

    // Cambiar un número convierte el modo actual en Custom, partiendo de sus valores.
    fun edit(change: (SamplerSettings) -> SamplerSettings) {
        custom = change(shown)
        mode = SamplerMode.CUSTOM
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Muestreo (GGUF)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Controla qué tan creativa o repetitiva es la respuesta. Los cambios se aplican en el siguiente mensaje. LiteRT usa sus propios ajustes del modelo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SamplerMode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                    }
                }
                NumField("Temperatura", shown.temperature) { v -> edit { it.copy(temperature = v) } }
                NumField("Rango de temp. dinámica (0 = fija)", shown.dynTempRange) { v -> edit { it.copy(dynTempRange = v) } }
                IntField("Top-K (0 = off)", shown.topK) { v -> edit { it.copy(topK = v) } }
                NumField("Top-P (1 = off)", shown.topP) { v -> edit { it.copy(topP = v) } }
                NumField("Min-P (0 = off)", shown.minP) { v -> edit { it.copy(minP = v) } }
                NumField("XTC probabilidad (0 = off)", shown.xtcProbability) { v -> edit { it.copy(xtcProbability = v) } }
                NumField("XTC umbral", shown.xtcThreshold) { v -> edit { it.copy(xtcThreshold = v) } }
                NumField("Penalización de repetición (1 = off)", shown.repeatPenalty) { v -> edit { it.copy(repeatPenalty = v) } }
                IntField("Repetición: últimos N tokens", shown.repeatLastN) { v -> edit { it.copy(repeatLastN = v) } }
                NumField("DRY multiplicador (0 = off)", shown.dryMultiplier) { v -> edit { it.copy(dryMultiplier = v) } }
                NumField("DRY base", shown.dryBase) { v -> edit { it.copy(dryBase = v) } }
                IntField("DRY longitud permitida", shown.dryAllowedLength) { v -> edit { it.copy(dryAllowedLength = v) } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                store.save(mode, custom)
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun NumField(label: String, value: Float, onValue: (Float) -> Unit) {
    // El texto se guarda aparte para poder escribir "0." sin que se reescriba a cada tecla.
    var text by remember(value) { mutableStateOf(trim(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            t.replace(',', '.').toFloatOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

@Composable
private fun IntField(label: String, value: Int, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            t.toIntOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

private fun trim(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
