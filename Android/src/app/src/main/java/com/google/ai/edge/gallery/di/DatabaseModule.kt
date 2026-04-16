package com.google.ai.edge.gallery.di

import android.content.Context
import com.google.ai.edge.gallery.data.local.BoxChatDatabase
import com.google.ai.edge.gallery.data.local.dao.ConversationDao
import com.google.ai.edge.gallery.data.local.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BoxChatDatabase {
        return BoxChatDatabase.getInstance(context)
    }

    @Provides
    fun provideConversationDao(db: BoxChatDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: BoxChatDatabase): MessageDao = db.messageDao()
}
