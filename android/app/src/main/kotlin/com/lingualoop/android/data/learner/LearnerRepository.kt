package com.lingualoop.android.data.learner

import com.lingualoop.android.data.api.LinguaLoopApi
import com.lingualoop.android.data.api.dto.QueueResponse
import com.lingualoop.android.data.api.dto.StatsResponse
import com.lingualoop.android.data.content.ContentRepository
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Home data (queue + stats): network-first with a last-known snapshot so
 * the home screen still renders offline.
 */
@Singleton
class LearnerRepository @Inject constructor(
    private val api: LinguaLoopApi,
    private val contentRepository: ContentRepository,
    private val json: Json,
) {
    suspend fun queue(): QueueResponse {
        return try {
            api.queue().also {
                contentRepository.cacheSnapshot(KEY_QUEUE, json.encodeToString(QueueResponse.serializer(), it))
            }
        } catch (error: IOException) {
            contentRepository.snapshot(KEY_QUEUE)
                ?.let { runCatching { json.decodeFromString(QueueResponse.serializer(), it) }.getOrNull() }
                ?: throw error
        }
    }

    suspend fun stats(): StatsResponse {
        return try {
            api.stats().also {
                contentRepository.cacheSnapshot(KEY_STATS, json.encodeToString(StatsResponse.serializer(), it))
            }
        } catch (error: IOException) {
            contentRepository.snapshot(KEY_STATS)
                ?.let { runCatching { json.decodeFromString(StatsResponse.serializer(), it) }.getOrNull() }
                ?: throw error
        }
    }

    companion object {
        const val KEY_QUEUE = "queue"
        const val KEY_STATS = "stats"
    }
}
