package com.example.twinmind.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "summaries",
    foreignKeys = [ForeignKey(
        entity = RecordingSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val title: String = "",
    val summary: String = "",
    val actionItems: String = "",
    val keyPoints: String = "",
    val status: String = "PENDING"
)
