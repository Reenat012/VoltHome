package ru.mugalimov.volthome.data.remote.auth

import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.prefs.EncryptedPrefsProvider

@Singleton
class SessionManager @Inject constructor(
    private val prefsProvider: EncryptedPrefsProvider
) {
    companion object {
        private const val TAG = "SessionManager"

        private const val K_TOKEN = "session_jwt"
        private const val K_EXPIRES_MS = "expires_at_ms"
        private const val K_REFRESH_ID = "refresh_id"
        private const val K_UID = "session_uid"

        const val DEFAULT_LEEWAY_SECONDS: Long = 120
    }

    private val io = Dispatchers.IO

    data class AuthSession(
        val accessToken: String,
        val tokenType: String = "Bearer",
        val expiresAtMillis: Long,
        val refreshId: String?,
        val uid: String?
    )

    private suspend fun prefs(): SharedPreferences = prefsProvider.get()

    /**
     * Сохраняем серверную сессию.
     *
     * Важно:
     * - uid может быть null на старом сервере;
     * - commit используем синхронно, чтобы значение было доступно сразу после возврата.
     */
    suspend fun save(
        sessionJwt: String,
        expiresAtEpochSeconds: Long,
        refreshId: String?,
        uid: String?
    ) = withContext(io) {
        val p = prefs()

        val editor = p.edit()
            .putString(K_TOKEN, sessionJwt)
            .putLong(K_EXPIRES_MS, expiresAtEpochSeconds * 1000L)

        if (refreshId != null) {
            editor.putString(K_REFRESH_ID, refreshId)
        } else {
            editor.remove(K_REFRESH_ID)
        }

        if (uid != null) {
            editor.putString(K_UID, uid)
        } else {
            editor.remove(K_UID)
        }

        editor.commit()

        // Временный диагностический лог для проверки, что uid реально сохраняется.
        Log.d(
            TAG,
            "SESSION_SAVE uid=$uid refreshIdPresent=${!refreshId.isNullOrBlank()}"
        )
    }

    suspend fun clear() = withContext(io) {
        val p = prefs()
        p.edit()
            .remove(K_TOKEN)
            .remove(K_EXPIRES_MS)
            .remove(K_REFRESH_ID)
            .remove(K_UID)
            .commit()
    }

    suspend fun load(): AuthSession? = withContext(io) {
        val p = prefs()
        val token = p.getString(K_TOKEN, null) ?: return@withContext null
        val expMs = p.getLong(K_EXPIRES_MS, 0L)
        val rid = p.getString(K_REFRESH_ID, null)
        val uid = p.getString(K_UID, null)

        // Временный диагностический лог для проверки, что uid реально читается обратно.
        Log.d(
            TAG,
            "SESSION_LOAD uid=$uid refreshIdPresent=${!rid.isNullOrBlank()}"
        )

        AuthSession(
            accessToken = token,
            expiresAtMillis = expMs,
            tokenType = "Bearer",
            refreshId = rid,
            uid = uid
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

    suspend fun currentBearerOrNull(): String? =
        load()?.let { "${it.tokenType} ${it.accessToken}" }

    suspend fun refreshTokenOrNull(): String? =
        withContext(io) { prefs().getString(K_REFRESH_ID, null) }

    /**
     * Отдельный геттер uid.
     * Полезен для recovery-сценариев и фоновых оркестраторов.
     */
    suspend fun currentUidOrNull(): String? =
        withContext(io) { prefs().getString(K_UID, null) }
}