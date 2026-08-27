package ru.mugalimov.volthome.domain.model

data class UserProfile(
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val plan: String,
    val planUntilEpochSeconds: Long?
)
