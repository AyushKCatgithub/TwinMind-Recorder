package com.example.twinmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.twinmind.data.local.entity.TranscriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transcript: TranscriptEntity): Long

    @Query("SELECT * FROM transcripts WHERE sessionId = :sessionId ORDER BY sequenceNumber")
    suspend fun getTranscriptsForSession(sessionId: Long): List<TranscriptEntity>

    @Query("SELECT * FROM transcripts WHERE sessionId = :sessionId ORDER BY sequenceNumber")
    fun observeTranscriptsForSession(sessionId: Long): Flow<List<TranscriptEntity>>

    @Query("SELECT GROUP_CONCAT(text, ' ') FROM transcripts WHERE sessionId = :sessionId ORDER BY sequenceNumber")
    suspend fun getFullTranscript(sessionId: Long): String?

    @Query("DELETE FROM transcripts WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
