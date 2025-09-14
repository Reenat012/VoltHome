package ru.mugalimov.volthome.data.repository.impl

import com.yandex.authsdk.YandexAuthException
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.YandexAuthToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.remote.auth.AuthError
import ru.mugalimov.volthome.data.remote.auth.AuthSession
import ru.mugalimov.volthome.data.remote.auth.SessionManager
import ru.mugalimov.volthome.data.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация репозитория авторизации на основе рекомендаций Яндекс ID SDK 3.1.x:
 * - SDK создаётся через фабрику и пробрасывается через DI (см. Hilt-модуль).
 * - Запуск авторизации — через Activity Result API: sdk.contract + launcher.launch(YandexAuthLoginOptions()).
 * - Обработка результата — через YandexAuthResult (Success/Failure/Cancelled).
 *
 * ВАЖНО: Репозиторий НЕ создаёт контекст и НЕ строит Intent — это делает UI через контракт.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sdk: YandexAuthSdk,
    private val sessionManager: SessionManager
) : AuthRepository {

    /** Опции логина, которые следует передавать в launcher.launch(...) */
    override fun loginOptions(): YandexAuthLoginOptions = YandexAuthLoginOptions()

    /**
     * Обработка результата контракта sdk.contract.
     * На успех — сохраняем токен в зашифрованное хранилище.
     */
    override suspend fun handleAuthResult(result: YandexAuthResult): Result<AuthSession> {
        return withContext(Dispatchers.IO) {
            try {
                val token: YandexAuthToken = when (result) {
                    is YandexAuthResult.Success -> result.token
                    is YandexAuthResult.Failure -> throw result.exception
                    YandexAuthResult.Cancelled  -> return@withContext Result.failure(mapError(AuthError.Cancelled))
                }

                val expiresAt = System.currentTimeMillis() + token.expiresIn * 1000L
                val session = AuthSession(
                    accessToken = token.value,
                    expiresAtMillis = expiresAt,
                    tokenType = "OAuth"
                )
                sessionManager.save(session)
                Result.success(session)
            } catch (e: YandexAuthException) {
                Result.failure(mapError(mapException(e)))
            } catch (t: Throwable) {
                Result.failure(mapError(AuthError.Other(t.message)))
            }
        }
    }

    /** Текущая сессия (или null). */
    override suspend fun currentSession(): AuthSession? = sessionManager.load()

    /** Полный выход: чистим локальное хранилище. */
    override suspend fun signOut() {
        sessionManager.clear()
        // Примечание: revoke на стороне Яндекса выполняется через серверный flow при наличии client_secret.
    }

    /** Маппинг исключений SDK на доменные ошибки. */
    private fun mapException(e: YandexAuthException): AuthError {
        val msg = (e.message ?: "").lowercase()
        return when {
            "cancel" in msg -> AuthError.Cancelled
            "security" in msg -> AuthError.Security
            "network" in msg || "connect" in msg -> AuthError.Connection
            "jwt" in msg -> AuthError.JwtAuthorization
            "oauth" in msg && "token" in msg -> AuthError.OAuthTokenInvalid
            else -> AuthError.Other(e.message)
        }
    }

    /** Превращаем доменную ошибку в Throwable для Result.failure, с коротким техническим кодом. */
    private fun mapError(err: AuthError): Throwable = when (err) {
        AuthError.Cancelled -> RuntimeException("cancelled")
        AuthError.Connection -> RuntimeException("connection")
        AuthError.Security -> RuntimeException("security")
        AuthError.OAuthTokenInvalid -> RuntimeException("oauth_invalid")
        AuthError.JwtAuthorization -> RuntimeException("jwt_auth")
        is AuthError.Other -> RuntimeException(err.message ?: "other")
    }
}