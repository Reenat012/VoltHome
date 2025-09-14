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
        private const val K_ACCESS = "access_token"
        private const val K_EXPIRES = "expires_at"
        private const val K_UID = "uid"
        private const val K_SCOPES = "scopes"
        private const val K_TYPE = "token_type"
    }

    suspend fun save(session: AuthSession) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(K_ACCESS, session.accessToken)
            .putLong(K_EXPIRES, session.expiresAtMillis)
            .putString(K_UID, session.uid)
            // сохраняем scopes через запятую
            .putString(K_SCOPES, session.scopes.joinToString(","))
            .putString(K_TYPE, session.tokenType)
            .apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(K_ACCESS)
            .remove(K_EXPIRES)
            .remove(K_UID)
            .remove(K_SCOPES)
            .remove(K_TYPE)
            .apply()
    }

    suspend fun load(): AuthSession? = withContext(Dispatchers.IO) {
        val token = prefs.getString(K_ACCESS, null) ?: return@withContext null
        val exp = prefs.getLong(K_EXPIRES, 0L)
        val uid = prefs.getString(K_UID, null)
        val scopesRaw = prefs.getString(K_SCOPES, "") ?: ""
        // поддерживаем и пробелы, и запятые
        val scopes = scopesRaw.split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .toSet()
        val type = prefs.getString(K_TYPE, "OAuth") ?: "OAuth"
        AuthSession(
            accessToken = token,
            expiresAtMillis = exp,
            uid = uid,
            scopes = scopes,
            tokenType = type
        )
    }
}