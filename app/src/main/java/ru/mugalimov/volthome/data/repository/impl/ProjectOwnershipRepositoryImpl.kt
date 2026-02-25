package ru.mugalimov.volthome.data.repository.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import ru.mugalimov.volthome.data.local.dao.ProjectLocalStateDao
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository

/**
 * Реализация ownership-хранилища на базе ProjectLocalStateEntity.
 *
 * Комментарий:
 * - manual lock = manual_overrides_present
 * - bootstrapVersion = manual_lock_bootstrap_version
 *
 * ВАЖНО:
 * - DAO возвращает nullable (Boolean?/Int?/Flow<Boolean?>), потому что строка проекта может отсутствовать.
 * - Репозиторий обязан выдавать НЕ-null и нормализовать значение.
 * - Поэтому здесь: ensureRow(projectId) + дефолты.
 */
class ProjectOwnershipRepositoryImpl @Inject constructor(
    private val projectLocalStateDao: ProjectLocalStateDao
) : ProjectOwnershipRepository {

    override suspend fun isManualLock(projectId: String): Boolean {
        val pid = projectId.trim()
        if (pid.isBlank()) return false

        // ✅ Гарантируем наличие строки, иначе DAO вернёт null
        projectLocalStateDao.ensureRow(pid)

        // ✅ Нормализуем nullable -> non-null
        return projectLocalStateDao.getManualOverridesPresent(pid) ?: false
    }

    override suspend fun setManualLock(projectId: String, locked: Boolean) {
        val pid = projectId.trim()
        if (pid.isBlank()) return

        // ✅ ensureRow, иначе UPDATE может вернуть 0 строк
        projectLocalStateDao.ensureRow(pid)

        val rows = projectLocalStateDao.setManualOverridesPresent(pid, locked)
        // Комментарий: rows должен быть 1. Если 0 — значит ensureRow не сработал или projectId грязный.
        // Здесь не падаем, чтобы не ломать UX, но логировать лучше на уровне usecase.
        if (rows != 1) {
            // intentionally empty
        }
    }

    override fun observeManualLock(projectId: String): Flow<Boolean> {
        val pid = projectId.trim()
        if (pid.isBlank()) {
            // Нельзя вернуть emptyFlow без импорта, поэтому делаем map от DAO через ensureRow в другом контуре.
            // Но blank pid — это ошибка вызова, возвращаем "всегда false".
            return projectLocalStateDao.observeManualOverridesPresent(projectId)
                .map { false } // projectId пустой -> считаем, что лока нет
        }

        // Важно: observe* не может вызвать suspend ensureRow прямо тут.
        // Поэтому:
        // - В нормальном контуре ensureRow(pid) должен происходить при выборе активного проекта (bootstrap).
        // - Но даже если его забыли, мы всё равно нормализуем null -> false, чтобы UI не падал.
        return projectLocalStateDao.observeManualOverridesPresent(pid)
            .map { it ?: false }
    }

    override suspend fun getBootstrapVersion(projectId: String): Int {
        val pid = projectId.trim()
        if (pid.isBlank()) return 0

        // ✅ Гарантируем наличие строки
        projectLocalStateDao.ensureRow(pid)

        // ✅ Нормализуем nullable -> 0
        return projectLocalStateDao.getManualLockBootstrapVersion(pid) ?: 0
    }

    override suspend fun setBootstrapVersion(projectId: String, version: Int) {
        val pid = projectId.trim()
        if (pid.isBlank()) return

        // ✅ ensureRow, иначе UPDATE может вернуть 0 строк
        projectLocalStateDao.ensureRow(pid)

        val rows = projectLocalStateDao.setManualLockBootstrapVersion(pid, version)
        if (rows != 1) {
            // intentionally empty
        }
    }
}