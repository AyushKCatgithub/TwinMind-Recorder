package com.example.twinmind.service.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.twinmind.data.repository.RecordingRepository
import com.example.twinmind.data.repository.SummaryRepository
import com.example.twinmind.data.repository.TranscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TerminationRecoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recordingRepository: RecordingRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val summaryRepository: SummaryRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val activeSessions = recordingRepository.getActiveSessions()
        for (session in activeSessions) {
            recordingRepository.finishSession(session.id, session.durationMs)
        }

        val pendingChunks = transcriptionRepository.getAllPendingOrFailedChunks()
        for (chunk in pendingChunks) {
            val workRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(workDataOf("chunk_id" to chunk.id))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag("recovery_transcription_${chunk.id}")
                .build()
            WorkManager.getInstance(applicationContext).enqueue(workRequest)
        }

        val allSessions = recordingRepository.getActiveSessions()
        for (session in allSessions) {
            val summary = summaryRepository.getSummary(session.id)
            if (summary?.status == "GENERATING") {
                val workRequest = OneTimeWorkRequestBuilder<SummaryWorker>()
                    .setInputData(workDataOf("session_id" to session.id))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(applicationContext)
                    .enqueueUniqueWork(
                        "summary_recovery_${session.id}",
                        ExistingWorkPolicy.KEEP,
                        workRequest
                    )
            }
        }

        return Result.success()
    }
}
