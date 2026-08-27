package ru.mugalimov.volthome.data.local.auth

enum class LocalAuthProvider {
    GUEST,
    YANDEX
}

data class LocalAuthSession(
    val provider: LocalAuthProvider,
    val uid: String,
    val displayName: String,
    val email: String? = null,
    val avatarUrl: String? = null
)
