package ru.mugalimov.volthome.data.remote.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mugalimov.volthome.data.remote.api.AuthApi
import ru.mugalimov.volthome.data.remote.api.RefreshRequest
import ru.mugalimov.volthome.data.sync.work.TokenRefreshScheduler
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class RefreshCoordinator @Inject constructor(
    @Named("refreshApi") private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val scheduler: TokenRefreshScheduler
) {
    private val mutex = Mutex()
    @Volatile private var lastRefreshFailedWith401: Boolean = false

    /**
     * Обновляет токен, если есть refreshId. Возвращает новый JWT или null.
     * Чистит сессию только при подтверждённой 401 на refresh.
     * При успехе — планирует фоновой refresh заранее (expiresAt - leeway).
     */
    suspend fun tryRefresh(): String? = mutex.withLock {
        lastRefreshFailedWith401 = false

        val refreshId = sessionManager.refreshIdOrNull() ?: return null
        return try {
            val resp = authApi.refresh(RefreshRequest(refreshId = refreshId))
            sessionManager.save(
                sessionJwt = resp.sessionJwt,
                expiresAtEpochSeconds = resp.expiresAtEpochSeconds,
                refreshId = resp.refreshId
            )
            // ⏰ Сразу перепланируем фон обновления под новый срок
            scheduler.scheduleFromExpiry(expiresAtMillis = resp.expiresAtEpochSeconds * 1000L)

            resp.sessionJwt
        } catch (t: Throwable) {
            val isNetwork = t is IOException
            val isUnauthorized = t::class.java.simpleName.contains("HttpException", ignoreCase = true) &&
                    t.message?.contains("401") == true

            if (isUnauthorized) {
                lastRefreshFailedWith401 = true
                sessionManager.clear()
            }
            null
        }
    }

    fun wasLastRefreshUnauthorized(): Boolean = lastRefreshFailedWith401
}