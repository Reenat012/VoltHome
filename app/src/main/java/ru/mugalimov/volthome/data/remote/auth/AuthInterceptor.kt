package ru.mugalimov.volthome.data.remote.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
    private val refreshCoordinator: RefreshCoordinator
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // 1) Перед запросом: если токен скоро истечёт — тихо обновляем (блокирующе)
        val preAuthHeader: String? = try {
            maybeRefreshAndGetHeaderBlocking()
        } catch (_: Throwable) {
            null
        }

        val requestWithAuth = chain.request().let { req ->
            if (preAuthHeader != null) {
                req.newBuilder().header("Authorization", preAuthHeader).build()
            } else {
                req
            }
        }

        val response = try {
            chain.proceed(requestWithAuth)
        } catch (io: IOException) {
            // Сеть упала — пробрасываем; сессию НЕ чистим
            throw io
        }

        // 2) На 401 — одна попытка refresh + повтор запроса
        if (response.code == 401) {
            response.close()

            val refreshed: String? = try {
                runBlocking { refreshCoordinator.tryRefresh() }
            } catch (_: Throwable) {
                null
            }

            // Если refresh вернул "недействителен" (401) — не повторяем, отдадим 401
            if (refreshed == null && refreshCoordinator.wasLastRefreshUnauthorized()) {
                return chain.proceed(addAuthHeader(chain.request(), null))
            }

            // Если обновились — повторяем запрос с новым токеном
            if (refreshed != null) {
                val retried = addAuthHeader(chain.request(), "Bearer $refreshed")
                return chain.proceed(retried)
            }
        }

        return response
    }

    /**
     * Блокирующая версия "проверить/обновить и вернуть заголовок Authorization".
     * Без suspend, чтобы быть совместимым с OkHttp Interceptor.
     */
    private fun maybeRefreshAndGetHeaderBlocking(): String? {
        val session = runBlocking { sessionManager.load() } ?: return null
        val headerFromSession = "${session.tokenType} ${session.accessToken}"

        val now = System.currentTimeMillis()
        val leewayMs = TimeUnit.SECONDS.toMillis(SessionManager.DEFAULT_LEEWAY_SECONDS)
        val expiringSoon = (session.expiresAtMillis - now) <= leewayMs

        return if (!expiringSoon) {
            headerFromSession
        } else {
            // Токен вот-вот истечёт — тихий refresh (блокирующе)
            val newToken = runBlocking { refreshCoordinator.tryRefresh() }
            if (newToken != null) "Bearer $newToken" else headerFromSession
        }
    }

    private fun addAuthHeader(original: Request, bearerOrNull: String?): Request {
        return original.newBuilder().apply {
            if (bearerOrNull != null) header("Authorization", bearerOrNull)
        }.build()
    }
}