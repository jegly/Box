package com.google.ai.edge.gallery.ui.roleplay

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.characters.CharacterSearch
import com.google.ai.edge.gallery.data.characters.SearchableCharacter
import com.google.ai.edge.gallery.data.characters.toStringList
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import java.io.File

/**
 * Biblioteca de personajes: cuadrícula con portada, búsqueda y filtro por etiquetas (como Layla).
 * Tocar un personaje abre el chat; el menú de los tres puntos tiene el resto de acciones.
 */
@Composable
fun CharacterLibrary(
    characters: List<RoleplayCharacter>,
    onChat: (RoleplayCharacter) -> Unit,
    onEdit: (RoleplayCharacter) -> Unit,
    onShare: (RoleplayCharacter) -> Unit,
    onDelete: (RoleplayCharacter) -> Unit,
    onMemories: (RoleplayCharacter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<String?>(null) }

    val searchable = remember(characters) {
        characters.map { SearchableCharacter(it.id, it.name, it.description, it.tagsJson.toStringList()) }
    }
    val tags = remember(searchable) { CharacterSearch.allTags(searchable) }
    // Si la etiqueta elegida ya no existe (por ejemplo, se borró el personaje), se quita el filtro.
    val activeTag = tag?.takeIf { t -> tags.any { it.equals(t, ignoreCase = true) } }
    val visibleIds = remember(searchable, query, activeTag) {
        CharacterSearch.filterIds(searchable, query, activeTag).toSet()
    }
    val visible = characters.filter { it.id in visibleIds }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar personajes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (tags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems(tags) { t ->
                    FilterChip(
                        selected = activeTag.equals(t, ignoreCase = true),
                        onClick = { tag = if (activeTag.equals(t, ignoreCase = true)) null else t },
                        label = { Text(t) },
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Ningún personaje coincide", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                // Margen abajo para que el botón + no tape la última fila.
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible, key = { it.id }) { c ->
                    CharacterTile(
                        character = c,
                        onChat = { onChat(c) },
                        onEdit = { onEdit(c) },
                        onShare = { onShare(c) },
                        onDelete = { onDelete(c) },
                        onMemories = { onMemories(c) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterTile(
    character: RoleplayCharacter,
    onChat: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onMemories: () -> Unit,
) {
    val cover = remember(character.avatarPath) {
        if (character.avatarPath.isEmpty()) null
        else try {
            val f = File(character.avatarPath)
            if (f.exists()) {
                // Se reduce al leer para no gastar memoria con PNG enormes.
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
            } else null
        } catch (_: Exception) {
            null
        }
    }
    var menu by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().clickable(onClick = onChat)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f), contentAlignment = Alignment.Center) {
                if (cover != null) {
                    Image(cover, contentDescription = character.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(character.name.take(1).uppercase(), style = MaterialTheme.typography.displayMedium)
                }
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Más acciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Memoria") }, leadingIcon = { Icon(Icons.Rounded.Psychology, null) }, onClick = { menu = false; onMemories() })
                        DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Compartir") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; onShare() })
                        DropdownMenuItem(text = { Text("Borrar") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(character.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = character.description.ifBlank { character.personality }.ifBlank { "Sin descripción" }
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
