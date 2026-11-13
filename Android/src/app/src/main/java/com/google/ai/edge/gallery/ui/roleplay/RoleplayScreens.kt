package com.google.ai.edge.gallery.ui.roleplay

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.characters.toJsonArrayString
import com.google.ai.edge.gallery.data.characters.toStringList
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import java.io.File

/** Lista de personajes: importar tarjeta (.json/.png), crear, editar, compartir, borrar y chatear. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleplayScreen(
    navigateUp: () -> Unit,
    /** Nombres de los modelos descargados que se pueden usar para chatear. */
    availableModels: List<String>,
    onOpenChat: (modelName: String, conversationId: String) -> Unit,
    onEditCharacter: (id: String) -> Unit,
    viewModel: RoleplayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val characters by viewModel.characters.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var toDelete by remember { mutableStateOf<RoleplayCharacter?>(null) }
    val personas by viewModel.personas.collectAsState()
    var showPersonas by remember { mutableStateOf(false) }
    var showSampler by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var memoryFor by remember { mutableStateOf<RoleplayCharacter?>(null) }
    val settingsStore = remember { com.google.ai.edge.gallery.data.characters.RoleplaySettingsStore(context) }
    var defaultModel by remember { mutableStateOf(settingsStore.defaultModel()) }
    // Modelo con el que se abren los chats: el predeterminado de Roleplay (null = hay que elegir).
    val modelName = com.google.ai.edge.gallery.data.characters.RoleplayModelResolver.resolve(
        characterModel = null,
        defaultModel = defaultModel,
        available = availableModels,
    )

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Roleplay") },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Configuración de Roleplay")
                    }
                    IconButton(onClick = {
                        // Las tarjetas suelen ser .json o .png; algunos gestores no reconocen
                        // el tipo, así que se aceptan todos.
                        picker.launch(arrayOf("application/json", "image/png", "text/plain", "*/*"))
                    }) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Importar tarjeta")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditCharacter("new") }) {
                Icon(Icons.Rounded.Add, contentDescription = "Crear personaje")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
        PersonaBar(activeName = personas.firstOrNull { it.isActive }?.name, onClick = { showPersonas = true })
        if (characters.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Aún no tienes personajes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Importa una tarjeta de SillyTavern o Layla (.json o .png) con el icono de carpeta, o toca + para crear una.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(characters, key = { it.id }) { c ->
                    CharacterRow(
                        character = c,
                        onChat = {
                            // Prioridad: modelo del personaje, luego el predeterminado de Roleplay.
                            val chatModel = com.google.ai.edge.gallery.data.characters.RoleplayModelResolver.resolve(
                                characterModel = c.modelName,
                                defaultModel = defaultModel,
                                available = availableModels,
                            )
                            if (chatModel != null) {
                                viewModel.startChat(c, chatModel) { convId -> onOpenChat(chatModel, convId) }
                            } else {
                                // Todavía no hay modelo elegido: se abre la configuración para elegir uno.
                                showSettings = true
                            }
                        },
                        onEdit = { onEditCharacter(c.id) },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, "${c.name}.json")
                                putExtra(Intent.EXTRA_TEXT, viewModel.exportJson(c))
                            }
                            context.startActivity(Intent.createChooser(send, "Compartir personaje"))
                        },
                        onDelete = { toDelete = c },
                        onMemories = { memoryFor = c },
                        modelAvailable = true,
                    )
                }
            }
        }
        }
    }

    memoryFor?.let { c ->
        MemoryDialog(character = c, viewModel = viewModel, onDismiss = { memoryFor = null })
    }

    if (showSettings) {
        RoleplaySettingsDialog(
            availableModels = availableModels,
            selectedModel = modelName,
            onSelectModel = {
                settingsStore.setDefaultModel(it)
                defaultModel = it
            },
            onOpenSampler = { showSampler = true },
            onDismiss = { showSettings = false },
        )
    }

    if (showSampler) {
        SamplerDialog(onDismiss = { showSampler = false })
    }

    if (showPersonas) {
        PersonaManagerDialog(
            personas = personas,
            onDismiss = { showPersonas = false },
            onSelect = { viewModel.selectPersona(it) },
            onSave = { viewModel.savePersona(it) },
            onDelete = { viewModel.deletePersona(it) },
        )
    }

    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar a ${c.name}?") },
            text = { Text("Los chats que ya tengas con este personaje se conservan.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(c); toDelete = null }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

/** Franja bajo la barra superior: con qué persona (perfil tuyo) vas a chatear. */
@Composable
private fun PersonaBar(activeName: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Chateas como", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(activeName ?: "Sin persona (usa «User»)", style = MaterialTheme.typography.titleSmall)
            }
            Text("Cambiar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CharacterRow(
    character: RoleplayCharacter,
    onChat: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onMemories: () -> Unit,
    modelAvailable: Boolean,
) {
    val avatar = remember(character.avatarPath) {
        if (character.avatarPath.isEmpty()) null
        else try {
            val f = File(character.avatarPath)
            if (f.exists()) {
                // Se reduce al leer para no gastar memoria con PNG enormes.
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
            } else null
        } catch (_: Exception) {
            null
        }
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = modelAvailable, onClick = onChat)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    Image(avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(character.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(character.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = character.description.ifBlank { character.personality }.ifBlank { "Sin descripción" }
                Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!modelAvailable) {
                    Text("Primero agrega un modelo para chatear", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onMemories) { Icon(Icons.Rounded.Psychology, contentDescription = "Memoria") }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Editar") }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, contentDescription = "Compartir") }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Borrar") }
        }
    }
}

/** "Crear personaje" con pestañas Core Info / Appearance / Avanzado, como en Layla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditorScreen(
    characterId: String,
    navigateUp: () -> Unit,
    /** Modelos descargados, para elegir uno propio del personaje. */
    availableModels: List<String> = emptyList(),
    viewModel: RoleplayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(characterId == "new") }
    var original by remember { mutableStateOf<RoleplayCharacter?>(null) }

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var scenario by remember { mutableStateOf("") }
    var impression by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var firstMessage by remember { mutableStateOf("") }
    val greetings = remember { mutableStateListOf<String>() }
    var exampleMessages by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var postHistory by remember { mutableStateOf("") }
    var creatorNotes by remember { mutableStateOf("") }
    var creator by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var modelName by remember { mutableStateOf("") }

    LaunchedEffect(characterId) {
        if (characterId != "new") {
            val c = viewModel.getById(characterId)
            if (c != null) {
                original = c
                name = c.name
                description = c.description
                personality = c.personality
                scenario = c.scenario
                impression = c.impression
                tags = c.tagsJson.toStringList().joinToString(", ")
                firstMessage = c.firstMessage
                greetings.clear()
                greetings.addAll(c.alternateGreetingsJson.toStringList())
                exampleMessages = c.exampleMessages
                systemPrompt = c.systemPrompt
                postHistory = c.postHistoryInstructions
                creatorNotes = c.creatorNotes
                creator = c.creator
                modelName = c.modelName
            }
            loaded = true
        }
    }

    fun build(): RoleplayCharacter {
        val base = original ?: RoleplayCharacter(name = name)
        return base.copy(
            modelName = modelName,
            name = name.trim(),
            description = description,
            personality = personality,
            scenario = scenario,
            impression = impression,
            tagsJson = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toJsonArrayString(),
            firstMessage = firstMessage,
            alternateGreetingsJson = greetings.map { it.trim() }.filter { it.isNotEmpty() }.toJsonArrayString(),
            exampleMessages = exampleMessages,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistory,
            creatorNotes = creatorNotes,
            creator = creator,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (characterId == "new") "Crear personaje" else "Editar personaje") },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                listOf("Core Info", "Appearance", "Avanzado").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    0 -> {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it; nameError = false },
                            label = { Text("Nombre") }, isError = nameError,
                            supportingText = if (nameError) ({ Text("El nombre es obligatorio") }) else null,
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        BigField("Descripción", description, { description = it }, 6)
                        BigField("Personalidad", personality, { personality = it }, 4)
                        BigField("Escenario", scenario, { scenario = it }, 4)
                        BigField("Impresión (lo que piensa de ti)", impression, { impression = it }, 3)
                        OutlinedTextField(
                            value = tags, onValueChange = { tags = it },
                            label = { Text("Etiquetas (separadas por coma)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    1 -> {
                        Text(
                            "Saludos. Se usa el primero al empezar un chat; si agregas más, se elige uno al azar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BigField("Saludo inicial", firstMessage, { firstMessage = it }, 5)
                        greetings.forEachIndexed { i, g ->
                            Row(verticalAlignment = Alignment.Top) {
                                OutlinedTextField(
                                    value = g, onValueChange = { greetings[i] = it },
                                    label = { Text("Saludo alternativo ${i + 1}") },
                                    minLines = 3, modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { greetings.removeAt(i) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Quitar saludo")
                                }
                            }
                        }
                        OutlinedButton(onClick = { greetings.add("") }) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Añadir")
                        }
                        Text(
                            "Puedes usar {{char}} y {{user}} en cualquier campo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text("Modelo de este personaje", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Si no eliges uno, usa el modelo predeterminado de Roleplay.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Un modelo guardado que ya no está descargado se muestra, para poder cambiarlo.
                        val options = listOf("") + availableModels + listOfNotNull(modelName.takeIf { it.isNotEmpty() && it !in availableModels })
                        options.forEach { opt ->
                            Row(
                                Modifier.fillMaxWidth().clickable { modelName = opt },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.RadioButton(selected = modelName == opt, onClick = { modelName = opt })
                                Text(
                                    when {
                                        opt.isEmpty() -> "Predeterminado de Roleplay"
                                        opt !in availableModels -> "$opt (no disponible)"
                                        else -> opt
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        BigField("Diálogo de ejemplo (usa <START> para separar)", exampleMessages, { exampleMessages = it }, 6)
                        BigField("Prompt de sistema (vacío = el predeterminado)", systemPrompt, { systemPrompt = it }, 4)
                        BigField("Instrucciones posteriores al historial", postHistory, { postHistory = it }, 3)
                        BigField("Notas del creador", creatorNotes, { creatorNotes = it }, 3)
                        OutlinedTextField(
                            value = creator, onValueChange = { creator = it },
                            label = { Text("Creador") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (name.isBlank()) { nameError = true; tab = 0; return@OutlinedButton }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_SUBJECT, "${name.trim()}.json")
                            putExtra(Intent.EXTRA_TEXT, viewModel.exportJson(build()))
                        }
                        context.startActivity(Intent.createChooser(send, "Compartir personaje"))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Compartir") }
                Button(
                    onClick = {
                        if (name.isBlank()) { nameError = true; tab = 0; return@Button }
                        viewModel.save(build(), onDone = navigateUp)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Guardar") }
            }
        }
    }
}

@Composable
private fun BigField(label: String, value: String, onChange: (String) -> Unit, lines: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = lines,
        modifier = Modifier.fillMaxWidth(),
    )
}
