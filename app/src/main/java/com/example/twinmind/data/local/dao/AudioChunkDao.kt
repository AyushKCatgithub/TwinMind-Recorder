package com.example.twinmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.twinmind.data.local.entity.AudioChunkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioChunkDao {
    @Insert
    suspend fun insert(chunk: AudioChunkEntity): Long

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId ORDER BY sequenceNumber")
    suspend fun getChunksForSession(sessionId: Long): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId ORDER BY sequenceNumber")
    fun observeChunksForSession(sessionId: Long): Flow<List<AudioChunkEntity>>

    @Query("SELECT * FROM audio_chunks WHERE id = :id")
    suspend fun getById(id: Long): AudioChunkEntity?

    @Query("UPDATE audio_chunks SET transcriptionStatus = :status WHERE id = :id")
    suspend fun updateTranscriptionStatus(id: Long, status: String)

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId AND transcriptionStatus IN ('PENDING', 'FAILED')")
    suspend fun getPendingOrFailedChunks(sessionId: Long): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE transcriptionStatus IN ('PENDING', 'FAILED')")
    suspend fun getAllPendingOrFailedChunks(): List<AudioChunkEntity>

    @Query("SELECT MAX(sequenceNumber) FROM audio_chunks WHERE sessionId = :sessionId")
    suspend fun getMaxSequenceNumber(sessionId: Long): Int?
}
