package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Единственный источник правды по ownership/lock/bootstrapVersion для проекта.
 *
 * ВАЖНО:
 * - Никаких прямых обращений к DAO из usecase'ов/VM.
 * - Только через этот репозиторий.
 */
interface ProjectOwnershipRepository {

    /** True, если для проекта зафиксированы ручные изменения (manual overrides присутствуют). */
    suspend fun isManualLock(projectId: String): Boolean

    /** Установить/снять manual lock (маркер наличия ручных изменений). */
    suspend fun setManualLock(projectId: String, locked: Boolean)

    /** Наблюдение за manual lock (для UI/логики, если понадобится). */
    fun observeManualLock(projectId: String): Flow<Boolean>

    /** Текущая версия bootstrap (persisted). */
    suspend fun getBootstrapVersion(projectId: String): Int

    /** Установить версию bootstrap (persisted). */
    suspend fun setBootstrapVersion(projectId: String, version: Int)
}