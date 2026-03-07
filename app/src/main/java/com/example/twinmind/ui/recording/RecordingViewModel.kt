package com.example.twinmind.ui.recording

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmind.data.local.entity.AudioChunkEntity
import com.example.twinmind.data.local.entity.RecordingSessionEntity
import com.example.twinmind.data.local.entity.TranscriptEntity
import com.example.twinmind.data.repository.RecordingRepository
import com.example.twinmind.data.repository.TranscriptionRepository
import com.example.twinmind.service.recording.AudioRecordingService
import com.example.twinmind.service.recording.RecordingStatus
import com.example.twinmind.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RecordingUiState {
    data object Idle : RecordingUiState()
    data class Active(
        val session: RecordingSessionEntity?,
        val status: RecordingStatus,
        val elapsedSeconds: Long,
        val chunks: List<AudioChunkEntity>,
        val transcripts: List<TranscriptEntity>,
        val silenceWarning: Boolean
    ) : RecordingUiState()
}

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: -1L

    val recordingStatus: StateFlow<RecordingStatus> = AudioRecordingService.recordingState

    val elapsedSeconds: StateFlow<Long> = AudioRecordingService.elapsedSeconds

    val silenceDetected: StateFlow<Boolean> = AudioRecordingService.silenceDetected

    val session: StateFlow<RecordingSessionEntity?> = recordingRepository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chunks: StateFlow<List<AudioChunkEntity>> = recordingRepository.observeChunksForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transcripts: StateFlow<List<TranscriptEntity>> = transcriptionRepository.observeTranscripts(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _hasStarted = MutableStateFlow(false)
    val hasStarted: StateFlow<Boolean> = _hasStarted

    fun startRecording() {
        if (_hasStarted.value) return
        _hasStarted.value = true

        val intent = Intent(application, AudioRecordingService::class.java).apply {
            action = Constants.ACTION_START
            putExtra(Constants.EXTRA_SESSION_ID, sessionId)
        }
        ContextCompat.startForegroundService(application, intent)
    }

    fun stopRecording() {
        val intent = Intent(application, AudioRecordingService::class.java).apply {
            action = Constants.ACTION_STOP
        }
        application.startService(intent)
    }

    fun resumeRecording() {
        val intent = Intent(application, AudioRecordingService::class.java).apply {
            action = Constants.ACTION_RESUME
        }
        application.startService(intent)
    }

    fun retryFailedTranscriptions() {
        viewModelScope.launch {
            val failed = transcriptionRepository.getPendingOrFailedChunks(sessionId)
            for (chunk in failed) {
                recordingRepository.enqueueTranscription(application, chunk.id)
            }
        }
    }
}
