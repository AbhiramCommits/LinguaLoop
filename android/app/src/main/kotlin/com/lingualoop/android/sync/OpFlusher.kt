package com.lingualoop.android.sync

import com.lingualoop.android.data.api.LinguaLoopApi
import com.lingualoop.android.data.api.dto.AttemptRequest
import com.lingualoop.android.data.db.PendingOpDao
import com.lingualoop.android.data.db.PendingOpEntity
import retrofit2.HttpException
import java.io.IOException

/**
 * Pure flush logic (no Android types) so it is unit-testable on the JVM.
 * Replays pending ops FIFO: attempts before the session completion they
 * belong to. Network errors stop the pass (still offline); 4xx rejections
 * are dropped so a stale op cannot poison the queue.
 */
class OpFlusher(
    private val api: LinguaLoopApi,
    private val pendingOpDao: PendingOpDao,
) {
    data class Result(val synced: Int, val remaining: Int, val stopped: Boolean)

    suspend fun flush(): Result {
        var synced = 0
        var stopped = false
        for (op in pendingOpDao.getAllOrdered()) {
            try {
                when (op.kind) {
                    PendingOpEntity.KIND_ATTEMPT -> api.submitAttempt(
                        op.sessionId,
                        AttemptRequest(
                            exerciseId = requireNotNull(op.exerciseId),
                            grade = requireNotNull(op.grade),
                            latencyMs = requireNotNull(op.latencyMs),
                            hintShown = op.hintShown,
                        ),
                    )
                    PendingOpEntity.KIND_COMPLETE -> api.completeSession(op.sessionId)
                }
                pendingOpDao.delete(op.id)
                synced += 1
            } catch (error: IOException) {
                stopped = true
                break
            } catch (error: HttpException) {
                if (error.code() in 400..499) {
                    pendingOpDao.delete(op.id) // server said no; drop the poison pill
                    synced += 1
                } else {
                    stopped = true
                    break
                }
            }
        }
        return Result(synced = synced, remaining = pendingOpDao.count(), stopped = stopped)
    }
}
