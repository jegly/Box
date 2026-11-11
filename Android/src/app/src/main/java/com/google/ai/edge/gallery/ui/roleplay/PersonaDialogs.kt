package com.google.ai.edge.gallery.ui.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.local.entities.UserPersona

/** Lista de personas: elegir la activa, crear, editar y borrar. */
@Composable
fun PersonaManagerDialog(
    personas: List<UserPersona>,
    onDismiss: () -> Unit,
    onSelect: (UserPersona) -> Unit,
    onSave: (UserPersona) -> Unit,
    onDelete: (UserPersona) -> Unit,
) {
    // null = no se está editando; una persona nueva se crea con nombre vacío.
    var editing by remember { mutableStateOf<UserPersona?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personas") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Tu perfil en el chat. Su nombre reemplaza a {{user}} y su descripción se la damos al modelo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (personas.isEmpty()) {
                    Text(
                        "Aún no tienes ninguna. Toca «Nueva persona».",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                personas.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = p.isActive, onClick = { onSelect(p) })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.description.isNotBlank()) {
                                Text(
                                    p.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { editing = p }) { Icon(Icons.Rounded.Edit, contentDescription = "Editar") }
                        IconButton(onClick = { onDelete(p) }) { Icon(Icons.Rounded.Delete, contentDescription = "Borrar") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
        dismissButton = { TextButton(onClick = { editing = UserPersona(name = "") }) { Text("Nueva persona") } },
    )

    editing?.let { current ->
        PersonaEditorDialog(
            persona = current,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
        )
    }
}

@Composable
private fun PersonaEditorDialog(
    persona: UserPersona,
    onDismiss: () -> Unit,
    onSave: (UserPersona) -> Unit,
) {
    var name by remember(persona.id) { mutableStateOf(persona.name) }
    var description by remember(persona.id) { mutableStateOf(persona.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (persona.name.isBlank()) "Nueva persona" else "Editar persona") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (quién eres en la historia)") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(persona.copy(name = name.trim(), description = description)) },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
