package com.lingualoop.android.ui.lesson

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lingualoop.android.MainDispatcherRule
import com.lingualoop.android.data.api.dto.ExerciseDto
import com.lingualoop.android.data.api.dto.LessonDto
import com.lingualoop.android.data.api.dto.SessionDto
import com.lingualoop.android.data.content.AudioStore
import com.lingualoop.android.data.content.ContentRepository
import com.lingualoop.android.data.session.SessionRepository
import com.lingualoop.android.testutil.FakeApi
import com.lingualoop.android.testutil.FakeCacheEntryDao
import com.lingualoop.android.testutil.FakeLessonDao
import com.lingualoop.android.testutil.FakePendingOpDao
import com.lingualoop.android.testutil.FakeSessionDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import java.io.File

class LessonViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val lesson = LessonDto(
        id = 1, unitId = 1, title = "Saludos básicos", position = 1,
        exercises = listOf(
            ExerciseDto(1, "TRANSLATE", "Good morning", "Buenos días"),
            ExerciseDto(2, "TRANSLATE", "Good night", "Buenas noches"),
        ),
    )

    private fun viewModel(
        api: FakeApi,
        pendingOps: FakePendingOpDao = FakePendingOpDao(),
        lessonDao: FakeLessonDao = FakeLessonDao(),
    ): LessonViewModel {
        val repositories = repositories(api, pendingOps, lessonDao)
        return LessonViewModel(repositories.first, repositories.second, repositories.third, "http://localhost")
    }

    private fun repositories(
        api: FakeApi,
        pendingOps: FakePendingOpDao,
        lessonDao: FakeLessonDao,
    ): Triple<ContentRepository, SessionRepository, AudioStore> {
        val content = ContentRepository(api, lessonDao, FakeCacheEntryDao(), json)
        val sessions = SessionRepository(api, FakeSessionDao(), pendingOps)
        val audio = AudioStore(File.createTempFile("audio", "").parentFile!!, OkHttpClient())
        return Triple(content, sessions, audio)
    }

    @Test
    fun `correct and incorrect answers map to grades and reach SUCCESS`() = runTest {
        val api = FakeApi().apply {
            nextLesson = lesson
            nextSession = SessionDto(9, 1, null, 0, "2026-09-30T10:00:00Z", 2)
        }
        val vm = viewModel(api)

        vm.state.test {
            vm.load(1)
            // settle: loading -> lesson + session
            var state = expectMostRecentItem()
            while (state.loading || state.lesson == null || state.session == null) {
                state = expectMostRecentItem()
            }

            vm.answer(lesson.exercises[0], "Buenos dias", hintShown = false)
            state = expectMostRecentItem()
            assertThat(state.answers[1]?.status).isEqualTo(AnswerStatus.SUCCESS)
            assertThat(state.answers[1]?.grade).isEqualTo(5)
            assertThat(state.answers[1]?.correct).isTrue()

            vm.answer(lesson.exercises[1], "wrong answer", hintShown = false)
            state = expectMostRecentItem()
            assertThat(state.answers[2]?.status).isEqualTo(AnswerStatus.SUCCESS)
            assertThat(state.answers[2]?.grade).isEqualTo(2)
            assertThat(state.answers[2]?.correct).isFalse()

            assertThat(api.attemptCalls).hasSize(2)
            assertThat(api.attemptCalls[0].grade).isEqualTo(5)
            assertThat(api.attemptCalls[1].hintShown).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offline attempts are queued locally and marked QUEUED`() = runTest {
        val pendingOps = FakePendingOpDao()
        val api = FakeApi().apply {
            nextLesson = lesson
            nextSession = SessionDto(9, 1, null, 0, "2026-09-30T10:00:00Z", 2)
        }
        val vm = viewModel(api, pendingOps)

        vm.state.test {
            vm.load(1)
            var state = expectMostRecentItem()
            while (state.lesson == null || state.session == null) {
                state = expectMostRecentItem()
            }

            api.failWithIOException = true
            vm.answer(lesson.exercises[0], "Buenos días", hintShown = false)
            state = expectMostRecentItem()

            assertThat(state.answers[1]?.status).isEqualTo(AnswerStatus.QUEUED)
            assertThat(state.answers[1]?.correct).isTrue()
            assertThat(state.offlineNote).contains("sync")
            assertThat(pendingOps.ops).hasSize(1)
            assertThat(pendingOps.ops[0].kind).isEqualTo("ATTEMPT")
            assertThat(pendingOps.ops[0].grade).isEqualTo(5)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `server errors roll the optimistic answer back`() = runTest {
        val api = FakeApi().apply {
            nextLesson = lesson
            nextSession = SessionDto(9, 1, null, 0, "2026-09-30T10:00:00Z", 2)
        }
        val vm = viewModel(api)

        vm.state.test {
            vm.load(1)
            var state = expectMostRecentItem()
            while (state.lesson == null || state.session == null) {
                state = expectMostRecentItem()
            }

            api.failSubmitWithHttpCode = 409
            vm.answer(lesson.exercises[0], "Buenos días", hintShown = false)
            state = expectMostRecentItem()

            assertThat(state.answers).isEmpty()
            assertThat(state.error).contains("rejected")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing offline produces a local summary and queues the completion`() = runTest {
        val pendingOps = FakePendingOpDao()
        val api = FakeApi().apply {
            nextLesson = lesson
            nextSession = SessionDto(9, 1, null, 12, "2026-09-30T10:00:00Z", 2)
        }
        val vm = viewModel(api, pendingOps)

        vm.state.test {
            vm.load(1)
            var state = expectMostRecentItem()
            while (state.lesson == null || state.session == null) {
                state = expectMostRecentItem()
            }
            api.failWithIOException = true
            vm.answer(lesson.exercises[0], "Buenos días", false)
            state = expectMostRecentItem()
            vm.answer(lesson.exercises[1], "not even close", false)
            state = expectMostRecentItem()
            assertThat(state.allAnswered).isTrue()

            vm.complete()
            state = expectMostRecentItem()

            assertThat(state.completed).isNotNull()
            assertThat(state.completed!!.attemptCount).isEqualTo(2)
            assertThat(state.completed!!.averageGrade).isEqualTo(3.5)
            assertThat(state.offlineNote).contains("offline")
            assertThat(pendingOps.ops.count { it.kind == "COMPLETE" }).isEqualTo(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hint delay from the session payload is counted down`() = runTest {
        val api = FakeApi().apply {
            nextLesson = lesson
            nextSession = SessionDto(9, 1, null, 12, "2026-09-30T10:00:00Z", 2)
        }
        val vm = viewModel(api)

        vm.state.test {
            vm.load(1)
            var state = expectMostRecentItem()
            while (state.lesson == null || state.session == null || state.hintRemainingSeconds == 0) {
                state = expectMostRecentItem()
            }
            assertThat(state.hintRemainingSeconds).isEqualTo(12)
            assertThat(state.hintAvailable).isFalse()

            testScheduler.advanceTimeBy(12_000)
            testScheduler.runCurrent()

            state = expectMostRecentItem()
            assertThat(state.hintRemainingSeconds).isEqualTo(0)
            assertThat(state.hintAvailable).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
