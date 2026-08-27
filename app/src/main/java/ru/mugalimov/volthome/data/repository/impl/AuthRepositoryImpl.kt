package ru.mugalimov.volthome.data.repository.impl

import android.util.Log
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthResult
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.local.auth.LocalAuthProvider
import ru.mugalimov.volthome.data.local.auth.LocalAuthSession
import ru.mugalimov.volthome.data.local.auth.LocalAuthSessionStore
import ru.mugalimov.volthome.data.local.auth.YandexCredentialStore
import ru.mugalimov.volthome.data.remote.yandex.YandexUserRemoteDataSource
import ru.mugalimov.volthome.data.repository.AuthRepository

/** Локальная авторизация. Яндекс ID используется только как необязательный провайдер профиля. */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sessionStore: LocalAuthSessionStore,
    private val credentialStore: YandexCredentialStore,
    private val yandexUserDataSource: YandexUserRemoteDataSource
) : AuthRepository {
    private companion object {
        const val TAG = "AuthRepository"
    }

    override fun loginOptions(): YandexAuthLoginOptions = YandexAuthLoginOptions()

    override suspend fun continueAsGuest(): LocalAuthSession = sessionStore.createGuest()

    override suspend fun handleAuthResult(result: YandexAuthResult): Result<LocalAuthSession> =
        withContext(Dispatchers.IO) {
            when (result) {
                is YandexAuthResult.Success -> runCatching {
                    val accessToken = result.token.value

                    val info = yandexUserDataSource.getUserInfo(accessToken).getOrNull()
                    val session = LocalAuthSession(
                        provider = LocalAuthProvider.YANDEX,
                        uid = info?.id?.takeIf { it.isNotBlank() }
                            ?: stableUidFromToken(accessToken),
                        displayName = info?.bestName?.takeIf { it.isNotBlank() }
                            ?: "Пользователь Яндекс ID",
                        email = info?.email,
                        avatarUrl = info?.avatarUrl()
                    )
                    // Сначала фиксируем долговечную локальную сессию. Недоступность
                    // KeyStore не должна выкидывать пользователя из приложения.
                    sessionStore.save(session)
                    credentialStore.save(
                        token = accessToken,
                        expiresInSeconds = result.token.expiresIn
                    ).onFailure {
                        Log.w(TAG, "Yandex credential was not persisted; local session is preserved", it)
                    }
                    session
                }

                is YandexAuthResult.Failure -> Result.failure(RuntimeException("oauth_invalid"))
                is YandexAuthResult.Cancelled -> Result.failure(RuntimeException("cancelled"))
            }
        }

    override suspend fun currentSession(): LocalAuthSession? {
        val session = sessionStore.load()
        if (session?.provider == LocalAuthProvider.YANDEX) {
            credentialStore.load().onFailure {
                Log.w(TAG, "Yandex credential is unavailable during bootstrap", it)
            }
        }
        return session
    }

    override suspend fun signOut() {
        sessionStore.clear()
        credentialStore.clear().onFailure {
            Log.w(TAG, "Credential cleanup deferred because secure storage is unavailable", it)
        }
    }

    private fun stableUidFromToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString(separator = "") { "%02x".format(it) }
        return "yandex-$digest"
    }
}
