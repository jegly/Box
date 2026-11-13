package com.google.ai.edge.gallery.ui.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
                TextButton(onClick = onOpenSampler) { Text("Ajustes de muestreo (presets)") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}
