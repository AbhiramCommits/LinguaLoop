package com.lingualoop.android.data.session

import com.lingualoop.android.data.api.LinguaLoopApi
import com.lingualoop.android.data.api.dto.AttemptRequest
import com.lingualoop.android.data.api.dto.AttemptResultDto
import com.lingualoop.android.data.api.dto.CompleteSessionResponse
import com.lingualoop.android.data.api.dto.SessionDto
import com.lingualoop.android.data.api.dto.StartSessionRequest
import com.lingualoop.android.data.db.PendingOpDao
import com.lingualoop.android.data.db.PendingOpEntity
import com.lingualoop.android.data.db.SessionDao
import com.lingualoop.android.data.db.SessionEntity
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when a write was queued locally instead of reaching the server. */
class QueuedOfflineException : IOException("Saved on this device; it will sync when connectivity returns")

/**
 * Study sessions: creation requires connectivity (the server issues the
 * session id — an unavoidable contract constraint, see docs/porting.md).
 * Attempts and completion degrade to a local Room queue on network failure
 * and are flushed FIFO by the sync worker once connectivity returns.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val api: LinguaLoopApi,
    private val sessionDao: SessionDao,
    private val pendingOpDao: PendingOpDao,
) {
    suspend fun start(lessonId: Long): SessionDto {
        val session = api.startSession(StartSessionRequest(lessonId = lessonId))
        sessionDao.upsert(
            SessionEntity(
                id = session.id,
                lessonId = session.lessonId,
                variantKey = session.variantKey,
                hintDelaySeconds = session.hintDelaySeconds,
                startedAt = session.startedAt,
                active = true,
            )
        )
        return session
    }

    suspend fun submitAttempt(
        sessionId: Long,
        exerciseId: Long,
        grade: Int,
        latencyMs: Int,
        hintShown: Boolean,
    ): AttemptResultDto {
        val request = AttemptRequest(exerciseId = exerciseId, grade = grade,
            latencyMs = latencyMs, hintShown = hintShown)
        return try {
            api.submitAttempt(sessionId, request)
        } catch (error: IOException) {
            pendingOpDao.insert(
                PendingOpEntity(
                    kind = PendingOpEntity.KIND_ATTEMPT,
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    grade = grade,
                    latencyMs = latencyMs,
                    hintShown = hintShown,
                    createdAt = System.currentTimeMillis(),
                )
            )
            throw QueuedOfflineException()
        } catch (error: HttpException) {
            throw error // real server rejection: let the UI roll back, do not queue
        }
    }

    suspend fun complete(sessionId: Long): CompleteSessionResponse {
        return try {
            api.completeSession(sessionId).also {
                sessionDao.markInactive(sessionId)
            }
        } catch (error: IOException) {
            pendingOpDao.insert(
                PendingOpEntity(
                    kind = PendingOpEntity.KIND_COMPLETE,
                    sessionId = sessionId,
                    createdAt = System.currentTimeMillis(),
                )
            )
            throw QueuedOfflineException()
        }
    }

    suspend fun pendingCount(): Int = pendingOpDao.count()

    suspend fun session(sessionId: Long): SessionEntity? = sessionDao.get(sessionId)
}
