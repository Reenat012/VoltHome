package ru.mugalimov.volthome.data.repository

import kotlinx.coroutines.flow.Flow
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
import ru.mugalimov.volthome.domain.model.phase_load.GroupWithDevices

interface ExplicationRepository {

    /**
     * ## Membership (устройство ↔ группа)
     *
     * **Источник истины:** `group_device_join`.
     *
     * В текущей схеме **у устройства нет поля `groupId`** ни в `DeviceEntity`, ни в доменной модели `Device`.
     * Поэтому принадлежность устройства группе задаётся **только** через join-таблицу.
     *
     * ### Матрица чтения/записи (факт по коду)
     * - **Чтение (AUTO / UI):**
     *   - `GroupDao.observeGroupsWithDevicesByProject(projectId)` возвращает `CircuitGroupWithDevices`
     *     через Room `@Relation` + `@Junction(GroupDeviceJoin)`.
     * - **Чтение (Manual baseState):**
     *   - `getGroupsWithDevicesByProject(projectId)` читает группы из `groups` и устройства по join
     *     (`GroupDeviceJoinDao.getDevicesForGroup(groupId)`), после чего строит baseState.
     * - **Запись (перенос/сохранение состава):**
     *   - любые изменения membership должны выражаться как операции над `group_device_join`
     *     (insert/delete связей).
     *
     * ### Важные ограничения
     * - `group_device_join` **не содержит `projectId`**, поэтому все операции удаления/обновления
     *   должны быть **строго ограничены** группами, принадлежащими конкретному `projectId`.
     */

    fun observeDistributionDecisions(): Flow<List<DistributionDecision>>
    suspend fun setLastDistributionDecisions(decisions: List<DistributionDecision>)

    fun observeAllGroup(): Flow<List<CircuitGroup>>

    fun observeGroupsWithDevices(): Flow<List<ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices>>

    /**
     * ✅ Project-scoped поток групп (VM-правильный вариант).
     */
    fun observeAllGroupByProject(projectId: String): Flow<List<CircuitGroup>>

    /**
     * ✅ Project-scoped snapshot: группы + устройства по membership (join).
     * ВАЖНО: сюда НЕ попадут устройства без join (unassigned) — это нормально.
     */
    suspend fun getGroupsWithDevicesByProject(projectId: String): List<GroupWithDevices>

    /**
     * ✅ Project-scoped snapshot: ВСЕ устройства проекта (живые, tombstones filtered).
     *
     * Нужен для Manual baseState, чтобы после MANUAL_SAVE устройства без join
     * появлялись в "Нераспределённые".
     */
    suspend fun getAllDevicesByProject(projectId: String): List<Device>

    /** Реактивный project-scoped список, включая устройства без группы. */
    fun observeAllDevicesByProject(projectId: String): Flow<List<Device>>

    /**
     * ## Manual Save (v1.1): DIFF-COMMIT контракт (Вариант B / идеальный)
     */
    suspend fun commitManualDraftTransactional(
        projectId: String,
        draftState: ProjectEditState
    )

    /**
     * AUTO-путь (исторический): replace-by-delete+insert.
     * В manual Save этот метод использовать нельзя.
     */
    suspend fun replaceAllGroupsTransactional(projectId: String, groups: List<CircuitGroup>)
    suspend fun replaceAllGroupsTransactionalAlreadyLocked(projectId: String, groups: List<CircuitGroup>)
    suspend fun replaceAllGroupsTransactional(groups: List<CircuitGroup>)

    @Deprecated(
        message = "Запрещено: нет project boundary. Используйте getGroupsWithDevicesByProject(projectId).",
        level = DeprecationLevel.ERROR
    )
    suspend fun getGroupsWithDevices(): List<GroupWithDevices>

    suspend fun addGroup(circuitGroups: List<CircuitGroup>)
    suspend fun updateGroup(groupId: Long)

    suspend fun getAllGroups(): List<CircuitGroup>

    @Deprecated(
        message = "deleteAllGroups() запрещён. Используйте replaceAllGroupsTransactional(projectId, emptyList())",
        level = DeprecationLevel.ERROR
    )
    suspend fun deleteAllGroups()

    suspend fun addDeviceToGroup(deviceId: Long, groupId: Long)

    suspend fun getDevicesForGroup(groupId: Long): List<Device>

    suspend fun handleRoomDeletion(roomId: Long)
    suspend fun handleDeviceDeletion(deviceId: Long)

    suspend fun getGroupById(groupId: Long): CircuitGroup?

    suspend fun getGroupByRoom(roomName: String): List<CircuitGroup>
    suspend fun getGroupByType(groupType: ru.mugalimov.volthome.domain.model.DeviceType): List<CircuitGroup>
}
