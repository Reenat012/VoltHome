package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.Project

interface ProjectsRepository {
    fun listProjects(): Flow<List<Project>>

    /**
     * Количество активных проектов (is_deleted = 0).
     * Источник истины: локальная Room БД.
     */
    suspend fun countActiveProjects(): Int

    /** Гарантирует, что активный проект выбран; если нет — создаёт локальный “черновик”. Возвращает id активного. */
    suspend fun ensureActiveDraft(): String

    suspend fun bootstrapFromRemote(): Int

    /**
     * Создаёт локальный проект (оффлайн-первый), ставит флаг локальных изменений
     * и возвращает его id (UUID string), чтобы UI мог сразу выбрать его активным.
     */
    suspend fun createProject(name: String, note: String? = null): String

    suspend fun renameProject(id: String, name: String)

    /**
     * Мягкое удаление (tombstone). Синхронизация — отдельно.
     */
    suspend fun deleteProject(id: String)

    /**
     * Открытие проекта (инициирует синхронизацию в фоне и любые lazy-подгрузки).
     * Не меняет activeProjectId (это делает слой, который управляет UI/Datastore).
     */
    suspend fun openProject(id: String)
}