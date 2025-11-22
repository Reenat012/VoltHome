package ru.mugalimov.volthome.data.remote.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.remote.api.AuthApi
import ru.mugalimov.volthome.data.remote.api.RefreshRequest
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Единая точка refresh (single-flight) с защитой от гонок rotation.
 * Только здесь выполняется вызов /auth/refresh.
 */
@Singleton
class RefreshGate @Inject constructor(
    @Named("refreshApi") private val authApi: AuthApi,
    private val sessionManager: SessionManager
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
        NETWORK, UNAUTHORIZED, UNKNOWN
    }

    /**
     * Если refresh уже идет — подождать его завершение.
     */
    suspend fun awaitIfRunning() {
        mutex.withLock {
            // Ничего не делаем: просто даем завершиться текущему циклу
            // Выйдем из скоупа — refresh уже либо закончился, либо не шел.
        }
    }

    /**
     * Инициировать refresh, если он нужен. Если уже запущен — дождаться результата.
     * @param minTtlSec если до истечения меньше, чем столькo секунд — пробуем обновить
     */
    suspend fun refreshIfNeeded(minTtlSec: Int): Result = withContext(io) {
        val needs = sessionManager.needsRefresh(leewaySeconds = minTtlSec.toLong())
        if (!needs) return@withContext Result.Idle

        mutex.withLock {
            // Если пока мы ждали, кто-то уже выполнил успешный refresh — выходим
            if (!sessionManager.needsRefresh(leewaySeconds = minTtlSec.toLong())) {
                lastResult = Result.Idle
                return@withLock lastResult
            }

            if (running) {
                // Теоретически не зайдем сюда — мы держим mutex — но оставим на случай будущих доработок
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
                    // Отличаем сетевые ошибки от 401/403 на уровне клиента сложно,
                    // поэтому семантика такая: сетевые исключения — NETWORK (без логаута).
                    lastResult = Result.Failed(FailureKind.NETWORK)
                    return@withLock lastResult
                }

                // Успех — сохраняем новую сессию (access + новый refresh), перепланируем ворк
                sessionManager.save(
                    sessionJwt = response.sessionJwt,
                    expiresAtEpochSeconds = response.expiresAtEpochSeconds,
                    refreshId = response.refreshId
                )
                lastResult = Result.Succeeded(response.expiresAtEpochSeconds)
                return@withLock lastResult
            } catch (t: Throwable) {
                // Возможные 401 с сервера мы различим вне: проверим актуальность refresh в сторе.
                // Здесь считаем «неизвестной» ошибкой.
                lastResult = Result.Failed(FailureKind.UNKNOWN)
                return@withLock lastResult
            } finally {
                running = false
            }
        }
    }

    /**
     * Жестко выполнить refresh «прямо сейчас», без проверки TTL.
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
                    // Сначала проверим гонку rotation:
                    // Если в сторе уже лежит ДРУГОЙ refresh-токен (значит, где-то параллельно всё уже обновилось),
                    // не делаем logout — позволим следующему запросу повториться с актуальным access.
                    val currentRefreshNow = sessionManager.refreshTokenOrNull()
                    return@withLock if (currentRefreshNow != null && sha256(currentRefreshNow) != usedSha) {
                        lastResult = Result.Failed(FailureKind.UNKNOWN)
                        lastResult
                    } else {
                        // Это реальный фатал по актуальному токену
                        lastResult = Result.Failed(FailureKind.UNAUTHORIZED)
                        lastResult
                    }
                }

                sessionManager.save(
                    sessionJwt = response.sessionJwt,
                    expiresAtEpochSeconds = response.expiresAtEpochSeconds,
                    refreshId = response.refreshId
                )
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