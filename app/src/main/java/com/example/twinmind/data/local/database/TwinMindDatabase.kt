package com.example.twinmind.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.twinmind.data.local.dao.AudioChunkDao
import com.example.twinmind.data.local.dao.RecordingSessionDao
import com.example.twinmind.data.local.dao.SummaryDao
import com.example.twinmind.data.local.dao.TranscriptDao
import com.example.twinmind.data.local.entity.AudioChunkEntity
import com.example.twinmind.data.local.entity.RecordingSessionEntity
import com.example.twinmind.data.local.entity.SummaryEntity
import com.example.twinmind.data.local.entity.TranscriptEntity

@Database(
    entities = [
        RecordingSessionEntity::class,
        AudioChunkEntity::class,
        TranscriptEntity::class,
        SummaryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TwinMindDatabase : RoomDatabase() {
    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun summaryDao(): SummaryDao
}
