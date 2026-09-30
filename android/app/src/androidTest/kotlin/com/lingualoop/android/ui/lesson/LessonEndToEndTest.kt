package com.lingualoop.android.ui.lesson

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lingualoop.android.data.api.dto.ExerciseDto
import com.lingualoop.android.data.api.dto.LessonDto
import com.lingualoop.android.data.api.dto.SessionDto
import com.lingualoop.android.data.content.AudioStore
import com.lingualoop.android.data.content.ContentRepository
import com.lingualoop.android.data.db.LinguaLoopDatabase
import com.lingualoop.android.data.session.SessionRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Completes a two-exercise TRANSLATE lesson end to end through the real
 * LessonViewModel + LessonScreen against a fake API: answer both exercises,
 * complete the session, verify the summary. Requires a device/emulator:
 * ./gradlew connectedDebugAndroidTest
 */
@OptIn(ExperimentalTestApi::class)
class LessonEndToEndTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var api: FakeApi
    private lateinit var viewModel: LessonViewModel

    private val lesson = LessonDto(
        id = 1, unitId = 1, title = "Saludos básicos", position = 1,
        exercises = listOf(
            ExerciseDto(11, "TRANSLATE", "Good morning", "Buenos días"),
            ExerciseDto(12, "TRANSLATE", "Good night", "Buenas noches"),
        ),
    )

    @Before
    fun setUp() {
        api = FakeApi().apply {
            nextLesson = lesson
            nextSession = SessionDto(9, 1, null, 0, "2026-09-30T10:00:00Z", 2)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LinguaLoopDatabase::class.java).build()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val content = ContentRepository(api, database.lessonDao(), database.cacheEntryDao(), json)
        val sessions = SessionRepository(api, database.sessionDao(), database.pendingOpDao())
        val audio = AudioStore(File(context.cacheDir, "audio-test"), OkHttpClient())
        viewModel = LessonViewModel(content, sessions, audio, "http://localhost:0")
    }

    @Test
    fun completesALessonEndToEnd() {
        composeRule.setContent {
            LessonScreen(lessonId = 1, viewModel = viewModel, onBackHome = {})
        }

        // Exercise 1: answer correctly (diacritics-insensitive grading).
        composeRule.waitUntilAtLeastOneExists(hasText("Exercise 1 of 2"), 5_000)
        composeRule.onNodeWithText("Your translation").performClick()
        composeRule.onNodeWithText("Your translation").performTextReplacement("Buenos dias")
        composeRule.onNodeWithText("Check").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Correct!"), 5_000)
        composeRule.onNodeWithText("Next").assertIsEnabled().performClick()

        // Exercise 2: answer incorrectly -> the feedback announces the right answer.
        composeRule.waitUntilAtLeastOneExists(hasText("Exercise 2 of 2"), 5_000)
        composeRule.onNodeWithText("Your translation").performTextReplacement("nope")
        composeRule.onNodeWithText("Check").performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasText("Incorrect — the correct answer is Buenas noches"),
            5_000,
        )

        composeRule.onNodeWithText("Complete session").assertIsEnabled().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Session complete"), 5_000)
        composeRule.waitUntilAtLeastOneExists(hasText("Exercises attempted: 2"), 5_000)
        composeRule.waitUntilAtLeastOneExists(hasText("Average grade: 3.50 / 5"), 5_000)
        composeRule.waitUntilAtLeastOneExists(hasText("Session saved."), 5_000)
    }
}
