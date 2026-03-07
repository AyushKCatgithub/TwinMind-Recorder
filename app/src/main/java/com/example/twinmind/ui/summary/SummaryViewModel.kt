package com.example.twinmind.ui.summary

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.twinmind.data.local.entity.AudioChunkEntity
import com.example.twinmind.data.local.entity.RecordingSessionEntity
import com.example.twinmind.data.local.entity.SummaryEntity
import com.example.twinmind.data.local.entity.TranscriptEntity
import com.example.twinmind.data.remote.api.SummaryContent
import com.example.twinmind.data.repository.RecordingRepository
import com.example.twinmind.data.repository.SummaryRepository
import com.example.twinmind.data.repository.TranscriptionRepository
import com.example.twinmind.service.workers.SummaryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class SummaryUiState {
    data object Loading : SummaryUiState()
    data class Ready(val content: SummaryContent) : SummaryUiState()
    data class Error(val message: String) : SummaryUiState()
    data object Idle : SummaryUiState()
}

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val summaryRepository: SummaryRepository,
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: -1L

    val session: StateFlow<RecordingSessionEntity?> = recordingRepository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val transcripts: StateFlow<List<TranscriptEntity>> = transcriptionRepository.observeTranscripts(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val summaryEntity: StateFlow<SummaryEntity?> = summaryRepository.observeSummary(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chunks: StateFlow<List<AudioChunkEntity>> = recordingRepository.observeChunksForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isTranscribing: StateFlow<Boolean> = chunks.map { chunkList ->
        chunkList.any { it.transcriptionStatus in listOf("PENDING", "IN_PROGRESS") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _summaryUiState = MutableStateFlow<SummaryUiState>(SummaryUiState.Idle)
    val summaryUiState: StateFlow<SummaryUiState> = _summaryUiState

    private val _streamingContent = MutableStateFlow(SummaryContent())
    val streamingContent: StateFlow<SummaryContent> = _streamingContent

    init {
        viewModelScope.launch {
            summaryRepository.observeSummary(sessionId).collect { entity ->
                if (entity == null) return@collect
                val content = SummaryContent(
                    title = entity.title,
                    summary = entity.summary,
                    actionItems = entity.actionItems,
                    keyPoints = entity.keyPoints
                )
                _streamingContent.value = content
                when (entity.status) {
                    "GENERATING" -> _summaryUiState.value = SummaryUiState.Loading
                    "COMPLETED" -> _summaryUiState.value = SummaryUiState.Ready(content)
                    "FAILED" -> _summaryUiState.value = SummaryUiState.Error("Failed to generate summary")
                }
            }
        }
    }

    fun generateSummary() {
        _summaryUiState.value = SummaryUiState.Loading

        val workRequest = OneTimeWorkRequestBuilder<SummaryWorker>()
            .setInputData(workDataOf("session_id" to sessionId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("summary_$sessionId")
            .build()

        WorkManager.getInstance(application)
            .enqueueUniqueWork(
                "summary_$sessionId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }

    fun retrySummary() {
        generateSummary()
    }
}
