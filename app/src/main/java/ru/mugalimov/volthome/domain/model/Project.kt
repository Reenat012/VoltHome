package ru.mugalimov.volthome.domain.model

/**
 * Доменная модель проекта — используется в UI/ViewModel.
 * Отражает ProjectEntity/ProjectDto, но без деталей хранения/сети.
 */
data class Project(
    val id: String,
    val name: String,
    val note: String?,
    val version: Int,
    val updatedAt: String,
    val isDeleted: Boolean
)
