package com.lingualoop.android.di

import com.lingualoop.android.data.auth.TokenStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun tokenStore(): TokenStore
}
