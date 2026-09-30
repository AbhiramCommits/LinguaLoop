package com.lingualoop.android.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingualoop.android.data.api.dto.AttemptResultDto
import com.lingualoop.android.data.api.dto.CompleteSessionResponse
import com.lingualoop.android.data.api.dto.ExerciseDto
import com.lingualoop.android.data.api.dto.LessonDto
import com.lingualoop.android.data.api.dto.SessionDto
import com.lingualoop.android.data.content.AudioStore
import com.lingualoop.android.data.content.ContentRepository
import com.lingualoop.android.data.session.QueuedOfflineException
import com.lingualoop.android.data.session.SessionRepository
import com.lingualoop.android.di.ApiBaseUrl
import com.lingualoop.android.util.isCorrectAnswer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File
import javax.inject.Inject

enum class AnswerStatus { SUBMITTING, SUCCESS, QUEUED }

data class AnswerState(
    val grade: Int,
    val correct: Boolean,
    val status: AnswerStatus,
)

data class LessonUiState(
    val loading: Boolean = true,
    val lesson: LessonDto? = null,
    val session: SessionDto? = null,
    val currentIndex: Int = 0,
    val answers: Map<Long, AnswerState> = emptyMap(),
    val downloadedAudio: Map<Long, File> = emptyMap(),
    val completed: CompleteSessionResponse? = null,
    val hintRemainingSeconds: Int = 0,
    val error: String? = null,
    val offlineNote: String? = null,
) {
    val currentExercise: ExerciseDto? get() = lesson?.exercises?.getOrNull(currentIndex)
    val allAnswered: Boolean get() = lesson != null && answers.size >= lesson.exercises.size
    val hintAvailable: Boolean get() = hintRemainingSeconds <= 0
}

@HiltViewModel
class LessonViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val sessionRepository: SessionRepository,
    private val audioStore: AudioStore,
    @ApiBaseUrl private val apiBaseUrl: String,
) : ViewModel() {

    private val _state = MutableStateFlow(LessonUiState())
    val state: StateFlow<LessonUiState> = _state.asStateFlow()

    private var latencyStartedAt = System.currentTimeMillis()
    private var hintJob: Job? = null

    fun load(lessonId: Long) {
        if (_state.value.lesson != null) return
        viewModelScope.launch {
            try {
                val lesson = contentRepository.lesson(lessonId)
                _state.update { it.copy(loading = false, lesson = lesson) }
                startSession(lessonId)
                restartPerExercise()
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = error.message ?: "Could not load the lesson")
                }
            }
        }
    }

    fun next() {
        _state.update { it.copy(currentIndex = minOf(it.currentIndex + 1, (it.lesson?.exercises?.size ?: 1) - 1)) }
        restartPerExercise()
    }

    fun previous() {
        _state.update { it.copy(currentIndex = maxOf(it.currentIndex - 1, 0)) }
        restartPerExercise()
    }

    fun answer(exercise: ExerciseDto, text: String, hintShown: Boolean) {
        val session = _state.value.session ?: return
        if (_state.value.answers.containsKey(exercise.id)) return
        val correct = isCorrectAnswer(text, exercise.answer)
        val grade = if (correct) 5 else 2
        val latencyMs = (System.currentTimeMillis() - latencyStartedAt).toInt().coerceAtLeast(0)

        _state.update { current ->
            current.copy(
                answers = current.answers + (exercise.id to AnswerState(grade, correct, AnswerStatus.SUBMITTING)),
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                sessionRepository.submitAttempt(
                    sessionId = session.id,
                    exerciseId = exercise.id,
                    grade = grade,
                    latencyMs = latencyMs,
                    hintShown = hintShown,
                )
                _state.update { current ->
                    current.copy(answers = current.answers + (exercise.id to AnswerState(grade, correct, AnswerStatus.SUCCESS)))
                }
            } catch (error: QueuedOfflineException) {
                _state.update { current ->
                    current.copy(
                        answers = current.answers + (exercise.id to AnswerState(grade, correct, AnswerStatus.QUEUED)),
                        offlineNote = "Saved on this device — it will sync when you are back online.",
                    )
                }
            } catch (error: Exception) {
                // Real server rejection: roll the optimistic answer back.
                _state.update { current ->
                    val answers = current.answers.toMutableMap()
                    answers.remove(exercise.id)
                    current.copy(
                        answers = answers,
                        error = if (error is HttpException) "The server rejected this answer"
                        else error.message ?: "Could not save your answer",
                    )
                }
            }
        }
    }

    fun complete() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            try {
                val summary = sessionRepository.complete(session.id)
                _state.update { it.copy(completed = summary, offlineNote = null) }
            } catch (error: QueuedOfflineException) {
                val grades = _state.value.answers.values.map { it.grade }
                _state.update {
                    it.copy(
                        completed = CompleteSessionResponse(
                            id = session.id,
                            startedAt = session.startedAt,
                            endedAt = null,
                            variantKey = session.variantKey,
                            attemptCount = grades.size.toLong(),
                            averageGrade = if (grades.isEmpty()) null else grades.average(),
                        ),
                        offlineNote = "Session finished offline — it will sync when connectivity returns.",
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Could not complete the session") }
            }
        }
    }

    fun ensureAudio(assetId: Long) {
        viewModelScope.launch {
            val file = audioStore.localFile(assetId)
                ?: audioStore.ensureDownloaded(assetId, apiBaseUrl)
            if (file != null) {
                _state.update { it.copy(downloadedAudio = it.downloadedAudio + (assetId to file)) }
            }
        }
    }

    private suspend fun startSession(lessonId: Long) {
        try {
            val session = sessionRepository.start(lessonId)
            _state.update { it.copy(session = session) }
        } catch (error: Exception) {
            _state.update {
                it.copy(error = "Sessions must be started online. Connect and reopen the lesson.")
            }
        }
    }

    private fun restartPerExercise() {
        latencyStartedAt = System.currentTimeMillis()
        hintJob?.cancel()
        val delaySeconds = _state.value.session?.hintDelaySeconds ?: 0
        if (delaySeconds <= 0) {
            _state.update { it.copy(hintRemainingSeconds = 0) }
            return
        }
        _state.update { it.copy(hintRemainingSeconds = delaySeconds) }
        hintJob = viewModelScope.launch {
            while (_state.value.hintRemainingSeconds > 0) {
                delay(1000)
                _state.update { it.copy(hintRemainingSeconds = maxOf(0, it.hintRemainingSeconds - 1)) }
            }
        }
    }
}
