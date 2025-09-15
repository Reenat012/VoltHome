package ru.mugalimov.volthome.data.remote.yandex

import com.google.gson.annotations.SerializedName

/**
 * Минимальная модель ответа /info. Поле display_name — то, что чаще всего нужно в UI.
 * Список полей взят из публичной документации Яндекс ID.
 */
data class YandexUserInfo(
    @SerializedName("id") val id: String? = null,
    @SerializedName("login") val login: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("real_name") val realName: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("default_email") val email: String? = null,
    @SerializedName("default_avatar_id") val avatarId: String? = null
) {
    val bestName: String
        get() = when {
            !realName.isNullOrBlank() -> realName
            !displayName.isNullOrBlank() -> displayName
            !login.isNullOrBlank() -> login
            else -> "Пользователь"
        }

    /**
     * Сборка URL аватарки (если нужен кругляшок в UI).
     * Пример шаблона у Яндекса: https://avatars.yandex.net/get-yapic/<id>/islands-200
     */
    fun avatarUrl(sizePreset: String = "islands-200"): String? =
        avatarId?.let { "https://avatars.yandex.net/get-yapic/$it/$sizePreset" }
}