package ru.mugalimov.volthome.data.repository.impl

import ru.mugalimov.volthome.data.remote.api.ProfileApi
import ru.mugalimov.volthome.data.remote.api.ProfileMeDto
import ru.mugalimov.volthome.data.remote.api.ProfileUpsertRequest
import ru.mugalimov.volthome.data.remote.yandex.YandexUserInfo
import ru.mugalimov.volthome.data.remote.yandex.YandexUserRemoteDataSource
import ru.mugalimov.volthome.data.remote.yandex.YandexTokenStore // ← NEW
import ru.mugalimov.volthome.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val remote: YandexUserRemoteDataSource,
    private val profileApi: ProfileApi,
    private val yaTokenStore: YandexTokenStore // ← NEW
) : UserRepository {

    override suspend fun loadMe(): Result<ProfileMeDto> {
        return try {
            val current = profileApi.getMe()

            val needsEnrich =
                current.displayName.isNullOrBlank() ||
                        current.displayName == "Volt User" ||
                        current.email.isNullOrBlank()

            if (!needsEnrich) return Result.success(current)

            // Берём сохранённый access_token Яндекса и тянем /info
            val yaToken = yaTokenStore.get()
            val ya: YandexUserInfo? = yaToken?.let { remote.getUserInfo(it).getOrNull() }

            if (ya == null) {
                // Нет токена/нет доступа/ошибка — отдаём как есть
                Result.success(current)
            } else {
                // Готовим апсерт: отправляем ТОЛЬКО улучшения
                val displayNameUpd: String? =
                    if (current.displayName.isNullOrBlank() || current.displayName == "Volt User") {
                        ya.bestName
                    } else {
                        null
                    }

                val emailUpd: String? = current.email ?: ya.email
                val avatarUpd: String? = current.avatarUrl ?: ya.avatarUrl()

                // Если нечего улучшать — возвращаем текущее
                val hasUpdates = (displayNameUpd != null) || (emailUpd != null) || (avatarUpd != null)
                if (!hasUpdates) return Result.success(current)

                val payload = ProfileUpsertRequest(
                    displayName = displayNameUpd, // null → поле не уйдёт
                    email = emailUpd,
                    avatarUrl = avatarUpd
                )

                val updated = runCatching {
                    profileApi.upsertMe(payload).profile // у тебя ответ { ok, profile }
                }.getOrElse { current }

                Result.success(updated)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}