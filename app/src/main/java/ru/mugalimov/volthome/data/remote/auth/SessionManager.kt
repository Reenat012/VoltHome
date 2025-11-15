package ru.mugalimov.volthome.data.remote.auth

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import ru.mugalimov.volthome.data.local.prefs.EncryptedPrefsProvider

@Singleton
class SessionManager @Inject constructor(
    private val prefsProvider: EncryptedPrefsProvider
) {
    companion object {
        private const val K_TOKEN = "session_jwt"
        private const val K_EXPIRES_MS = "expires_at_ms"
        private const val K_REFRESH_ID = "refresh_id"
        const val DEFAULT_LEEWAY_SECONDS: Long = 120
    }

    private val io = Dispatchers.IO

    data class AuthSession(
        val accessToken: String,
        val tokenType: String = "Bearer",
        val expiresAtMillis: Long,
        val refreshId: String?
    )

    private suspend fun prefs(): SharedPreferences = prefsProvider.get()

    suspend fun save(
        sessionJwt: String,
        expiresAtEpochSeconds: Long,
        refreshId: String?
    ) = withContext(io) {
        val p = prefs()
        val editor = p.edit()
            .putString(K_TOKEN, sessionJwt)
            .putLong(K_EXPIRES_MS, expiresAtEpochSeconds * 1000L)
        if (refreshId != null) editor.putString(K_REFRESH_ID, refreshId)
        editor.commit() // синхронно — чтобы немедленно было видно после возврата
    }

    suspend fun clear() = withContext(io) {
        val p = prefs()
        p.edit()
            .remove(K_TOKEN)
            .remove(K_EXPIRES_MS)
            .remove(K_REFRESH_ID)
            .commit()
    }

    suspend fun load(): AuthSession? = withContext(io) {
        val p = prefs()
        val token = p.getString(K_TOKEN, null) ?: return@withContext null
        val expMs = p.getLong(K_EXPIRES_MS, 0L)
        val rid = p.getString(K_REFRESH_ID, null)
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

    suspend fun refreshTokenOrNull(): String? = withContext(io) { prefs().getString(K_REFRESH_ID, null) }
}