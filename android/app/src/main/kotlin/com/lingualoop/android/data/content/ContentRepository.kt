package com.lingualoop.android.data.content

import com.lingualoop.android.data.api.LinguaLoopApi
import com.lingualoop.android.data.api.dto.LessonDto
import com.lingualoop.android.data.db.CacheEntryDao
import com.lingualoop.android.data.db.CacheEntryEntity
import com.lingualoop.android.data.db.LessonDao
import com.lingualoop.android.data.db.LessonEntity
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network-first lesson loading with a Room JSON cache: a lesson that has
 * been opened once can be re-opened (and finished) offline.
 */
@Singleton
class ContentRepository @Inject constructor(
    private val api: LinguaLoopApi,
    private val lessonDao: LessonDao,
    private val cacheEntryDao: CacheEntryDao,
    private val json: Json,
) {
    suspend fun lesson(lessonId: Long): LessonDto {
        return try {
            api.lesson(lessonId).also { cache(it) }
        } catch (error: IOException) {
            lessonDao.get(lessonId)?.let { runCatching { json.decodeFromString(LessonDto.serializer(), it.json) }.getOrNull() }
                ?: throw error
        }
    }

    suspend fun cachedLesson(lessonId: Long): LessonDto? =
        lessonDao.get(lessonId)?.let { runCatching { json.decodeFromString(LessonDto.serializer(), it.json) }.getOrNull() }

    private suspend fun cache(lesson: LessonDto) {
        lessonDao.upsert(
            LessonEntity(
                id = lesson.id,
                unitId = lesson.unitId,
                title = lesson.title,
                position = lesson.position,
                json = json.encodeToString(LessonDto.serializer(), lesson),
                cachedAt = System.currentTimeMillis(),
            )
        )
        lessonDao.evictAllExcept(lesson.id)
    }

    /** Last-known home data for offline display; refreshed on every successful fetch. */
    suspend fun cacheSnapshot(key: String, payload: String) {
        cacheEntryDao.upsert(CacheEntryEntity(key = key, json = payload, cachedAt = System.currentTimeMillis()))
    }

    suspend fun snapshot(key: String): String? = cacheEntryDao.get(key)?.json
}
