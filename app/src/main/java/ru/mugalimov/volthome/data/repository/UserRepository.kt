package ru.mugalimov.volthome.data.repository

import ru.mugalimov.volthome.data.remote.yandex.YandexUserInfo

interface UserRepository {
    suspend fun loadMe(): Result<YandexUserInfo>
}