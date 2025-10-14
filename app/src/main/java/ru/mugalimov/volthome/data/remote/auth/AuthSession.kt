package ru.mugalimov.volthome.data.remote.auth

data class AuthSession(
    val accessToken: String,
    val expiresAtMillis: Long,
    val uid: String? = null,
    val scopes: Set<String> = emptySet(),
    val tokenType: String = "OAuth",
    val refreshId: String? = null
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAtMillis
}