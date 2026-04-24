package ru.mugalimov.volthome.data.remote.api

import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Body

// GET /v1/profile/me
data class ProfileMeDto(
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?,
    val plan: String?,
    val planUntilEpochSeconds: Long?,
    val uid: String
)

// PUT /v1/profile/me — ТЕЛО запроса
data class ProfileUpsertRequest(
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null
)

// PUT /v1/profile/me — ОТВЕТ сервера: { ok, profile: {...} }
data class ProfileUpsertResponse(
    val ok: Boolean,
    val profile: ProfileMeDto
)

interface ProfileApi {
    @GET("v1/profile/me")
    suspend fun getMe(): ProfileMeDto

    @PUT("v1/profile/me")
    suspend fun upsertMe(@Body body: ProfileUpsertRequest): ProfileUpsertResponse
}