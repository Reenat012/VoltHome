package ru.mugalimov.volthome.ui.model

/**
 * Простые UI-модели, чтобы не тянуть domain в компонент Drawer.
 * Маппинг делаешь в своём VM/контейнере: Project -> ProjectUi, профиль -> UserProfileUi.
 */
data class UserProfileUi(
    val name: String?,
    val email: String?,
    val avatarUrl: String? = null
)

data class ProjectUi(
    val id: String,
    val name: String,
    val isActive: Boolean = false
)