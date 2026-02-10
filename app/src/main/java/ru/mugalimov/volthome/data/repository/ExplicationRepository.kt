package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.phase_load.GroupWithDevices

interface ExplicationRepository {

    // --- Decision log (in-memory) ---
    fun observeDistributionDecisions(): Flow<List<DistributionDecision>>
    suspend fun setLastDistributionDecisions(decisions: List<DistributionDecision>)

    // --- Observe ---
    fun observeAllGroup(): Flow<List<CircuitGroup>>
    fun observeGroupsWithDevices(): Flow<List<CircuitGroupWithDevices>>

    // --- Read ---
    suspend fun getGroupsWithDevices(): List<GroupWithDevices>
    suspend fun getGroupsWithDevicesByProject(projectId: String): List<GroupWithDevices>

    // --- Mutations ---
    suspend fun addGroup(circuitGroups: List<CircuitGroup>)
    suspend fun updateGroup(groupId: Long)
    suspend fun getAllGroups(): List<CircuitGroup>

    /**
     * ❌ Запрещено.
     * В мультипроектности "удалить всё" почти всегда означает "случайно снести чужой проект".
     *
     * Вместо этого:
     * - используйте replaceAllGroupsTransactional(projectId, emptyList())
     * - либо добавьте отдельный метод deleteAllGroupsByProject(projectId), если нужен семантически.
     */
    @Deprecated(
        message = "deleteAllGroups() запрещён. Используйте replaceAllGroupsTransactional(projectId, emptyList())",
        replaceWith = ReplaceWith("replaceAllGroupsTransactional(projectId, emptyList())"),
        level = DeprecationLevel.ERROR
    )
    suspend fun deleteAllGroups()

    suspend fun addDeviceToGroup(deviceId: Long, groupId: Long)
    suspend fun getDevicesForGroup(groupId: Long): List<Device>

    suspend fun handleRoomDeletion(roomId: Long)
    suspend fun handleDeviceDeletion(deviceId: Long)

    suspend fun getGroupById(groupId: Long): CircuitGroup?
    suspend fun getGroupByRoom(roomName: String): List<CircuitGroup>
    suspend fun getGroupByType(groupType: DeviceType): List<CircuitGroup>

    /**
     * ❌ Запрещённый overload: зависит от activeProjectId "в моменте".
     * Всегда используем вариант с projectId.
     */
    @Deprecated(
        message = "Используйте overload с projectId. Этот вариант зависит от activeProjectId и может писать не туда.",
        replaceWith = ReplaceWith("replaceAllGroupsTransactional(projectId, groups)"),
        level = DeprecationLevel.ERROR
    )
    suspend fun replaceAllGroupsTransactional(groups: List<CircuitGroup>)

    /**
     * ✅ Единственно безопасный replace для мультипроектности.
     */
    suspend fun replaceAllGroupsTransactional(projectId: String, groups: List<CircuitGroup>)
}