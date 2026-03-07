package com.example.twinmind.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.twinmind.data.local.dao.AudioChunkDao
import com.example.twinmind.data.local.dao.RecordingSessionDao
import com.example.twinmind.data.local.entity.AudioChunkEntity
import com.example.twinmind.data.local.entity.RecordingSessionEntity
import com.example.twinmind.service.workers.TranscriptionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val sessionDao: RecordingSessionDao,
    private val chunkDao: AudioChunkDao,
    @ApplicationContext private val context: Context
) {
    fun getAllSessions(): Flow<List<RecordingSessionEntity>> = sessionDao.getAllSessions()

    suspend fun createSession(): Long {
        return sessionDao.insert(RecordingSessionEntity())
    }

    suspend fun getSession(id: Long): RecordingSessionEntity? = sessionDao.getById(id)

    fun observeSession(id: Long): Flow<RecordingSessionEntity?> = sessionDao.observeById(id)

    suspend fun updateSessionStatus(id: Long, status: String) = sessionDao.updateStatus(id, status)

    suspend fun finishSession(id: Long, durationMs: Long) {
        sessionDao.finishSession(id, System.currentTimeMillis(), "STOPPED", durationMs)
    }

    suspend fun updateSessionDuration(id: Long, durationMs: Long) = sessionDao.updateDuration(id, durationMs)

    suspend fun updateSessionTitle(id: Long, title: String) = sessionDao.updateTitle(id, title)

    suspend fun saveAudioChunk(
        sessionId: Long,
        filePath: String,
        sequenceNumber: Int,
        startTimeMs: Long,
        endTimeMs: Long
    ): Long {
        return chunkDao.insert(
            AudioChunkEntity(
                sessionId = sessionId,
                filePath = filePath,
                sequenceNumber = sequenceNumber,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs
            )
        )
    }

    suspend fun getActiveSessions(): List<RecordingSessionEntity> = sessionDao.getActiveSessions()

    fun observeChunksForSession(sessionId: Long) = chunkDao.observeChunksForSession(sessionId)

    suspend fun getMaxChunkSequence(sessionId: Long): Int = chunkDao.getMaxSequenceNumber(sessionId) ?: -1

    suspend fun deleteSession(id: Long) {
        val chunks = chunkDao.getChunksForSession(id)
        for (chunk in chunks) {
            try { java.io.File(chunk.filePath).delete() } catch (_: Exception) {}
        }
        sessionDao.deleteById(id)
    }

    fun enqueueTranscription(appContext: Context, chunkId: Long) {
        val workRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(workDataOf("chunk_id" to chunkId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag("transcription_$chunkId")
            .build()
        WorkManager.getInstance(appContext).enqueue(workRequest)
    }
}
