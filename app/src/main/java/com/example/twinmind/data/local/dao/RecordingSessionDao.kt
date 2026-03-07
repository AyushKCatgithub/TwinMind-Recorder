package com.example.twinmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.twinmind.data.local.entity.RecordingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    @Insert
    suspend fun insert(session: RecordingSessionEntity): Long

    @Update
    suspend fun update(session: RecordingSessionEntity)

    @Query("SELECT * FROM recording_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun getById(id: Long): RecordingSessionEntity?

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<RecordingSessionEntity?>

    @Query("SELECT * FROM recording_sessions WHERE status IN ('RECORDING', 'PAUSED_PHONE_CALL', 'PAUSED_AUDIO_FOCUS')")
    suspend fun getActiveSessions(): List<RecordingSessionEntity>

    @Query("UPDATE recording_sessions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE recording_sessions SET endTime = :endTime, status = :status, durationMs = :durationMs WHERE id = :id")
    suspend fun finishSession(id: Long, endTime: Long, status: String, durationMs: Long)

    @Query("UPDATE recording_sessions SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE recording_sessions SET durationMs = :durationMs WHERE id = :id")
    suspend fun updateDuration(id: Long, durationMs: Long)

    @Query("DELETE FROM recording_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
