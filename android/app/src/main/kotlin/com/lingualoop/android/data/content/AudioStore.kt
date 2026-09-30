package com.lingualoop.android.data.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads audio assets (/api/audio/{id}, immutable) into app-private
 * storage so LISTEN exercises play offline. The server serves Opus by
 * default with an MP3 fallback.
 */
@Singleton
class AudioStore @Inject constructor(
    private val audioDir: File,
    private val httpClient: OkHttpClient,
) {
    fun localFile(assetId: Long): File? =
        File(audioDir, "$assetId").takeIf { it.exists() && it.length() > 0 }

    suspend fun ensureDownloaded(assetId: Long, baseUrl: String): File? {
        localFile(assetId)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url("${baseUrl.trimEnd('/')}/api/audio/$assetId").build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    audioDir.mkdirs()
                    val target = File(audioDir, "$assetId")
                    response.body?.byteStream()?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.takeIf { it.length() > 0 }
                }
            }.getOrNull()
        }
    }
}
