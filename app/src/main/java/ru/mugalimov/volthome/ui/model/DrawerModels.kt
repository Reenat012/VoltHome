package ru.mugalimov.volthome.ui.model

/**
 * Простая UI-модель для профиля пользователя,
 * чтобы не тянуть domain в Drawer.
 */
data class UserProfileUi(
    val name: String,
    val email: String?,
    val avatarUrl: String? = null,
    val subscriptionStatus: String? = null
)