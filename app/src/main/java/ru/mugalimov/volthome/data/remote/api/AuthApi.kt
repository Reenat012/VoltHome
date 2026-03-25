package ru.mugalimov.volthome.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Запрос обмена результата Яндекс ID на серверную сессию.
 *
 * Важно:
 * - code оставляем для обратной совместимости;
 * - yaAccessToken добавляем как явное поле, чтобы сервер мог сходить в /info
 *   и получить стабильный Yandex user id;
 * - uid оставляем как совместимое поле на будущее/для fallback-сценариев.
 */
data class ExchangeRequest(
    val code: String? = null,
    val uid: String? = null,
    val yaAccessToken: String? = null
)

data class RefreshRequest(
    val refreshId: String? = null
)

data class LogoutRequest(
    val refreshId: String? = null
)

/**
 * Ответ сервера с серверной сессией.
 *
 * Важно:
 * - uid теперь приходит с сервера как owner identity;
 * - старый сервер может uid не прислать, поэтому поле nullable.
 */
data class SessionResponse(
    val sessionJwt: String,
    val expiresAtEpochSeconds: Long,
    val expiresAt: Long? = null,
    val refreshId: String? = null,
    val uid: String? = null
)

interface AuthApi {

    // Сервер: app.use("/v1/auth", authRouter)
    @POST("v1/auth/yandex/exchange")
    suspend fun exchange(@Body body: ExchangeRequest): SessionResponse

    @POST("v1/auth/session/refresh")
    suspend fun refresh(@Body body: RefreshRequest? = null): SessionResponse

    @POST("v1/auth/session/logout")
    suspend fun logout(@Body body: LogoutRequest? = null): Response<Unit>
}