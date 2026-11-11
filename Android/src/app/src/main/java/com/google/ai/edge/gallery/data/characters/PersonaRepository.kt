package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.dao.PersonaDao
import com.google.ai.edge.gallery.data.local.entities.UserPersona
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Roleplay: guarda los perfiles del usuario y cuál está activo. */
@Singleton
class PersonaRepository @Inject constructor(
    private val personaDao: PersonaDao,
) {
    fun observeAll(): Flow<List<UserPersona>> = personaDao.getAll()

    suspend fun getActive(): UserPersona? = withContext(Dispatchers.IO) { personaDao.getActive() }

    /** Crea o actualiza. La primera persona que se crea queda activa automáticamente. */
    suspend fun save(persona: UserPersona) = withContext(Dispatchers.IO) {
        val first = personaDao.getActive() == null
        val keepActive = persona.isActive || first
        personaDao.insert(
            persona.copy(
                name = persona.name.trim(),
                isActive = keepActive,
                updatedAt = System.currentTimeMillis(),
            )
        )
        if (first) personaDao.setActiveOnly(persona.id)
    }

    suspend fun setActive(persona: UserPersona) = withContext(Dispatchers.IO) {
        personaDao.setActiveOnly(persona.id)
    }

    /** Si se borra la persona activa, el chat usa el nombre por defecto hasta elegir otra. */
    suspend fun delete(persona: UserPersona) = withContext(Dispatchers.IO) {
        personaDao.delete(persona)
    }
}
