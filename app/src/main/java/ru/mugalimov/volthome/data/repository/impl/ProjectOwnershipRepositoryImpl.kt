package ru.mugalimov.volthome.data.repository.impl

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import ru.mugalimov.volthome.data.local.dao.ProjectLocalStateDao
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository

/**
 * Реализация ownership-хранилища на базе ProjectLocalStateEntity.
 *
 * manual lock = manual_overrides_present
 * bootstrapVersion = manual_lock_bootstrap_version
 *
 * ВАЖНО:
 * - Репозиторий нормализует null -> дефолт.
 * - ensureRow(pid) обязателен перед чтением/записью.
 */
class ProjectOwnershipRepositoryImpl @Inject constructor(
    private val projectLocalStateDao: ProjectLocalStateDao
) : ProjectOwnershipRepository {

    companion object {
        private const val TAG = "MANUAL_LOCK"

        // Временный proof-тег для расследования старта/рестарта.
        private const val TRACE_TAG = "OWNERSHIP_READ"
    }

    override suspend fun isManualLock(projectId: String): Boolean {
        val pid = projectId.trim()
        if (pid.isBlank()) return false

        projectLocalStateDao.ensureRow(pid)

        val value = projectLocalStateDao.getManualOverridesPresent(pid) ?: false

        // Временный startup-proof лог:
        // видно, что реально прочитали из persisted state.
        Log.i(
            TRACE_TAG,
            "isManualLock pid=$pid value=$value caller=${Throwable().stackTrace.firstOrNull()?.let { "${it.className}.${it.methodName}" } ?: "unknown"}"
        )

        return value
    }

    override suspend fun setManualLock(projectId: String, locked: Boolean) {
        val pid = projectId.trim()
        if (pid.isBlank()) return

        projectLocalStateDao.ensureRow(pid)

        val rows = projectLocalStateDao.setManualOverridesPresent(pid, locked)
        if (rows != 1) {
            // ✅ Не молчим — это потенциальная потеря инварианта
            Log.e(TAG, "OWNERSHIP WRITE FAILED pid=$pid op=setManualLock locked=$locked rowsUpdated=$rows")
        }
    }

    override fun observeManualLock(projectId: String): Flow<Boolean> {
        val pid = projectId.trim()
        if (pid.isBlank()) {
            return projectLocalStateDao.observeManualOverridesPresent(projectId)
                .map { false }
        }

        // observe* не вызывает ensureRow (suspend). Нормализуем null->false.
        return projectLocalStateDao.observeManualOverridesPresent(pid)
            .map { raw ->
                val value = raw ?: false

                // Полезно для расследования стартового состояния и последующих изменений.
                Log.i(TRACE_TAG, "observeManualLock pid=$pid value=$value")

                value
            }
    }

    override suspend fun getBootstrapVersion(projectId: String): Int {
        val pid = projectId.trim()
        if (pid.isBlank()) return 0

        projectLocalStateDao.ensureRow(pid)
        return projectLocalStateDao.getManualLockBootstrapVersion(pid) ?: 0
    }

    override suspend fun setBootstrapVersion(projectId: String, version: Int) {
        val pid = projectId.trim()
        if (pid.isBlank()) return

        projectLocalStateDao.ensureRow(pid)

        val rows = projectLocalStateDao.setManualLockBootstrapVersion(pid, version)
        if (rows != 1) {
            Log.e(TAG, "OWNERSHIP WRITE FAILED pid=$pid op=setBootstrapVersion version=$version rowsUpdated=$rows")
        }
    }
}