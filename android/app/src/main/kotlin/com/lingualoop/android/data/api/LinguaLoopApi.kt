package com.lingualoop.android.data.api

import com.lingualoop.android.data.api.dto.AttemptRequest
import com.lingualoop.android.data.api.dto.AttemptResultDto
import com.lingualoop.android.data.api.dto.AuthResponse
import com.lingualoop.android.data.api.dto.CompleteSessionResponse
import com.lingualoop.android.data.api.dto.LessonDto
import com.lingualoop.android.data.api.dto.LoginRequest
import com.lingualoop.android.data.api.dto.QueueResponse
import com.lingualoop.android.data.api.dto.RegisterRequest
import com.lingualoop.android.data.api.dto.SessionDto
import com.lingualoop.android.data.api.dto.StatsResponse
import com.lingualoop.android.data.api.dto.StartSessionRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** The shared LinguaLoop REST contract; identical to the web client's surface. */
interface LinguaLoopApi {

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("api/lessons/{id}")
    suspend fun lesson(@Path("id") id: Long): LessonDto

    @GET("api/learners/me/queue")
    suspend fun queue(): QueueResponse

    @GET("api/learners/me/stats")
    suspend fun stats(): StatsResponse

    @POST("api/sessions")
    suspend fun startSession(@Body body: StartSessionRequest): SessionDto

    @POST("api/sessions/{id}/attempts")
    suspend fun submitAttempt(@Path("id") sessionId: Long, @Body body: AttemptRequest): AttemptResultDto

    @POST("api/sessions/{id}/complete")
    suspend fun completeSession(@Path("id") sessionId: Long): CompleteSessionResponse
}
