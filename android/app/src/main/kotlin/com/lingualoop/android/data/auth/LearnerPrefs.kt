package com.lingualoop.android.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class LearnerPrefs(
    val id: Long,
    val email: String,
    val displayName: String,
    val timezone: String? = null,
)
