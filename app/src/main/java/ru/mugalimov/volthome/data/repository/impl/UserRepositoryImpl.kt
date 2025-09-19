package ru.mugalimov.volthome.data.repository.impl

import ru.mugalimov.volthome.data.remote.api.ProfileApi
import ru.mugalimov.volthome.data.remote.api.ProfileMeDto
import ru.mugalimov.volthome.data.remote.auth.SessionManager
import ru.mugalimov.volthome.data.remote.yandex.YandexUserInfo
import ru.mugalimov.volthome.data.remote.yandex.YandexUserRemoteDataSource
import ru.mugalimov.volthome.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val session: SessionManager,
    private val remote: YandexUserRemoteDataSource,
    private val profileApi: ProfileApi
) : UserRepository {

    override suspend fun loadMe(): Result<ProfileMeDto> = try {
                Result.success(profileApi.getMe())
            } catch (t: Throwable) {
                Result.failure(t)
            }
}