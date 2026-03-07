package com.example.twinmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val status: String = "RECORDING",
    val title: String? = null,
    val durationMs: Long = 0
)
