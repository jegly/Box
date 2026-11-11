package com.google.ai.edge.gallery.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.ai.edge.gallery.data.local.dao.CharacterDao
import com.google.ai.edge.gallery.data.local.dao.ConversationDao
import com.google.ai.edge.gallery.data.local.dao.MessageDao
import com.google.ai.edge.gallery.data.local.dao.PersonaDao
import com.google.ai.edge.gallery.data.local.entities.Conversation
import com.google.ai.edge.gallery.data.local.entities.Message
import com.google.ai.edge.gallery.data.local.entities.RoleplayCharacter
import com.google.ai.edge.gallery.data.local.entities.UserPersona
import com.google.ai.edge.gallery.security.SecurityUtils
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Box: Encrypted Room database for chat history persistence.
 * Uses SQLCipher for at-rest encryption with a key derived from device Keystore.
 */
@Database(
    entities = [Conversation::class, Message::class, RoleplayCharacter::class, UserPersona::class],
    version = 4,
    exportSchema = false
)
abstract class BoxChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun characterDao(): CharacterDao
    abstract fun personaDao(): PersonaDao

    companion object {
        private const val DB_NAME = "box_chat.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN systemPrompt TEXT NOT NULL DEFAULT ''")
            }
        }

        // Roleplay: tabla de personajes + enlace conversación -> personaje.
        // Las columnas deben coincidir con RoleplayCharacter y Conversation, o Room lanza error al abrir.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN characterId TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `characters` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`personality` TEXT NOT NULL, " +
                        "`scenario` TEXT NOT NULL, " +
                        "`firstMessage` TEXT NOT NULL, " +
                        "`alternateGreetingsJson` TEXT NOT NULL, " +
                        "`exampleMessages` TEXT NOT NULL, " +
                        "`systemPrompt` TEXT NOT NULL, " +
                        "`postHistoryInstructions` TEXT NOT NULL, " +
                        "`creatorNotes` TEXT NOT NULL, " +
                        "`creator` TEXT NOT NULL, " +
                        "`characterVersion` TEXT NOT NULL, " +
                        "`tagsJson` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`impression` TEXT NOT NULL, " +
                        "`avatarPath` TEXT NOT NULL, " +
                        "`extensionsJson` TEXT NOT NULL, " +
                        "`characterBookJson` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        // Roleplay: personas (perfiles del usuario) + enlace conversación -> persona.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN personaId TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `personas` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`isActive` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
