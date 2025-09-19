package ru.mugalimov.volthome.data.remote.api

import retrofit2.http.GET

data class ProfileMeDto(
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val plan: String,
    val planUntilEpochSeconds: Long?,
    val uid: String
)

interface ProfileApi {
    @GET("profile/me")
    suspend fun getMe(): ProfileMeDto
}