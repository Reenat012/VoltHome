package ru.mugalimov.volthome.data.repository

import ru.mugalimov.volthome.data.remote.api.ProfileMeDto

interface UserRepository {
    suspend fun loadMe(): Result<ProfileMeDto>
}