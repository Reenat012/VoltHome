package ru.mugalimov.volthome.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class ExchangeRequest(val code: String? = null, val uid: String? = null)
data class RefreshRequest(val refreshId: String? = null)
data class LogoutRequest(val refreshId: String? = null)

data class SessionResponse(
    val sessionJwt: String,
    val expiresAtEpochSeconds: Long,
    val expiresAt: Long? = null,
    val refreshId: String? = null
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