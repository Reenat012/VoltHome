package ru.mugalimov.volthome.domain.use_case

import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao

/**
 * Commit 3 — Reader membership для bootstrap diff.
 *
 * Гарантии:
 * - Читает ТОЛЬКО active devices через единый DAO.
 * - Ограничивает joins строго groupIds проекта.
 * - Нормализует membership (без порядка, без groupId).
 * - Детектит duplicate device в join.
 */
class ManualLockMembershipReader @Inject constructor(
    private val deviceDao: DeviceDao,
    private val groupDao: GroupDao,
    private val joinDao: GroupDeviceJoinDao,
) {

    data class MembershipSnapshot(
        val canonical: Map<Long, String>,
        val hasDuplicate: Boolean
    )

    suspend fun read(projectId: String): MembershipSnapshot {

        val activeIds = deviceDao.getActiveIdsByProjectId(projectId).toSet()
        val groupIds = groupDao.getGroupIdsByProject(projectId)

        if (groupIds.isEmpty()) {
            return MembershipSnapshot(emptyMap(), false)
        }

        val joins = joinDao.getJoinsForGroupIds(groupIds)

        val deviceToGroups = mutableMapOf<Long, MutableList<Long>>()

        joins.forEach { join ->
            if (join.deviceId !in activeIds) return@forEach
            deviceToGroups.getOrPut(join.deviceId) { mutableListOf() }
                .add(join.groupId)
        }

        var duplicateDetected = false

        val canonical = mutableMapOf<Long, String>()

        deviceToGroups.forEach { (deviceId, groups) ->
            if (groups.size > 1) {
                duplicateDetected = true
                Log.e(
                    "MANUAL_LOCK",
                    "BACKFILL reason=BAD_JOIN_DUPLICATE_DEVICE deviceId=$deviceId count=${groups.size}"
                )
            }

            val key = groups.sorted().joinToString(",")
            canonical[deviceId] = key
        }

        return MembershipSnapshot(
            canonical = canonical,
            hasDuplicate = duplicateDetected
        )
    }
}