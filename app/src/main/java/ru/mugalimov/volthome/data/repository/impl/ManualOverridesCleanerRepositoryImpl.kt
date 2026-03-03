package ru.mugalimov.volthome.data.repository.impl

import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.repository.ManualOverridesCleanerRepository

/**
 * Чистит ТОЛЬКО ручные overrides (project-scoped).
 *
 * Важно:
 * - membership (group_device_join) — это SoT структуры, поэтому joins НЕ трогаем вообще.
 * - Никаких тяжелых SELECT ради "joinsCount": доказательность даём текстом лога.
 */
class ManualOverridesCleanerRepositoryImpl @Inject constructor(
    private val groupPhaseOverrideDao: GroupPhaseOverrideDao
) : ManualOverridesCleanerRepository {

    override suspend fun clearAllManualOverrides(projectId: String): Boolean {
        val pid = projectId.trim()
        if (pid.isBlank()) return false

        return try {
            // ✅ Вариант A: удаляем только project-scoped overrides
            val deletedPhase = groupPhaseOverrideDao.deleteByProject(pid)

            Log.w(
                "OVERRIDES",
                "RESET overrides pid=$pid deletedPhase=$deletedPhase (joins untouched)"
            )
            true
        } catch (t: Throwable) {
            Log.e("OVERRIDES", "RESET overrides FAILED pid=$pid", t)
            false
        }
    }
}