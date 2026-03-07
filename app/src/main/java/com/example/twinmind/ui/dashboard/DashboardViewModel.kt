package com.example.twinmind.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.twinmind.data.local.entity.RecordingSessionEntity
import com.example.twinmind.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val sessions: List<RecordingSessionEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    val sessions: StateFlow<List<RecordingSessionEntity>> = recordingRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _newSessionId = MutableStateFlow<Long?>(null)
    val newSessionId: StateFlow<Long?> = _newSessionId

    fun createNewSession() {
        viewModelScope.launch {
            val id = recordingRepository.createSession()
            _newSessionId.value = id
        }
    }

    fun onSessionNavigated() {
        _newSessionId.value = null
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            recordingRepository.deleteSession(id)
        }
    }
}
