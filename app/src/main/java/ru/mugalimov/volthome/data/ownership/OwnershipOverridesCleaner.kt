package ru.mugalimov.volthome.data.ownership

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao

/**
 * Единая точка очистки всех ownership/manual overrides.
 *
 * ВАЖНО:
 * - Здесь должен быть ЯВНЫЙ список всех override-таблиц из audit.
 * - Сейчас по присланному коду audit-фактически содержит только group_phase_overrides.
 * - Если позже появятся новые override-таблицы, ДОБАВЛЯТЬ их нужно только сюда.
 *
 * НИЧЕГО, кроме override-таблиц, этот cleaner не трогает:
 * - joins не трогаем
 * - groups не трогаем
 * - devices не трогаем
 */
@Singleton
class OwnershipOverridesCleaner @Inject constructor(
    private val groupPhaseOverrideDao: GroupPhaseOverrideDao,
) {

    data class ClearStats(
        val deletedGroupPhaseOverrides: Int,
        val totalDeleted: Int,
    )

    suspend fun clearAll(projectId: String): ClearStats {
        val pid = projectId.trim()
        require(pid.isNotBlank()) { "projectId must be non-blank" }

        Log.w("OVERRIDES", "OVERRIDES_CLEANER BEGIN pid=$pid")

        // ✅ ЯВНЫЙ список override-таблиц из audit.
        // Сейчас по факту у тебя только одна override-таблица.
        val deletedGroupPhaseOverrides = groupPhaseOverrideDao.deleteByProject(pid)

        val stats = ClearStats(
            deletedGroupPhaseOverrides = deletedGroupPhaseOverrides,
            totalDeleted = deletedGroupPhaseOverrides,
        )

        Log.w(
            "OVERRIDES",
            "OVERRIDES_CLEANER DONE pid=$pid " +
                    "deletedGroupPhaseOverrides=${stats.deletedGroupPhaseOverrides} " +
                    "totalDeleted=${stats.totalDeleted}"
        )

        return stats
    }
}