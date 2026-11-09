package com.google.ai.edge.gallery.data.characters

import android.content.Context
import android.net.Uri
import com.google.ai.edge.gallery.data.local.ChatRepository
import com.google.ai.edge.gallery.data.local.dao.CharacterDao
import com.google.ai.edge.gallery.data.local.entities.Conversation
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import com.google.ai.edge.gallery.security.SecurityUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Roleplay: guarda, importa y exporta personajes, y crea chats con ellos.
 * La lógica de leer tarjetas y armar el prompt vive en CharacterCardParser y
 * CharacterPromptBuilder, que no dependen de Android y tienen pruebas unitarias.
 */
@Singleton
class CharacterRepository @Inject constructor(
    private val characterDao: CharacterDao,
    private val chatRepository: ChatRepository,
    @ApplicationContext private val context: Context,
) {
    fun observeAll(): Flow<List<RoleplayCharacter>> = characterDao.getAll()

    suspend fun getById(id: String): RoleplayCharacter? = characterDao.getById(id)

    /**
     * Importa una tarjeta .json o .png elegida por el usuario.
     * Lanza CharacterCardException con un mensaje legible si el archivo no sirve.
     */
    suspend fun importFromUri(uri: Uri): RoleplayCharacter = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use {
            it.readNBytes(CharacterCardParser.MAX_FILE_BYTES + 1)
        } ?: throw CharacterCardException("No se pudo abrir el archivo")
        importFromBytes(bytes)
    }

    suspend fun importFromBytes(bytes: ByteArray): RoleplayCharacter = withContext(Dispatchers.IO) {
        val parsed = CharacterCardParser.parse(bytes)
        val id = UUID.randomUUID().toString()
        // Si la tarjeta venía en un PNG, esa imagen es el avatar del personaje.
        val avatarPath = parsed.avatarPng?.let { png ->
            avatarFile(id).also { it.writeBytes(png) }.absolutePath
        } ?: ""
        val entity = parsed.card.toEntity(id = id, avatarPath = avatarPath)
        characterDao.insert(entity)
        entity
    }

    /** Crea o actualiza (REPLACE) un personaje, por ejemplo desde la pantalla "Crear personaje". */
    suspend fun save(character: RoleplayCharacter) = withContext(Dispatchers.IO) {
        characterDao.insert(character.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Borra el personaje y su avatar. Los chats ya creados siguen funcionando: llevan el prompt guardado. */
    suspend fun delete(character: RoleplayCharacter) = withContext(Dispatchers.IO) {
        characterDao.delete(character)
        if (character.avatarPath.isNotEmpty()) {
            SecurityUtils.secureDelete(File(character.avatarPath))
        }
    }

    /** JSON chara_card_v2 para compartir el personaje con SillyTavern, Layla u otra app. */
    fun exportJson(character: RoleplayCharacter): String =
        CharacterCardParser.encodeV2(character.toCard())

    /**
     * Crea una conversación nueva con este personaje: guarda el prompt de sistema armado desde la
     * tarjeta y, si hay saludo, lo deja como primer mensaje del personaje.
     *
     * Ojo: este prompt NO pasa por SecurityUtils.sanitizePrompt, que corta a 4096 caracteres y
     * truncaría las tarjetas largas. Las instrucciones posteriores al historial
     * (post_history_instructions) todavía no se aplican en cada turno: eso va con el chat.
     */
    suspend fun startChat(
        character: RoleplayCharacter,
        modelName: String,
        userName: String = CharacterPromptBuilder.DEFAULT_USER_NAME,
        random: Random = Random.Default,
    ): Conversation = withContext(Dispatchers.IO) {
        val built = CharacterPromptBuilder.build(character.toCard(), userName)
        val greeting = CharacterPromptBuilder.pickGreeting(built.greetings, random)
        // El modelo no ve el saludo guardado como mensaje, así que se le avisa en el prompt.
        val systemPrompt = if (greeting != null) {
            built.systemPrompt + "\n\n### Opening message (already sent)\n" + greeting
        } else {
            built.systemPrompt
        }
        val conversation = chatRepository.createConversation(
            title = character.name,
            taskType = "llm_chat",
            modelName = modelName,
            systemPrompt = systemPrompt,
            characterId = character.id,
        )
        greeting?.let { text ->
            chatRepository.saveMessage(
                conversationId = conversation.id,
                role = "assistant",
                content = text,
            )
        }
        conversation
    }

    private fun avatarFile(id: String): File {
        val dir = File(context.filesDir, "characters").apply { mkdirs() }
        return File(dir, "$id.png")
    }
}
