package ru.mugalimov.volthome.data.remote.auth

import android.util.Log
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.remote.api.AuthApi
import ru.mugalimov.volthome.data.remote.api.RefreshRequest
import ru.mugalimov.volthome.data.sync.work.TokenRefreshScheduler

/**
 * Единая точка refresh (single-flight) с защитой от гонок rotation.
 * Только здесь выполняется вызов /auth/refresh.
 *
 * ВАЖНО:
 * - после любого успешного /auth/refresh мы не только сохраняем сессию в SessionManager,
 *   но и перепланируем TokenRefreshWorker через TokenRefreshScheduler;
 * - uid тоже обязан сохраняться здесь, иначе после refresh stable identity потеряется.
 */
@Singleton
class RefreshGate @Inject constructor(
    @Named("refreshApi") private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val tokenRefreshScheduler: TokenRefreshScheduler
) {

    private val io = Dispatchers.IO
    private val mutex = Mutex()
    private var running: Boolean = false
    private var lastTokenUsedSha: String? = null
    private var lastResult: Result = Result.Idle

    sealed interface Result {
        data object Idle : Result
        data class Succeeded(val newExpSeconds: Long) : Result
        data class Failed(val kind: FailureKind) : Result
    }

    enum class FailureKind {
        NETWORK,
        UNAUTHORIZED,
        UNKNOWN
    }

    /**
     * Если refresh уже идет — подождать его завершение.
     */
    suspend fun awaitIfRunning() {
        mutex.withLock {
            // Ничего не делаем: просто даем завершиться текущему циклу.
        }
    }

    /**
     * Инициировать refresh, если он нужен.
     * Если уже запущен — дождаться результата.
     *
     * @param minTtlSec если до истечения меньше, чем столько секунд — пробуем обновить.
     */
    suspend fun refreshIfNeeded(minTtlSec: Int): Result = withContext(io) {
        val needs = sessionManager.needsRefresh(leewaySeconds = minTtlSec.toLong())
        if (!needs) return@withContext Result.Idle

        mutex.withLock {
            // Пока ждали лок, ситуация могла измениться — перепроверяем.
            if (!sessionManager.needsRefresh(leewaySeconds = minTtlSec.toLong())) {
                lastResult = Result.Idle
                return@withLock lastResult
            }

            if (running) {
                // Теоретически сюда не попадём (держим mutex), но оставляем на будущее.
                return@withLock lastResult
            }

            running = true
            try {
                val refreshToken = sessionManager.refreshTokenOrNull() ?: run {
                    lastResult = Result.Failed(FailureKind.UNAUTHORIZED)
                    return@withLock lastResult
                }

                val usedSha = sha256(refreshToken)
                lastTokenUsedSha = usedSha

                val response = try {
                    authApi.refresh(RefreshRequest(refreshId = refreshToken))
                } catch (e: CancellationException) {
                    lastResult = Result.Failed(FailureKind.UNKNOWN)
                    return@withLock lastResult
                } catch (e: Throwable) {
                    // Сетевые исключения — NETWORK (без логаута).
                    lastResult = Result.Failed(FailureKind.NETWORK)
                    return@withLock lastResult
                }

                Log.d(
                    "RefreshGate",
                    "REFRESH_IF_NEEDED_OK uid=${response.uid} refreshIdPresent=${!response.refreshId.isNullOrBlank()}"
                )

                // Успех — сохраняем новую сессию (access + новый refresh + uid),
                // и сразу же планируем следующий refresh.
                sessionManager.save(
                    sessionJwt = response.sessionJwt,
                    expiresAtEpochSeconds = response.expiresAtEpochSeconds,
                    refreshId = response.refreshId,
                    uid = response.uid
                )

                tokenRefreshScheduler.schedule(response.expiresAtEpochSeconds * 1000L)

                lastResult = Result.Succeeded(response.expiresAtEpochSeconds)
                return@withLock lastResult
            } catch (_: Throwable) {
                lastResult = Result.Failed(FailureKind.UNKNOWN)
                return@withLock lastResult
            } finally {
                running = false
            }
        }
    }

    /**
     * Жестко выполнить refresh прямо сейчас, без проверки TTL.
     * Нужен для Authenticator после 401.
     */
    suspend fun forceRefresh(): Result = withContext(io) {
        mutex.withLock {
            if (running) {
                return@withLock lastResult
            }

            running = true
            try {
                val refreshToken = sessionManager.refreshTokenOrNull() ?: run {
                    lastResult = Result.Failed(FailureKind.UNAUTHORIZED)
                    return@withLock lastResult
                }

                val usedSha = sha256(refreshToken)
                lastTokenUsedSha = usedSha

                val response = try {
                    authApi.refresh(RefreshRequest(refreshId = refreshToken))
                } catch (e: Throwable) {
                    // Проверяем гонку rotation:
                    // если в сторе уже лежит другой refresh-токен — значит,
                    // где-то параллельно всё уже обновилось.
                    val currentRefreshNow = sessionManager.refreshTokenOrNull()
                    return@withLock if (
                        currentRefreshNow != null && sha256(currentRefreshNow) != usedSha
                    ) {
                        lastResult = Result.Failed(FailureKind.UNKNOWN)
                        lastResult
                    } else {
                        // Это реальный фатал по актуальному refresh.
                        lastResult = Result.Failed(FailureKind.UNAUTHORIZED)
                        lastResult
                    }
                }

                Log.d(
                    "RefreshGate",
                    "FORCE_REFRESH_OK uid=${response.uid} refreshIdPresent=${!response.refreshId.isNullOrBlank()}"
                )

                // При forceRefresh тоже обязаны сохранять uid.
                sessionManager.save(
                    sessionJwt = response.sessionJwt,
                    expiresAtEpochSeconds = response.expiresAtEpochSeconds,
                    refreshId = response.refreshId,
                    uid = response.uid
                )

                // Планируем следующий refresh по новому exp.
                tokenRefreshScheduler.schedule(response.expiresAtEpochSeconds * 1000L)

                lastResult = Result.Succeeded(response.expiresAtEpochSeconds)
                return@withLock lastResult
            } finally {
                running = false
            }
        }
    }

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val b = md.digest(s.toByteArray(Charsets.UTF_8))
        return b.joinToString("") { "%02x".format(it) }
    }
}