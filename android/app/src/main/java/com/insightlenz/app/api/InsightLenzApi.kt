package com.insightlenz.app.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Backend URL — switch between local (Mac) and production (Render) here.
 *
 * LOCAL (Mac on same WiFi):
 *   "http://192.168.29.238:8000/"
 *
 * PRODUCTION (Render — paste your Render URL below):
 *   "https://insightlenz-backend.onrender.com/"   ← replace with your actual Render URL
 *
 * Once you deploy to Render, set USE_PRODUCTION = true and rebuild.
 */
private const val USE_PRODUCTION = false   // ← flip to true after Render deploy

private const val LOCAL_URL      = "http://192.168.29.238:8000/"
private const val PRODUCTION_URL = "https://YOUR-APP-NAME.onrender.com/"  // ← paste Render URL here

val BACKEND_BASE_URL = if (USE_PRODUCTION) PRODUCTION_URL else LOCAL_URL

interface InsightLenzApiService {

    @GET("health")
    suspend fun health(): HealthResponse

    @POST("intelligence/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @POST("intelligence/decide")
    suspend fun decide(@Body request: ChatRequest): ChatResponse

    @GET("intelligence/morning-brief")
    suspend fun morningBrief(): ChatResponse

    @GET("intelligence/evening-review")
    suspend fun eveningReview(): ChatResponse

    @GET("intelligence/weekly-review")
    suspend fun weeklyReview(): ChatResponse

    @GET("context/")
    suspend fun getUserContext(): UserContextResponse

    @POST("context/app-usage")
    suspend fun syncAppUsage(@Body request: AppUsageSyncRequest): AppUsageSyncResponse

    // ── History ──────────────────────────────────────────────────────────────

    @GET("intelligence/history")
    suspend fun getChatHistory(
        @Query("session_id") sessionId: String,
        @Query("limit") limit: Int = 50
    ): List<HistoryMessage>

    @GET("intelligence/sessions")
    suspend fun getSessions(
        @Query("limit") limit: Int = 20
    ): List<SessionSummary>

    @GET("intelligence/memories")
    suspend fun getMemories(
        @Query("limit") limit: Int = 50
    ): List<MemoryItem>
}

object ApiClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)  // AI responses take time
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val service: InsightLenzApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BACKEND_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InsightLenzApiService::class.java)
    }
}
