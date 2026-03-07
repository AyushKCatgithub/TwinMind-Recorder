package com.example.twinmind.service.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.twinmind.data.remote.api.SummaryContent
import com.example.twinmind.data.repository.SummaryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val summaryRepository: SummaryRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong("session_id", -1L)
        if (sessionId == -1L) return Result.failure()

        val existing = summaryRepository.getSummary(sessionId)
        if (existing?.status == "COMPLETED") return Result.success()

        return try {
            summaryRepository.generateSummaryStream(sessionId).collect {}
            Result.success()
        } catch (e: Exception) {
            Log.e("SummaryWorker", "Attempt $runAttemptCount failed for session $sessionId", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                summaryRepository.updateSummaryContent(sessionId, SummaryContent(), "FAILED")
                Result.failure()
            }
        }
    }
}
