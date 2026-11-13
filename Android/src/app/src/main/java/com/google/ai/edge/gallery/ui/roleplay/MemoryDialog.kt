package com.google.ai.edge.gallery.ui.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.characters.MemoryPromptBuilder
import com.google.ai.edge.gallery.data.characters.RoleplaySettingsStore
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter

/**
 * Memoria a largo plazo de un personaje: recuerdos (por ejemplo, resúmenes de charlas anteriores)
 * que se añaden al prompt cuando abres un chat nuevo con él.
 */
@Composable
fun MemoryDialog(
    character: RoleplayCharacter,
    viewModel: RoleplayViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { RoleplaySettingsStore(context) }
    val memories by remember(character.id) { viewModel.memories(character.id) }.collectAsState(emptyList())
    var enabled by remember { mutableStateOf(store.memoryEnabled(character.id)) }
    var maxText by remember { mutableStateOf(store.memoryMaxInjections().toString()) }
    var editing by remember { mutableStateOf<CharacterMemory?>(null) }
    var editText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Memoria de ${character.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Al abrir un chat nuevo, los recuerdos más recientes se añaden al prompt para que el personaje recuerde charlas anteriores.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Usar memoria con este personaje", Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = {
                        enabled = it
                        store.setMemoryEnabled(character.id, it)
                    })
                }
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { t ->
                        maxText = t
                        t.toIntOrNull()?.let { store.setMemoryMaxInjections(it) }
                    },
                    label = { Text("Máx. de recuerdos por chat (0-20)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { editText = ""; editing = CharacterMemory(characterId = character.id, content = "") }) {
                    Text("+ Añadir recuerdo")
                }
                if (memories.isEmpty()) {
                    Text("Todavía no hay recuerdos.", style = MaterialTheme.typography.bodySmall)
                }
                memories.forEach { m ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(MemoryPromptBuilder.timeAgo(m.createdAt, now), style = MaterialTheme.typography.labelSmall)
                                Text(m.content, style = MaterialTheme.typography.bodyMedium, maxLines = 5)
                            }
                            IconButton(onClick = { editText = m.content; editing = m }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Editar recuerdo")
                            }
                            IconButton(onClick = { viewModel.deleteMemory(m) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Borrar recuerdo")
                            }
                        }
                    }
                }
                if (memories.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("Borrar todos los recuerdos") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )

    editing?.let { target ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (target.content.isEmpty()) "Nuevo recuerdo" else "Editar recuerdo") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 12,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveMemory(target.copy(content = editText))
                    editing = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todos los recuerdos?") },
            text = { Text("Se borran los recuerdos de ${character.name}. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearMemories(character.id); confirmClear = false }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}
