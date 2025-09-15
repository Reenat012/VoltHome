package ru.mugalimov.volthome.data.repository.impl

import ru.mugalimov.volthome.data.remote.auth.SessionManager
import ru.mugalimov.volthome.data.remote.yandex.YandexUserInfo
import ru.mugalimov.volthome.data.remote.yandex.YandexUserRemoteDataSource
import ru.mugalimov.volthome.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val session: SessionManager,
    private val remote: YandexUserRemoteDataSource
) : UserRepository {

    override suspend fun loadMe(): Result<YandexUserInfo> {
        val s = session.load() ?: return Result.failure(IllegalStateException("not_logged_in"))
        if (s.isExpired) return Result.failure(IllegalStateException("token_expired"))
        return remote.getUserInfo(s.accessToken)
    }
}