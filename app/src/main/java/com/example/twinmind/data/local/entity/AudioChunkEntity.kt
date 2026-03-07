package com.example.twinmind.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_chunks",
    foreignKeys = [ForeignKey(
        entity = RecordingSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class AudioChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val filePath: String,
    val sequenceNumber: Int,
    val transcriptionStatus: String = "PENDING",
    val startTimeMs: Long = 0,
    val endTimeMs: Long = 0
)
