package com.example.twinmind.utils

import android.media.AudioFormat

object Constants {
    const val SAMPLE_RATE = 16000
    val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val BYTES_PER_SAMPLE = 2
    const val CHUNK_DURATION_MS = 30_000L
    const val OVERLAP_DURATION_MS = 2_000L
    const val SILENCE_THRESHOLD = 500
    const val SILENCE_DURATION_MS = 10_000L
    const val MIN_STORAGE_MB = 50L

    const val NOTIFICATION_ID = 1
    const val WARNING_NOTIFICATION_ID = 2
    const val RECORDING_CHANNEL_ID = "recording_channel"
    const val WARNING_CHANNEL_ID = "warning_channel"

    const val ACTION_START = "com.example.twinmind.ACTION_START"
    const val ACTION_STOP = "com.example.twinmind.ACTION_STOP"
    const val ACTION_RESUME = "com.example.twinmind.ACTION_RESUME"
    const val EXTRA_SESSION_ID = "extra_session_id"
}
