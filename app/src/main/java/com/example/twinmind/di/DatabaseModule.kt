package com.example.twinmind.di

import android.content.Context
import androidx.room.Room
import com.example.twinmind.data.local.dao.AudioChunkDao
import com.example.twinmind.data.local.dao.RecordingSessionDao
import com.example.twinmind.data.local.dao.SummaryDao
import com.example.twinmind.data.local.dao.TranscriptDao
import com.example.twinmind.data.local.database.TwinMindDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): TwinMindDatabase {
        return Room.databaseBuilder(
            context,
            TwinMindDatabase::class.java,
            "twinmind_database"
        ).build()
    }

    @Provides
    fun provideRecordingSessionDao(db: TwinMindDatabase): RecordingSessionDao = db.recordingSessionDao()

    @Provides
    fun provideAudioChunkDao(db: TwinMindDatabase): AudioChunkDao = db.audioChunkDao()

    @Provides
    fun provideTranscriptDao(db: TwinMindDatabase): TranscriptDao = db.transcriptDao()

    @Provides
    fun provideSummaryDao(db: TwinMindDatabase): SummaryDao = db.summaryDao()
}
