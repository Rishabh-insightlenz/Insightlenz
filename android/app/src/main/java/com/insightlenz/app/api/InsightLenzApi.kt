package com.insightlenz.app.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * !! IMPORTANT !!
 * Change this to your Mac Mini's local IP address.
 * On your Mac, run:  ifconfig | grep "inet " | grep -v 127
 * You'll see something like: inet 192.168.1.42
 * Set that IP here. The phone and Mac must be on the same WiFi.
 */
const val BACKEND_BASE_URL = "http://192.168.29.238:8000/"

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
