package com.google.ai.edge.gallery.ui.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.google.ai.edge.gallery.data.characters.RoleplaySettingsStore

/**
 * Configuración de Roleplay: modelo predeterminado para los chats de personaje y acceso a los
 * ajustes de muestreo. Nada de esto afecta a los chats normales de Box.
 */
@Composable
fun RoleplaySettingsDialog(
    availableModels: List<String>,
    selectedModel: String?,
    onSelectModel: (String) -> Unit,
    onOpenSampler: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { RoleplaySettingsStore(context) }
    var loreMax by remember { mutableStateOf(store.loreMaxEntries().toString()) }
    var speculative by remember { mutableStateOf(store.speculativeDecoding()) }
    // null = aún no se ha comprobado (el modelo se analiza al cargarlo).
    val speculativeSupport =
      remember(selectedModel) { store.speculativeSupported(selectedModel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración de Roleplay") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Modelo para los chats de personaje", style = MaterialTheme.typography.titleSmall)
                if (availableModels.isEmpty()) {
                    Text(
                        "No hay modelos descargados. Descarga o importa uno desde Box y vuelve aquí.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                availableModels.forEach { name ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelectModel(name) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = name == selectedModel, onClick = { onSelectModel(name) })
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = loreMax,
                    onValueChange = { t ->
                        loreMax = t
                        t.toIntOrNull()?.let { store.setLoreMaxEntries(it) }
                    },
                    label = { Text("Máx. de entradas de lorebook por mensaje (0-20)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(
                        checked = speculative,
                        // Si ModelInfo ya dijo que el modelo no la soporta, no tiene sentido dejarlo
                        // activado en silencio.
                        enabled = speculativeSupport != false,
                        onCheckedChange = { speculative = it; store.setSpeculativeDecoding(it) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Decodificación especulativa (LiteRT)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when (speculativeSupport) {
                                false ->
                                    "El modelo seleccionado no la soporta " +
                                        "(hasSpeculativeDecodingSupport = false). El interruptor no tendrá efecto."
                                true ->
                                    "Este modelo sí la soporta. Se aplica al volver a cargar el modelo."
                                null ->
                                    "Acelera la respuesta si el modelo la soporta. Se comprueba al cargar " +
                                        "el modelo; si no la soporta, el interruptor se desactiva."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (speculativeSupport == false) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onOpenSampler) { Text("Ajustes de muestreo (presets)") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}
