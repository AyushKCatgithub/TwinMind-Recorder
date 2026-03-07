package com.example.twinmind.data.remote.api

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("v1beta/models/{model}:streamGenerateContent?alt=sse")
    @Streaming
    suspend fun streamGenerateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): ResponseBody

    @POST("v1/models/{model}:generateContent")
    suspend fun generateContentV1(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("v1/models/{model}:streamGenerateContent?alt=sse")
    @Streaming
    suspend fun streamGenerateContentV1(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): ResponseBody
}

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null
)

data class GeminiPart(
    val text: String? = null,
    val inlineData: InlineData? = null
)

data class InlineData(
    val mimeType: String,
    val data: String
)

data class GenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = null
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null
)

data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)
