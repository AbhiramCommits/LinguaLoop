package com.lingualoop.android.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors the shared LinguaLoop REST contract exactly (see docs/porting.md).
// Field names must not drift: no API changes are allowed from this client.

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val timezone: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LearnerDto(
    val id: Long,
    val email: String,
    val displayName: String,
    val timezone: String? = null,
)

@Serializable
data class AuthResponse(
    val token: String,
    val learner: LearnerDto,
)

@Serializable
data class AudioAssetDto(
    val id: Long,
    val durationMs: Int,
    val bytes: Long,
)

@Serializable
data class ExerciseDto(
    val id: Long,
    val type: String, // TRANSLATE | MULTIPLE_CHOICE | LISTEN
    val prompt: String,
    val answer: String,
    val choices: List<String> = emptyList(),
    val caption: String? = null,
    val audioAsset: AudioAssetDto? = null,
) {
    val isTranslate: Boolean get() = type == "TRANSLATE"
    val isMultipleChoice: Boolean get() = type == "MULTIPLE_CHOICE"
    val isListen: Boolean get() = type == "LISTEN"
}

@Serializable
data class LessonDto(
    val id: Long,
    val unitId: Long,
    val title: String,
    val position: Int,
    val exercises: List<ExerciseDto> = emptyList(),
)

@Serializable
data class ReviewInfoDto(
    val easeFactor: Double,
    val intervalDays: Double,
    val repetitions: Int,
    val dueAt: String,
    val lastGrade: Int? = null,
    val lapses: Int,
)

@Serializable
data class QueueItemDto(
    val exerciseId: Long,
    val type: String,
    val prompt: String,
    val answer: String,
    val choices: List<String> = emptyList(),
    val caption: String? = null,
    val audioAsset: AudioAssetDto? = null,
    val lessonId: Long,
    val lessonTitle: String,
    val unitId: Long,
    val unitTitle: String,
    val review: ReviewInfoDto? = null,
    @SerialName("new") val isNew: Boolean,
)

@Serializable
data class QueueResponse(
    val items: List<QueueItemDto>,
    val dueCount: Int,
)

@Serializable
data class StreakDto(
    val currentDays: Int,
    val longestDays: Int,
    val lastActiveDate: String? = null,
)

@Serializable
data class LessonMasteryDto(
    val lessonId: Long,
    val title: String,
    val mastery: Double,
    val masteredExercises: Long,
    val totalExercises: Long,
)

@Serializable
data class UnitMasteryDto(
    val unitId: Long,
    val title: String,
    val mastery: Double,
    val lessons: List<LessonMasteryDto> = emptyList(),
)

@Serializable
data class StatsResponse(
    val attemptsTotal: Long,
    val exercisesStudied: Long,
    val exercisesMastered: Long,
    val averageGrade: Double? = null,
    val dueNow: Long,
    val sessionsCompleted: Long,
    val streak: StreakDto,
    val unit: UnitMasteryDto? = null,
)

@Serializable
data class StartSessionRequest(
    val lessonId: Long,
)

@Serializable
data class SessionDto(
    val id: Long,
    val lessonId: Long,
    val variantKey: String? = null,
    val hintDelaySeconds: Int? = null,
    val startedAt: String,
    val exerciseCount: Long,
)

@Serializable
data class AttemptRequest(
    val exerciseId: Long,
    val grade: Int,
    val latencyMs: Int,
    val hintShown: Boolean? = null,
)

@Serializable
data class AttemptResultDto(
    val attemptId: Long,
    val exerciseId: Long,
    val grade: Int,
    val review: ReviewInfoDto,
)

@Serializable
data class CompleteSessionResponse(
    val id: Long,
    val startedAt: String,
    val endedAt: String? = null,
    val variantKey: String? = null,
    val attemptCount: Long,
    val averageGrade: Double? = null,
)
