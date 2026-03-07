package com.example.twinmind.ui.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    onNavigateBack: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val summaryEntity by viewModel.summaryEntity.collectAsStateWithLifecycle()
    val summaryUiState by viewModel.summaryUiState.collectAsStateWithLifecycle()
    val streamingContent by viewModel.streamingContent.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.isTranscribing.collectAsStateWithLifecycle()
    val chunks by viewModel.chunks.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Transcript", "Summary")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        session?.title ?: "Meeting #${viewModel.sessionId}",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> TranscriptTab(
                    transcriptText = transcripts.joinToString("\n\n") { it.text },
                    isEmpty = transcripts.isEmpty(),
                    isTranscribing = isTranscribing,
                    totalChunks = chunks.size,
                    completedChunks = chunks.count { it.transcriptionStatus == "COMPLETED" }
                )
                1 -> SummaryTab(
                    summaryUiState = summaryUiState,
                    summaryEntity = summaryEntity,
                    streamingContent = streamingContent,
                    onGenerate = { viewModel.generateSummary() },
                    onRetry = { viewModel.retrySummary() },
                    hasTranscript = transcripts.isNotEmpty(),
                    isTranscribing = isTranscribing
                )
            }
        }
    }
}

@Composable
private fun TranscriptTab(
    transcriptText: String,
    isEmpty: Boolean,
    isTranscribing: Boolean,
    totalChunks: Int,
    completedChunks: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        when {
            isEmpty && isTranscribing -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Transcribing audio...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (totalChunks > 0) {
                        Text(
                            "Processing chunk $completedChunks of $totalChunks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { if (totalChunks > 0) completedChunks.toFloat() / totalChunks else 0f },
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(4.dp),
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        PulsingText("Waiting for audio chunks...")
                    }
                }
            }

            isEmpty && !isTranscribing -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No transcript available",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Record a meeting to see the transcript here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (isTranscribing) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Transcribing... ($completedChunks/$totalChunks chunks)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Text(
                        text = transcriptText,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingText(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.alpha(alpha)
    )
}

@Composable
private fun SummaryTab(
    summaryUiState: SummaryUiState,
    summaryEntity: com.example.twinmind.data.local.entity.SummaryEntity?,
    streamingContent: com.example.twinmind.data.remote.api.SummaryContent,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    hasTranscript: Boolean,
    isTranscribing: Boolean
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        val isCompleted = summaryEntity?.status == "COMPLETED"
        val isGenerating = summaryUiState is SummaryUiState.Loading
        val hasContent = summaryUiState is SummaryUiState.Ready
        val isError = summaryUiState is SummaryUiState.Error ||
                summaryEntity?.status == "FAILED"

        when {
            isCompleted && hasContent -> {
                SummaryContentView(
                    title = streamingContent.title.ifEmpty { summaryEntity?.title ?: "" },
                    summary = streamingContent.summary.ifEmpty { summaryEntity?.summary ?: "" },
                    actionItems = streamingContent.actionItems.ifEmpty { summaryEntity?.actionItems ?: "" },
                    keyPoints = streamingContent.keyPoints.ifEmpty { summaryEntity?.keyPoints ?: "" }
                )
            }

            isCompleted -> {
                SummaryContentView(
                    title = summaryEntity?.title ?: "",
                    summary = summaryEntity?.summary ?: "",
                    actionItems = summaryEntity?.actionItems ?: "",
                    keyPoints = summaryEntity?.keyPoints ?: ""
                )
            }

            isGenerating -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "Generating summary...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PulsingText("AI is analyzing your meeting transcript")
                    }
                }
            }

            isError -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (summaryUiState is SummaryUiState.Error) summaryUiState.message
                            else "Failed to generate summary",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Generate an AI summary of your meeting",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onGenerate,
                            enabled = hasTranscript && !isTranscribing,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Summary")
                        }
                        if (isTranscribing) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Transcription in progress...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        } else if (!hasTranscript) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No transcript available yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryContentView(
    title: String,
    summary: String,
    actionItems: String,
    keyPoints: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AnimatedVisibility(visible = title.isNotEmpty(), enter = fadeIn()) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (summary.isNotEmpty()) {
            SummarySection(
                icon = Icons.Default.Summarize,
                title = "Summary",
                content = summary
            )
        }

        if (actionItems.isNotEmpty()) {
            SummarySection(
                icon = Icons.Default.CheckCircleOutline,
                title = "Action Items",
                content = actionItems
            )
        }

        if (keyPoints.isNotEmpty()) {
            SummarySection(
                icon = Icons.Default.Lightbulb,
                title = "Key Points",
                content = keyPoints
            )
        }
    }
}

@Composable
private fun SummarySection(
    icon: ImageVector,
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}
