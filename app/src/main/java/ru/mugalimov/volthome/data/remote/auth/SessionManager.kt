package ru.mugalimov.volthome.data.remote.auth

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val prefs: SharedPreferences
) {
    companion object {
        private const val K_TOKEN = "session_jwt"
        private const val K_EXPIRES_MS = "expires_at_ms"
        private const val K_REFRESH_ID = "refresh_id"

        /** Запас времени перед истечением токена, при котором начинаем обновление. */
        const val DEFAULT_LEEWAY_SECONDS: Long = 120
    }

    private val io = Dispatchers.IO

    suspend fun save(
        sessionJwt: String,
        expiresAtEpochSeconds: Long,
        refreshId: String?
    ) = withContext(io) {
        prefs.edit()
            .putString(K_TOKEN, sessionJwt)
            .putLong(K_EXPIRES_MS, expiresAtEpochSeconds * 1000L)
            .apply()
        if (refreshId != null) {
            prefs.edit().putString(K_REFRESH_ID, refreshId).apply()
        }
    }

    suspend fun clear() = withContext(io) {
        prefs.edit()
            .remove(K_TOKEN)
            .remove(K_EXPIRES_MS)
            .remove(K_REFRESH_ID)
            .apply()
    }

    suspend fun load(): AuthSession? = withContext(io) {
        val token = prefs.getString(K_TOKEN, null) ?: return@withContext null
        val expMs = prefs.getLong(K_EXPIRES_MS, 0L)
        val rid = prefs.getString(K_REFRESH_ID, null)
        AuthSession(
            accessToken = token,
            expiresAtMillis = expMs,
            tokenType = "Bearer",
            refreshId = rid
        )
    }

    suspend fun hasValidSession(leewaySeconds: Long = DEFAULT_LEEWAY_SECONDS): Boolean {
        val s = load() ?: return false
        val now = System.currentTimeMillis()
        val leewayMs = TimeUnit.SECONDS.toMillis(leewaySeconds)
        val expired = now >= s.expiresAtMillis
        val expiringSoon = now + leewayMs >= s.expiresAtMillis
        return !expired && !expiringSoon
    }

    suspend fun needsRefresh(leewaySeconds: Long = DEFAULT_LEEWAY_SECONDS): Boolean {
        val s = load() ?: return false
        val now = System.currentTimeMillis()
        val leewayMs = TimeUnit.SECONDS.toMillis(leewaySeconds)
        return now >= s.expiresAtMillis || (now + leewayMs >= s.expiresAtMillis)
    }

    suspend fun currentBearerOrNull(): String? = load()?.let { "${it.tokenType} ${it.accessToken}" }

    suspend fun refreshIdOrNull(): String? = withContext(io) {
        prefs.getString(K_REFRESH_ID, null)
    }
}