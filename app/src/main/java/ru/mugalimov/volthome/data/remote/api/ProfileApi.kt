package ru.mugalimov.volthome.data.remote.api

import retrofit2.http.GET

// routes/profile.js → GET /v1/profile/me
data class ProfileMeDto(
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val plan: String,
    val planUntilEpochSeconds: Long?,
    val uid: String
)

interface ProfileApi {
    @GET("v1/profile/me")
    suspend fun getMe(): ProfileMeDto
}