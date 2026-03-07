package com.example.twinmind.data.repository

import android.util.Base64
import android.util.Log
import com.example.twinmind.BuildConfig
import com.example.twinmind.data.local.dao.AudioChunkDao
import com.example.twinmind.data.local.dao.TranscriptDao
import com.example.twinmind.data.local.entity.AudioChunkEntity
import com.example.twinmind.data.local.entity.TranscriptEntity
import com.example.twinmind.data.remote.api.GeminiApi
import com.example.twinmind.data.remote.api.GeminiContent
import com.example.twinmind.data.remote.api.GeminiPart
import com.example.twinmind.data.remote.api.GeminiRequest
import com.example.twinmind.data.remote.api.GenerationConfig
import com.example.twinmind.data.remote.api.InlineData
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    private val geminiApi: GeminiApi,
    private val chunkDao: AudioChunkDao,
    private val transcriptDao: TranscriptDao
) {
    companion object {
        private const val TAG = "TranscriptionRepo"
        const val GEMINI_MODEL = "gemini-2.5-flash-lite"
    }

    suspend fun transcribeChunk(chunkId: Long) {
        val chunk = chunkDao.getById(chunkId)
            ?: throw IllegalArgumentException("Chunk not found: $chunkId")

        chunkDao.updateTranscriptionStatus(chunkId, "IN_PROGRESS")

        try {
            val text = transcribeWithGemini(chunk)
            transcriptDao.insert(
                TranscriptEntity(
                    sessionId = chunk.sessionId,
                    text = text,
                    sequenceNumber = chunk.sequenceNumber
                )
            )
            chunkDao.updateTranscriptionStatus(chunkId, "COMPLETED")
            Log.d(TAG, "Transcription completed for chunk $chunkId, session ${chunk.sessionId}")
        } catch (e: Exception) {
            chunkDao.updateTranscriptionStatus(chunkId, "FAILED")
            Log.e(TAG, "Transcription failed for chunk $chunkId: ${e.message}", e)
            throw e
        }
    }

    private suspend fun transcribeWithGemini(chunk: AudioChunkEntity): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        Log.d(TAG, "API key length: ${apiKey.length}, model: $GEMINI_MODEL")
        if (apiKey.isBlank()) {
            throw IllegalStateException("GEMINI_API_KEY is not set. Rebuild with Clean Project.")
        }

        val audioFile = File(chunk.filePath)
        if (!audioFile.exists()) {
            throw IllegalStateException("Audio file not found: ${chunk.filePath}")
        }

        val audioBytes = audioFile.readBytes()
        Log.d(TAG, "Audio file size: ${audioBytes.size} bytes, path: ${chunk.filePath}")
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(inlineData = InlineData(mimeType = "audio/wav", data = base64Audio)),
                        GeminiPart(text = "Transcribe this audio accurately. Return only the raw transcription text, no formatting or labels.")
                    )
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.1f,
                maxOutputTokens = 4096
            )
        )

        try {
            val response = geminiApi.generateContentV1(GEMINI_MODEL, apiKey, request)

            if (response.error != null) {
                throw IllegalStateException("Gemini API error: ${response.error.code} - ${response.error.message}")
            }

            val text = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text

            return text?.trim() ?: throw IllegalStateException("Empty transcription response from Gemini")
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "Gemini HTTP ${e.code()}: $errorBody")
            throw IllegalStateException("Gemini API HTTP ${e.code()}: $errorBody", e)
        }
    }

    fun observeTranscripts(sessionId: Long): Flow<List<TranscriptEntity>> =
        transcriptDao.observeTranscriptsForSession(sessionId)

    suspend fun getFullTranscript(sessionId: Long): String? =
        transcriptDao.getFullTranscript(sessionId)

    suspend fun getAllPendingOrFailedChunks(): List<AudioChunkEntity> =
        chunkDao.getAllPendingOrFailedChunks()

    suspend fun getPendingOrFailedChunks(sessionId: Long): List<AudioChunkEntity> =
        chunkDao.getPendingOrFailedChunks(sessionId)
}
