package com.example.twinmind.ui.recording

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.twinmind.service.recording.RecordingStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSummary: (Long) -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val recordingStatus by viewModel.recordingStatus.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val silenceDetected by viewModel.silenceDetected.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val hasStarted by viewModel.hasStarted.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!hasStarted) {
            viewModel.startRecording()
        }
    }

    val isRecording = recordingStatus is RecordingStatus.Recording
    val isPaused = recordingStatus is RecordingStatus.PausedPhoneCall ||
            recordingStatus is RecordingStatus.PausedAudioFocus
    val isStopped = recordingStatus is RecordingStatus.Stopped ||
            (recordingStatus is RecordingStatus.Idle && hasStarted)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isStopped || !hasStarted) onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            RecordingIndicator(isRecording = isRecording)

            Spacer(modifier = Modifier.height(24.dp))

            val displaySeconds = if (isStopped) {
                (session?.durationMs ?: 0L) / 1000
            } else {
                elapsedSeconds
            }
            val minutes = displaySeconds / 60
            val seconds = displaySeconds % 60
            Text(
                text = String.format("%02d:%02d", minutes, seconds),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            val statusColor = when (recordingStatus) {
                is RecordingStatus.Recording -> MaterialTheme.colorScheme.error
                is RecordingStatus.PausedPhoneCall,
                is RecordingStatus.PausedAudioFocus -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            }

            Text(
                text = recordingStatus.displayText,
                style = MaterialTheme.typography.titleMedium,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = silenceDetected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "No audio detected - Check microphone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPaused) {
                    Button(
                        onClick = { viewModel.resumeRecording() },
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                if (!isStopped) {
                    Button(
                        onClick = { viewModel.stopRecording() },
                        shape = CircleShape,
                        modifier = Modifier.size(if (isPaused) 72.dp else 80.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                if (isStopped) {
                    Button(
                        onClick = { onNavigateToSummary(viewModel.sessionId) },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(
                            "View Summary",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (transcripts.isNotEmpty()) {
                Text(
                    "Live Transcript",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(transcripts) { transcript ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = transcript.text,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingIndicator(isRecording: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        CircleShape
                    )
            )
        }
        Icon(
            Icons.Default.FiberManualRecord,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isRecording) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}
