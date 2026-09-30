package com.lingualoop.android.testutil

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
import com.lingualoop.android.data.db.CacheEntryDao
import com.lingualoop.android.data.db.CacheEntryEntity
import com.lingualoop.android.data.db.LessonDao
import com.lingualoop.android.data.db.LessonEntity
import com.lingualoop.android.data.db.PendingOpDao
import com.lingualoop.android.data.db.PendingOpEntity
import com.lingualoop.android.data.db.SessionDao
import com.lingualoop.android.data.db.SessionEntity
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

class FakeApi : LinguaLoopApi {
    var failWithIOException: Boolean = false
    var failSubmitWithHttpCode: Int? = null
    var nextLesson: LessonDto? = null
    var nextSession: SessionDto? = null
    val attemptCalls = mutableListOf<AttemptRequest>()
    val completeCalls = mutableListOf<Long>()

    override suspend fun register(body: RegisterRequest): AuthResponse =
        AuthResponse("token", LearnerDto(1, body.email, body.displayName))

    override suspend fun login(body: LoginRequest): AuthResponse =
        AuthResponse("token", LearnerDto(1, body.email, "Learner"))

    override suspend fun lesson(id: Long): LessonDto {
        if (failWithIOException) throw IOException("offline")
        return nextLesson ?: throw IOException("no lesson")
    }

    override suspend fun queue(): QueueResponse = QueueResponse(emptyList(), 0)

    override suspend fun stats(): StatsResponse = StatsResponse(
        attemptsTotal = 0, exercisesStudied = 0, exercisesMastered = 0,
        averageGrade = null, dueNow = 0, sessionsCompleted = 0,
        streak = StreakDto(0, 0), unit = null,
    )

    override suspend fun startSession(body: StartSessionRequest): SessionDto {
        if (failWithIOException) throw IOException("offline")
        return nextSession ?: throw IOException("no session")
    }

    override suspend fun submitAttempt(sessionId: Long, body: AttemptRequest): AttemptResultDto {
        failSubmitWithHttpCode?.let { code ->
            throw HttpException(Response.error<Any>(code, "{}".toResponseBody()))
        }
        if (failWithIOException) throw IOException("offline")
        attemptCalls += body
        return AttemptResultDto(
            attemptId = attemptCalls.size.toLong(),
            exerciseId = body.exerciseId,
            grade = body.grade,
            review = ReviewInfoDto(2.5, 1.0, 1, "2026-10-01T00:00:00Z", body.grade, 0),
        )
    }

    override suspend fun completeSession(sessionId: Long): CompleteSessionResponse {
        if (failWithIOException) throw IOException("offline")
        completeCalls += sessionId
        return CompleteSessionResponse(sessionId, "2026-09-30T10:00:00Z",
            "2026-09-30T10:05:00Z", null, 2, 3.5)
    }
}

class FakeLessonDao : LessonDao {
    val store = mutableMapOf<Long, LessonEntity>()
    override suspend fun upsert(lesson: LessonEntity) { store[lesson.id] = lesson }
    override suspend fun get(id: Long): LessonEntity? = store[id]
    override suspend fun evictAllExcept(keepId: Long) { store.keys.retainAll { it == keepId } }
}

class FakeSessionDao : SessionDao {
    val store = mutableMapOf<Long, SessionEntity>()
    override suspend fun upsert(session: SessionEntity) { store[session.id] = session }
    override suspend fun get(id: Long): SessionEntity? = store[id]
    override suspend fun markInactive(id: Long) {
        store[id]?.let { store[id] = it.copy(active = false) }
    }
}

class FakePendingOpDao : PendingOpDao {
    val ops = mutableListOf<PendingOpEntity>()
    private var nextId = 1L
    override suspend fun insert(op: PendingOpEntity): Long {
        val id = nextId++
        ops += op.copy(id = id)
        return id
    }
    override suspend fun getAllOrdered(): List<PendingOpEntity> = ops.sortedBy { it.id }
    override suspend fun count(): Int = ops.size
    override suspend fun delete(id: Long) { ops.removeAll { it.id == id } }
}

class FakeCacheEntryDao : CacheEntryDao {
    val store = mutableMapOf<String, CacheEntryEntity>()
    override suspend fun upsert(entry: CacheEntryEntity) { store[entry.key] = entry }
    override suspend fun get(key: String): CacheEntryEntity? = store[key]
}
