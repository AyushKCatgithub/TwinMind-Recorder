package com.example.twinmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.twinmind.data.local.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: SummaryEntity): Long

    @Update
    suspend fun update(summary: SummaryEntity)

    @Query("SELECT * FROM summaries WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getForSession(sessionId: Long): SummaryEntity?

    @Query("SELECT * FROM summaries WHERE sessionId = :sessionId LIMIT 1")
    fun observeForSession(sessionId: Long): Flow<SummaryEntity?>

    @Query("UPDATE summaries SET status = :status WHERE sessionId = :sessionId")
    suspend fun updateStatus(sessionId: Long, status: String)

    @Query("UPDATE summaries SET title = :title, summary = :summary, actionItems = :actionItems, keyPoints = :keyPoints, status = :status WHERE sessionId = :sessionId")
    suspend fun updateContent(sessionId: Long, title: String, summary: String, actionItems: String, keyPoints: String, status: String)
}
