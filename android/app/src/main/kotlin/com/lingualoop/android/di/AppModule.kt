package com.lingualoop.android.di

import android.content.Context
import com.lingualoop.android.data.api.AuthInterceptor
import com.lingualoop.android.data.api.LinguaLoopApi
import com.lingualoop.android.data.auth.TokenStore
import com.lingualoop.android.data.db.LinguaLoopDatabase
import com.lingualoop.android.data.db.PendingOpDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(tokenStore: TokenStore): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor { tokenStore.tokenValue() })
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    @Provides
    @Singleton
    fun provideApi(
        client: OkHttpClient,
        json: Json,
        @ApiBaseUrl baseUrl: String,
    ): LinguaLoopApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(LinguaLoopApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LinguaLoopDatabase =
        LinguaLoopDatabase.build(context)

    @Provides
    @Singleton
    fun provideAudioDirectory(@ApplicationContext context: Context): java.io.File =
        java.io.File(context.filesDir, "audio").apply { mkdirs() }

    @Provides
    fun providePendingOpDao(database: LinguaLoopDatabase): PendingOpDao = database.pendingOpDao()

    @Provides
    fun provideLessonDao(database: LinguaLoopDatabase): com.lingualoop.android.data.db.LessonDao =
        database.lessonDao()

    @Provides
    fun provideSessionDao(database: LinguaLoopDatabase): com.lingualoop.android.data.db.SessionDao =
        database.sessionDao()

    @Provides
    fun provideCacheEntryDao(database: LinguaLoopDatabase): com.lingualoop.android.data.db.CacheEntryDao =
        database.cacheEntryDao()
}
