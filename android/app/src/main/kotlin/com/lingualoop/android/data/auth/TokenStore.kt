package com.lingualoop.android.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JWT + learner identity stored in EncryptedSharedPreferences
 * (AES256-GCM via the Android Keystore).
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "lingualoop_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, null))
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _learner = MutableStateFlow(
        prefs.getString(KEY_LEARNER, null)?.let { runCatching { json.decodeFromString(LearnerPrefs.serializer(), it) }.getOrNull() }
    )
    val learner: StateFlow<LearnerPrefs?> = _learner.asStateFlow()

    fun tokenValue(): String? = _token.value

    fun save(token: String, learner: LearnerPrefs) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_LEARNER, json.encodeToString(LearnerPrefs.serializer(), learner))
            .apply()
        _token.value = token
        _learner.value = learner
    }

    fun clear() {
        prefs.edit().clear().apply()
        _token.value = null
        _learner.value = null
    }

    companion object {
        private const val KEY_TOKEN = "jwt"
        private const val KEY_LEARNER = "learner"
    }
}
