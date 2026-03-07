package com.example.twinmind.service.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.twinmind.data.repository.TranscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transcriptionRepository: TranscriptionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val chunkId = inputData.getLong("chunk_id", -1L)
        if (chunkId == -1L) return Result.failure()

        return try {
            transcriptionRepository.transcribeChunk(chunkId)
            Result.success()
        } catch (e: Exception) {
            Log.e("TranscriptionWorker", "Attempt $runAttemptCount failed for chunk $chunkId", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
