package ru.mugalimov.volthome.data.remote.auth

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val prefs: SharedPreferences
) {
    companion object {
        //        private const val K_ACCESS = "access_token"
//        private const val K_EXPIRES = "expires_at"
//        private const val K_UID = "uid"
//        private const val K_SCOPES = "scopes"
//        private const val K_TYPE = "token_type"
        private const val K_TOKEN = "session_jwt"
        private const val K_EXPIRES_MS = "expires_at_ms"
        private const val K_REFRESH_ID = "refresh_id"
    }

    suspend fun save(sessionJwt: String, expiresAtEpochSeconds: Long, refreshId: String?) =
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(K_TOKEN, sessionJwt)
                .putLong(K_EXPIRES_MS, expiresAtEpochSeconds * 1000L)
                .apply()
            if (refreshId != null) prefs.edit().putString(K_REFRESH_ID, refreshId).apply()
        }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(K_TOKEN).remove(K_EXPIRES_MS).remove(K_REFRESH_ID).apply()
    }

    suspend fun load(): AuthSession? = withContext(Dispatchers.IO) {
        val token = prefs.getString(K_TOKEN, null) ?: return@withContext null
        val expMs = prefs.getLong(K_EXPIRES_MS, 0L)
        val rid = prefs.getString(K_REFRESH_ID, null)
        AuthSession(
            accessToken = token,
            expiresAtMillis = expMs,
            uid = null,
            scopes = emptySet(),
            tokenType = "Bearer",
            refreshId = rid
        )
    }
}