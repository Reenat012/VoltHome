package ru.mugalimov.volthome.data.local.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.prefs.EncryptedPrefsProvider

data class YandexCredential(
    val accessToken: String,
    val receivedAtEpochSeconds: Long,
    /** null означает, что срок старого токена неизвестен. */
    val expiresAtEpochSeconds: Long?
)

@Singleton
class YandexCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
    private val prefsProvider: EncryptedPrefsProvider
) {
    private companion object {
        const val KEY_TOKEN = "yandex_oauth_token_v2"
        const val KEY_RECEIVED_AT = "yandex_oauth_received_at_v2"
        const val KEY_EXPIRES_AT = "yandex_oauth_expires_at_v2"
        const val LEGACY_TOKEN = "ya_access_token"
        const val CONTROL_PREFS = "auth_credential_control"
        const val KEY_CLEAR_PENDING = "credential_clear_pending"
    }
    private val controlPrefs =
        context.getSharedPreferences(CONTROL_PREFS, Context.MODE_PRIVATE)

    suspend fun save(token: String, expiresInSeconds: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis() / 1000L
                val expiresAt = expiresInSeconds
                    .takeIf { it > 0L }
                    ?.let { now + it }
                val editor = prefsProvider.get().edit()
                    .putString(KEY_TOKEN, token)
                    .putLong(KEY_RECEIVED_AT, now)
                if (expiresAt == null) editor.remove(KEY_EXPIRES_AT)
                else editor.putLong(KEY_EXPIRES_AT, expiresAt)
                check(editor.commit()) { "Credential commit failed" }
                controlPrefs.edit().putBoolean(KEY_CLEAR_PENDING, false).commit()
                Unit
            }
        }

    suspend fun load(): Result<YandexCredential?> = withContext(Dispatchers.IO) {
        runCatching {
            if (controlPrefs.getBoolean(KEY_CLEAR_PENDING, false)) {
                clearEncryptedCredential()
                controlPrefs.edit().putBoolean(KEY_CLEAR_PENDING, false).commit()
                return@runCatching null
            }

            val prefs = prefsProvider.get()
            val currentToken = prefs.getString(KEY_TOKEN, null)
            if (!currentToken.isNullOrBlank()) {
                return@runCatching YandexCredential(
                    accessToken = currentToken,
                    receivedAtEpochSeconds = prefs.getLong(KEY_RECEIVED_AT, 0L),
                    expiresAtEpochSeconds = prefs.getLong(KEY_EXPIRES_AT, 0L)
                        .takeIf { it > 0L }
                )
            }

            val legacyToken = prefs.getString(LEGACY_TOKEN, null)
                ?.takeIf(String::isNotBlank)
                ?: return@runCatching null

            val now = System.currentTimeMillis() / 1000L
            val committed = prefs.edit()
                .putString(KEY_TOKEN, legacyToken)
                .putLong(KEY_RECEIVED_AT, now)
                .remove(KEY_EXPIRES_AT)
                .commit()
            check(committed) { "Legacy credential migration failed" }
            YandexCredential(legacyToken, now, null)
        }
    }

    suspend fun clear(): Result<Unit> = withContext(Dispatchers.IO) {
        val result = runCatching {
            clearEncryptedCredential()
        }
        controlPrefs.edit()
            .putBoolean(KEY_CLEAR_PENDING, result.isFailure)
            .commit()
        result
    }

    private suspend fun clearEncryptedCredential() {
        val committed = prefsProvider.get().edit()
            .remove(KEY_TOKEN)
            .remove(KEY_RECEIVED_AT)
            .remove(KEY_EXPIRES_AT)
            .remove(LEGACY_TOKEN)
            .remove("session_jwt")
            .remove("expires_at_ms")
            .remove("refresh_id")
            .remove("session_uid")
            .commit()
        check(committed) { "Credential clear failed" }
    }
}
