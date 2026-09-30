package com.lingualoop.android.sync

import com.google.common.truth.Truth.assertThat
import com.lingualoop.android.data.db.PendingOpEntity
import com.lingualoop.android.testutil.FakeApi
import com.lingualoop.android.testutil.FakePendingOpDao
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OpFlusherTest {

    private fun op(
        id: Long = 0,
        kind: String,
        sessionId: Long,
        exerciseId: Long? = null,
        grade: Int? = null,
    ) = PendingOpEntity(
        id = id, kind = kind, sessionId = sessionId, exerciseId = exerciseId,
        grade = grade, latencyMs = 1500, hintShown = false, createdAt = id,
    )

    @Test
    fun `flushes FIFO so attempts precede their session completion`() = runTest {
        val api = FakeApi()
        val dao = FakePendingOpDao()
        dao.ops += listOf(
            op(1, PendingOpEntity.KIND_ATTEMPT, sessionId = 9, exerciseId = 21, grade = 5),
            op(2, PendingOpEntity.KIND_ATTEMPT, sessionId = 9, exerciseId = 22, grade = 2),
            op(3, PendingOpEntity.KIND_COMPLETE, sessionId = 9),
        )
        val flusher = OpFlusher(api, dao)

        val result = flusher.flush()

        assertThat(result.synced).isEqualTo(3)
        assertThat(result.remaining).isEqualTo(0)
        assertThat(result.stopped).isFalse()
        assertThat(api.attemptCalls.map { it.exerciseId }).containsExactly(21L, 22L).inOrder()
        assertThat(api.completeCalls).containsExactly(9L)
        assertThat(dao.ops).isEmpty()
    }

    @Test
    fun `stops at a network failure and keeps remaining ops`() = runTest {
        val api = FakeApi().apply { failWithIOException = true }
        val dao = FakePendingOpDao()
        dao.ops += listOf(
            op(1, PendingOpEntity.KIND_ATTEMPT, sessionId = 9, exerciseId = 21, grade = 5),
            op(2, PendingOpEntity.KIND_COMPLETE, sessionId = 9),
        )
        val flusher = OpFlusher(api, dao)

        val result = flusher.flush()

        assertThat(result.stopped).isTrue()
        assertThat(result.synced).isEqualTo(0)
        assertThat(result.remaining).isEqualTo(2)
    }

    @Test
    fun `drops ops the server rejects with 4xx`() = runTest {
        val api = FakeApi().apply { failSubmitWithHttpCode = 409 }
        val dao = FakePendingOpDao()
        dao.ops += listOf(
            op(1, PendingOpEntity.KIND_ATTEMPT, sessionId = 9, exerciseId = 21, grade = 5),
        )
        val flusher = OpFlusher(api, dao)

        val result = flusher.flush()

        assertThat(result.synced).isEqualTo(1)
        assertThat(result.remaining).isEqualTo(0)
        assertThat(dao.ops).isEmpty()
    }
}
