package ru.mugalimov.volthome.data.repository.impl

import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
import ru.mugalimov.volthome.data.repository.ManualOverridesCleanerRepository

class ManualOverridesCleanerRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val groupDeviceJoinDao: GroupDeviceJoinDao,
    private val groupPhaseOverrideDao: GroupPhaseOverrideDao
) : ManualOverridesCleanerRepository {

    override suspend fun clearAllManualOverrides(projectId: String): Boolean {
        val pid = projectId.trim()
        if (pid.isBlank()) return false

        return try {
            // 1) groupIds проекта (потому что join без project boundary)
            val groupIds = groupDao.getGroupIdsByProject(pid)

            // 2) phase overrides (project-scoped)
            val deletedPhase = groupPhaseOverrideDao.deleteByProject(pid)

            // 3) joins для groupIds
            val deletedJoins = if (groupIds.isEmpty()) 0 else groupDeviceJoinDao.deleteJoinsForGroupIds(groupIds)

            Log.w(
                "OVERRIDES",
                "RESET overrides pid=$pid groupIds=${groupIds.size} deletedPhase=$deletedPhase deletedJoins=$deletedJoins"
            )
            true
        } catch (t: Throwable) {
            Log.e("OVERRIDES", "RESET overrides FAILED pid=$pid", t)
            false
        }
    }
}