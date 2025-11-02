package ru.mugalimov.volthome.data.repository.impl

import com.yandex.authsdk.YandexAuthException
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.YandexAuthToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.remote.api.AuthApi
import ru.mugalimov.volthome.data.remote.api.ExchangeRequest
import ru.mugalimov.volthome.data.remote.api.LogoutRequest
import ru.mugalimov.volthome.data.remote.auth.AuthError
import ru.mugalimov.volthome.data.remote.auth.AuthSession
import ru.mugalimov.volthome.data.remote.auth.SessionManager
import ru.mugalimov.volthome.data.remote.auth.toAuthSession
import ru.mugalimov.volthome.data.repository.AuthRepository
import ru.mugalimov.volthome.data.sync.work.TokenRefreshScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация репозитория авторизации на основе рекомендаций Яндекс ID SDK 3.1.x.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sdk: YandexAuthSdk,
    private val authApi: AuthApi,
    private val session: SessionManager,
    private val tokenRefreshScheduler: TokenRefreshScheduler
) : AuthRepository {

    /** Опции логина, которые следует передавать в launcher.launch(...) */
    override fun loginOptions(): YandexAuthLoginOptions = YandexAuthLoginOptions()

    /**
     * Обработка результата контракта sdk.contract.
     * На успех — обмениваем код на серверную сессию, сохраняем и планируем фоновый refresh.
     */
    override suspend fun handleAuthResult(result: YandexAuthResult): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            when (result) {
                is YandexAuthResult.Success -> {
                    val ya: YandexAuthToken = result.token
                    return@withContext try {
                        // Обмен на серверную сессию
                        val resp = authApi.exchange(ExchangeRequest(code = ya.value))

                        // Сохраняем локально
                        session.save(
                            sessionJwt = resp.sessionJwt,
                            expiresAtEpochSeconds = resp.expiresAtEpochSeconds,
                            refreshId = resp.refreshId
                        )

                        // ⏰ Сразу планируем фоновое продление
                        tokenRefreshScheduler.scheduleDual(resp.expiresAtEpochSeconds * 1000L)

                        Result.success(
                            AuthSession(
                                accessToken = resp.sessionJwt,
                                expiresAtMillis = resp.expiresAtEpochSeconds * 1000L,
                                tokenType = "Bearer",
                                refreshId = resp.refreshId
                            )
                        )
                    } catch (_: Throwable) {
                        Result.failure(RuntimeException("jwt_auth"))
                    }
                }

                is YandexAuthResult.Failure -> {
                    Result.failure(RuntimeException("oauth_invalid"))
                }

                is YandexAuthResult.Cancelled -> {
                    Result.failure(RuntimeException("cancelled"))
                }
            }
        }

    /** Текущая сессия (или null). */
    override suspend fun currentSession(): AuthSession? = session.load()?.toAuthSession()

    /** Полный выход: чистим локальное хранилище и отзываем refresh на сервере (best-effort). */
    override suspend fun signOut() {
        val s = session.load()
        try {
            authApi.logout(LogoutRequest(refreshId = s?.refreshId))
        } catch (_: Throwable) {
            // server logout best-effort
        } finally {
            session.clear()
            // Отдельно ничего отменять не надо: новый work REPLACE-нит старый при следующем логине
        }
    }

    /** Маппинг исключений SDK на доменные ошибки (оставлено без изменений). */
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