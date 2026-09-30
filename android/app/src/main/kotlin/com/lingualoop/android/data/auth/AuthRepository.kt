package com.lingualoop.android.data.auth

import com.lingualoop.android.data.api.LinguaLoopApi
import com.lingualoop.android.data.api.dto.AuthResponse
import com.lingualoop.android.data.api.dto.LoginRequest
import com.lingualoop.android.data.api.dto.RegisterRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: LinguaLoopApi,
    private val tokenStore: TokenStore,
) {
    suspend fun register(email: String, password: String, displayName: String): AuthResponse =
        api.register(RegisterRequest(email = email, password = password, displayName = displayName))
            .also { persist(it) }

    suspend fun login(email: String, password: String): AuthResponse =
        api.login(LoginRequest(email = email, password = password))
            .also { persist(it) }

    private fun persist(auth: AuthResponse) {
        tokenStore.save(
            token = auth.token,
            learner = LearnerPrefs(
                id = auth.learner.id,
                email = auth.learner.email,
                displayName = auth.learner.displayName,
                timezone = auth.learner.timezone,
            ),
        )
    }

    fun logout() = tokenStore.clear()
}
