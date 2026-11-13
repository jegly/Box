package com.google.ai.edge.gallery.data.characters

import com.google.ai.edge.gallery.data.local.dao.MemoryDao
import com.google.ai.edge.gallery.data.local.entities.CharacterMemory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Roleplay: recuerdos a largo plazo por personaje. */
@Singleton
class MemoryRepository @Inject constructor(private val memoryDao: MemoryDao) {
    fun observe(characterId: String): Flow<List<CharacterMemory>> = memoryDao.observe(characterId)

    suspend fun getAll(characterId: String): List<CharacterMemory> =
        withContext(Dispatchers.IO) { memoryDao.getAll(characterId) }

    suspend fun save(memory: CharacterMemory) = withContext(Dispatchers.IO) {
        val text = memory.content.trim()
        if (text.isNotEmpty()) memoryDao.insert(memory.copy(content = text))
    }

    suspend fun delete(memory: CharacterMemory) = withContext(Dispatchers.IO) { memoryDao.delete(memory) }

    suspend fun deleteAll(characterId: String) = withContext(Dispatchers.IO) { memoryDao.deleteAll(characterId) }
}
