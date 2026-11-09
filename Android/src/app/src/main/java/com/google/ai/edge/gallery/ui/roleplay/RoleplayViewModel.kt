package com.google.ai.edge.gallery.ui.roleplay

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.characters.CharacterCardException
import com.google.ai.edge.gallery.data.characters.CharacterRepository
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Roleplay: lista de personajes, importar/exportar, guardar y abrir un chat. */
@HiltViewModel
class RoleplayViewModel @Inject constructor(
    private val repository: CharacterRepository,
) : ViewModel() {

    val characters: StateFlow<List<RoleplayCharacter>> = repository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    /** Mensaje corto para mostrar al usuario (error o confirmación). */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            try {
                val c = repository.importFromUri(uri)
                _message.value = "Personaje importado: ${c.name}"
            } catch (e: CharacterCardException) {
                _message.value = e.message ?: "No se pudo importar la tarjeta"
            } catch (e: Exception) {
                _message.value = "No se pudo importar: ${e.message ?: "error desconocido"}"
            }
        }
    }

    fun save(character: RoleplayCharacter, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.save(character)
                onDone()
            } catch (e: Exception) {
                _message.value = "No se pudo guardar: ${e.message ?: "error desconocido"}"
            }
        }
    }

    fun delete(character: RoleplayCharacter) {
        viewModelScope.launch {
            try {
                repository.delete(character)
            } catch (e: Exception) {
                _message.value = "No se pudo borrar: ${e.message ?: "error desconocido"}"
            }
        }
    }

    suspend fun getById(id: String): RoleplayCharacter? = repository.getById(id)

    fun exportJson(character: RoleplayCharacter): String = repository.exportJson(character)

    /** Crea el chat y devuelve el id de la conversación (null si falló). */
    fun startChat(character: RoleplayCharacter, modelName: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val conversation = repository.startChat(character, modelName)
                onReady(conversation.id)
            } catch (e: Exception) {
                _message.value = "No se pudo abrir el chat: ${e.message ?: "error desconocido"}"
            }
        }
    }
}
