package com.lingualoop.android.ui.lesson

import com.lingualoop.android.data.api.LinguaLoopApi
import com.lingualoop.android.data.api.dto.AttemptRequest
import com.lingualoop.android.data.api.dto.AttemptResultDto
import com.lingualoop.android.data.api.dto.AuthResponse
import com.lingualoop.android.data.api.dto.CompleteSessionResponse
import com.lingualoop.android.data.api.dto.LearnerDto
import com.lingualoop.android.data.api.dto.LessonDto
import com.lingualoop.android.data.api.dto.LoginRequest
import com.lingualoop.android.data.api.dto.QueueResponse
import com.lingualoop.android.data.api.dto.RegisterRequest
import com.lingualoop.android.data.api.dto.ReviewInfoDto
import com.lingualoop.android.data.api.dto.SessionDto
import com.lingualoop.android.data.api.dto.StatsResponse
import com.lingualoop.android.data.api.dto.StreakDto
import com.lingualoop.android.data.api.dto.StartSessionRequest

/** androidTest-local fake of the API (unit-test fakes are not visible here). */
class FakeApi : LinguaLoopApi {
    var nextLesson: LessonDto? = null
    var nextSession: SessionDto? = null
    val attemptCalls = mutableListOf<AttemptRequest>()
    val completeCalls = mutableListOf<Long>()

    override suspend fun register(body: RegisterRequest): AuthResponse =
        AuthResponse("token", LearnerDto(1, body.email, body.displayName))

    override suspend fun login(body: LoginRequest): AuthResponse =
        AuthResponse("token", LearnerDto(1, body.email, "Learner"))

    override suspend fun lesson(id: Long): LessonDto = nextLesson!!

    override suspend fun queue(): QueueResponse = QueueResponse(emptyList(), 0)

    override suspend fun stats(): StatsResponse = StatsResponse(
        attemptsTotal = 0, exercisesStudied = 0, exercisesMastered = 0,
        averageGrade = null, dueNow = 0, sessionsCompleted = 0,
        streak = StreakDto(0, 0), unit = null,
    )

    override suspend fun startSession(body: StartSessionRequest): SessionDto = nextSession!!

    override suspend fun submitAttempt(sessionId: Long, body: AttemptRequest): AttemptResultDto {
        attemptCalls += body
        return AttemptResultDto(
            attemptId = attemptCalls.size.toLong(),
            exerciseId = body.exerciseId,
            grade = body.grade,
            review = ReviewInfoDto(2.5, 1.0, 1, "2026-10-01T00:00:00Z", body.grade, 0),
        )
    }

    override suspend fun completeSession(sessionId: Long): CompleteSessionResponse {
        completeCalls += sessionId
        return CompleteSessionResponse(sessionId, "2026-09-30T10:00:00Z",
            "2026-09-30T10:05:00Z", null, attemptCalls.size.toLong(), 3.5)
    }
}
