package com.lingualoop.android.di

import com.lingualoop.android.BuildConfig
import com.lingualoop.android.di.ApiBaseUrl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ConfigModule {

    @Provides
    @ApiBaseUrl
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL
}
