package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.Project

interface ProjectsRepository {
    fun listProjects(): Flow<List<Project>>

    suspend fun getActiveProjectId(): String?

    /**
     * Количество активных проектов (is_deleted = 0).
     * Источник истины: локальная Room БД.
     */
    suspend fun countActiveProjects(): Int

    /** Гарантирует, что активный проект выбран; если нет — создаёт локальный “черновик”. Возвращает id активного. */
    suspend fun ensureActiveDraft(): String

    /** Создаёт локальный проект. Мастер может отложить активацию до успешного расчёта. */
    suspend fun createProject(name: String, note: String? = null, activate: Boolean = true): String

    suspend fun renameProject(id: String, name: String)

    /** Помечает локальный проект удалённым. */
    suspend fun deleteProject(id: String)

    /** Выбирает локальный проект активным. */
    suspend fun openProject(id: String)
}
