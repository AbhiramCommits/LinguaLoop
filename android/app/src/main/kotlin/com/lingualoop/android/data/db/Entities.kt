package com.lingualoop.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached lesson JSON so a started lesson survives loss of connectivity. */
@Entity(tableName = "lesson_cache")
data class LessonEntity(
    @PrimaryKey val id: Long,
    val unitId: Long,
    val title: String,
    val position: Int,
    val json: String,
    val cachedAt: Long,
)

/** Locally known study sessions (created online; state survives offline). */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: Long,
    val lessonId: Long,
    val variantKey: String?,
    val hintDelaySeconds: Int?,
    val startedAt: String,
    val active: Boolean,
)

/** Offline queue of graded attempts and completions; flushed FIFO by the sync worker. */
@Entity(tableName = "pending_ops")
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String, // "ATTEMPT" | "COMPLETE"
    val sessionId: Long,
    val exerciseId: Long? = null,
    val grade: Int? = null,
    val latencyMs: Int? = null,
    val hintShown: Boolean = false,
    val createdAt: Long,
) {
    companion object {
        const val KIND_ATTEMPT = "ATTEMPT"
        const val KIND_COMPLETE = "COMPLETE"
    }
}

/** Last-known JSON for home data (queue/stats) shown while offline. */
@Entity(tableName = "cache_entries")
data class CacheEntryEntity(
    @PrimaryKey val key: String,
    val json: String,
    val cachedAt: Long,
)
