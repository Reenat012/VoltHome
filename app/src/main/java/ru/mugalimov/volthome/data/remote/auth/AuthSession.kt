package ru.mugalimov.volthome.data.remote.auth

/**
 * Домашняя модель авторизационной сессии.
 *
 * uid нужен как стабильный идентификатор пользователя,
 * который приходит с сервера и затем используется в recovery-сценариях.
 */
data class AuthSession(
    val accessToken: String,
    val expiresAtMillis: Long,
    val uid: String? = null,
    val scopes: Set<String> = emptySet(),
    val tokenType: String = "Bearer",
    val refreshId: String? = null
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAtMillis
}

/** -> из SessionManager.AuthSession в доменную AuthSession */
fun SessionManager.AuthSession.toAuthSession(): AuthSession =
    AuthSession(
        accessToken = accessToken,
        expiresAtMillis = expiresAtMillis,
        uid = uid,
        tokenType = tokenType,
        refreshId = refreshId
    )

/** <- из доменной AuthSession в SessionManager.AuthSession (если потребуется) */
fun AuthSession.toSessionAuth(): SessionManager.AuthSession =
    SessionManager.AuthSession(
        accessToken = accessToken,
        tokenType = tokenType,
        expiresAtMillis = expiresAtMillis,
        refreshId = refreshId,
        uid = uid
    )