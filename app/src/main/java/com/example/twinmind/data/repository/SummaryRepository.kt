package com.example.twinmind.data.repository

import android.util.Log
import com.example.twinmind.BuildConfig
import com.example.twinmind.data.local.dao.SummaryDao
import com.example.twinmind.data.local.dao.TranscriptDao
import com.example.twinmind.data.local.entity.SummaryEntity
import com.example.twinmind.data.remote.api.GeminiApi
import com.example.twinmind.data.remote.api.GeminiContent
import com.example.twinmind.data.remote.api.GeminiPart
import com.example.twinmind.data.remote.api.GeminiRequest
import com.example.twinmind.data.remote.api.GenerationConfig
import com.example.twinmind.data.remote.api.SummaryContent
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryRepository @Inject constructor(
    private val geminiApi: GeminiApi,
    private val summaryDao: SummaryDao,
    private val transcriptDao: TranscriptDao
) {
    companion object {
        private const val TAG = "SummaryRepo"
        private const val GEMINI_MODEL = "gemini-2.5-flash-lite"
    }

    private val gson = Gson()

    fun observeSummary(sessionId: Long): Flow<SummaryEntity?> =
        summaryDao.observeForSession(sessionId)

    suspend fun getSummary(sessionId: Long): SummaryEntity? =
        summaryDao.getForSession(sessionId)

    fun generateSummaryStream(sessionId: Long): Flow<SummaryContent> = flow {
        val transcript = transcriptDao.getFullTranscript(sessionId)
        if (transcript.isNullOrBlank()) {
            throw IllegalStateException("No transcript available for session $sessionId")
        }

        Log.d(TAG, "Starting summary generation for session $sessionId, transcript length: ${transcript.length}")

        val existing = summaryDao.getForSession(sessionId)
        if (existing == null) {
            summaryDao.insert(SummaryEntity(sessionId = sessionId, status = "GENERATING"))
        } else {
            summaryDao.updateStatus(sessionId, "GENERATING")
        }

        val prompt = buildPrompt(transcript)

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.4f,
                maxOutputTokens = 8192
            )
        )

        try {
            val response = geminiApi.generateContentV1(GEMINI_MODEL, BuildConfig.GEMINI_API_KEY, request)

            if (response.error != null) {
                throw IllegalStateException("Gemini API error: ${response.error.code} - ${response.error.message}")
            }

            val rawText = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?: throw IllegalStateException("Empty summary response from Gemini")

            Log.d(TAG, "Raw summary response: ${rawText.take(200)}...")

            val content = parseSummaryJson(rawText.trim())
            emit(content)

            summaryDao.updateContent(
                sessionId,
                content.title,
                content.summary,
                content.actionItems,
                content.keyPoints,
                "COMPLETED"
            )
            Log.d(TAG, "Summary generation completed for session $sessionId")
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "Gemini HTTP ${e.code()}: $errorBody")
            throw IllegalStateException("Gemini API HTTP ${e.code()}: $errorBody", e)
        }
    }

    private fun parseSummaryJson(raw: String): SummaryContent {
        val cleaned = raw
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*$", RegexOption.MULTILINE), "")
            .trim()

        val jsonStr = Regex("\\{[\\s\\S]*\\}").find(cleaned)?.value ?: cleaned

        return try {
            val json = gson.fromJson(jsonStr, JsonObject::class.java)
            SummaryContent(
                title = extractString(json, "title"),
                summary = extractString(json, "summary"),
                actionItems = extractStringOrArray(json, "actionItems", "action_items"),
                keyPoints = extractStringOrArray(json, "keyPoints", "key_points")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse summary JSON, extracting manually", e)
            extractFallback(cleaned)
        }
    }

    private fun extractString(json: JsonObject?, vararg keys: String): String {
        for (key in keys) {
            val element = json?.get(key) ?: continue
            if (element.isJsonPrimitive) return element.asString
        }
        return ""
    }

    private fun extractStringOrArray(json: JsonObject?, vararg keys: String): String {
        for (key in keys) {
            val element = json?.get(key) ?: continue
            if (element.isJsonPrimitive) return element.asString
            if (element.isJsonArray) {
                return element.asJsonArray.joinToString("\n") { item ->
                    val text = item.asString
                    if (text.startsWith("•") || text.startsWith("-")) text else "• $text"
                }
            }
        }
        return ""
    }

    private fun extractFallback(text: String): SummaryContent {
        val lines = text.lines().filter { it.isNotBlank() }
        val title = lines.firstOrNull()
            ?.replace(Regex("^[#*\"{}\\[\\]]+\\s*"), "")
            ?.replace(Regex("[\"]+$"), "")
            ?.trim() ?: ""
        val body = lines.drop(1).joinToString("\n").trim()
        return SummaryContent(title = title, summary = body)
    }

    private fun buildPrompt(transcript: String): String {
        return """You are meeting notes assistant. Given the following meeting transcript, generate a structured summary.

Return a JSON object with exactly these keys:
- "title": A short title for the meeting (max 10 words)
- "summary": A concise paragraph summarizing the meeting (3-5 sentences)
- "actionItems": Bullet points of action items, each on a new line starting with •
- "keyPoints": Bullet points of key takeaways, each on a new line starting with •

Transcript:
$transcript"""
    }

    suspend fun updateSummaryContent(sessionId: Long, content: SummaryContent, status: String) {
        summaryDao.updateContent(
            sessionId = sessionId,
            title = content.title,
            summary = content.summary,
            actionItems = content.actionItems,
            keyPoints = content.keyPoints,
            status = status
        )
    }
}
