package com.google.ai.edge.gallery.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.ai.edge.gallery.data.local.dao.ConversationDao
import com.google.ai.edge.gallery.data.local.dao.MessageDao
import com.google.ai.edge.gallery.data.local.entities.Conversation
import com.google.ai.edge.gallery.data.local.entities.Message
import com.google.ai.edge.gallery.security.SecurityUtils
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Box: Encrypted Room database for chat history persistence.
 * Uses SQLCipher for at-rest encryption with a key derived from device Keystore.
 */
@Database(
    entities = [Conversation::class, Message::class],
    version = 1,
    exportSchema = false
)
abstract class BoxChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        private const val DB_NAME = "box_chat.db"

        @Volatile
        private var INSTANCE: BoxChatDatabase? = null

        fun getInstance(context: Context): BoxChatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): BoxChatDatabase {
            // Load SQLCipher native libraries
            System.loadLibrary("sqlcipher")

            val passphrase = SecurityUtils.getDatabasePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                BoxChatDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
