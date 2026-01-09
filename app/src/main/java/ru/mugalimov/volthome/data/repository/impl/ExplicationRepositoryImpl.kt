package ru.mugalimov.volthome.data.repository.impl

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.core.error.GroupNotFoundException
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.datastore.ActiveProjectDataStore
import ru.mugalimov.volthome.data.local.entity.CircuitGroupWithDevices
import ru.mugalimov.volthome.data.local.entity.GroupDeviceJoin
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.di.database.AppDatabase
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.mapper.mapToDomainDevices
import ru.mugalimov.volthome.domain.mapper.mapToDomainGroups
import ru.mugalimov.volthome.domain.mapper.mapToDomainGroupsFromRelations
import ru.mugalimov.volthome.domain.mapper.toDomainDevice
import ru.mugalimov.volthome.domain.mapper.toDomainGroup
import ru.mugalimov.volthome.domain.mapper.toDomainGroupFromRelation
import ru.mugalimov.volthome.domain.mapper.toEntityGroup
import ru.mugalimov.volthome.domain.mapper.toEntityJoin
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.phase_load.GroupWithDevices
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.mugalimov.volthome.domain.model.DistributionDecision

class ExplicationRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val groupDeviceJoinDao: GroupDeviceJoinDao,
    private val deviceDao: DeviceDao,
    private val db: AppDatabase,
    private val activeProjectDs: ActiveProjectDataStore,
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : ExplicationRepository {

    // ===== Decision log распределения фаз (in-memory) =====
    // Не пишем в БД, чтобы не тащить миграции. Для UI "почему так" достаточно.
    private val _distributionDecisions =
        MutableStateFlow<List<DistributionDecision>>(emptyList())

    override fun observeDistributionDecisions(): Flow<List<DistributionDecision>> =
        _distributionDecisions.asStateFlow()

    override suspend fun setLastDistributionDecisions(decisions: List<DistributionDecision>) {
        _distributionDecisions.value = decisions
    }

    /** Поток доменных групп с учётом активного проекта. */
    override fun observeAllGroup(): Flow<List<CircuitGroup>> =
        observeGroupsWithDevices()
            .map { rel -> rel.map { it.toDomainGroupFromRelation() } }

    /** Поток сущностей для PhaseLoad: строго в рамках активного проекта. */
    override fun observeGroupsWithDevices(): Flow<List<CircuitGroupWithDevices>> {
        val allDevicesFlow = deviceDao.observeAllDevices()
        val joinsFlow = groupDeviceJoinDao.observeJoins()

        return activeProjectDs.activeProjectId.flatMapLatest { projectId ->
            val groupsFlow = if (projectId != null)
                groupDao.observeAllGroupsByProject(projectId)
            else
                groupDao.observeAllGroups()

            combine(groupsFlow, allDevicesFlow, joinsFlow) { groups, devices, joins ->
                val devicesById = devices.associateBy { it.deviceId }
                val deviceIdsByGroup = joins
                    .groupBy({ it.groupId }, { it.deviceId })
                    .mapValues { (_, ids) -> ids.toHashSet() }

                groups.map { g ->
                    val ids = deviceIdsByGroup[g.groupId].orEmpty()
                    val devs = ids.mapNotNull { devicesById[it] }
                    CircuitGroupWithDevices(group = g, devices = devs)
                }
            }
        }
    }

    override suspend fun getGroupsWithDevices(): List<GroupWithDevices> {
        val projectId = activeProjectDs.activeProjectId.first()
        val groups = if (projectId != null)
            groupDao.getAllGroupsByProject(projectId)
        else
            groupDao.getAllGroups()

        return groups.map { entity ->
            val devices =
                groupDeviceJoinDao.getDevicesForGroup(entity.groupId)  // 🔁 было deviceDao.getDevicesForGroup(...)
            GroupWithDevices(
                group = entity.toDomainGroup(devices.map { it.toDomainDevice() }),
                devices = devices.map { it.toDomainDevice() }
            )
        }
    }

    override suspend fun addGroup(circuitGroups: List<CircuitGroup>) {
        withContext(dispatchers) {
            val projectId = activeProjectDs.activeProjectId.first()
            db.withTransaction {
                groupDao.deleteAllGroups()
                circuitGroups.forEach { group ->
                    val entityToInsert =
                        group.toEntityGroup().copy(groupId = 0, projectId = projectId)
                    val newGroupId = groupDao.addGroup(entityToInsert)
                    group.devices.forEach { device ->
                        groupDeviceJoinDao.insertJoin(
                            GroupDeviceJoin(
                                groupId = newGroupId,
                                deviceId = device.id
                            )
                        )
                    }
                }
            }
        }
    }

    override suspend fun updateGroup(groupId: Long) {
        val groupWithDevices = groupDao.getGroupWithDevicesById(groupId)
            ?: throw GroupNotFoundException("Группа $groupId не найдена")

        val newCurrent = groupWithDevices.devices
            .mapToDomainDevices()
            .sumOf { d ->
                CurrentCalculator.calculateNominalCurrent(
                    power = d.power.toDouble(),
                    voltage = (d.voltage.value.takeIf { it > 0 } ?: 230).toDouble(),
                    powerFactor = d.powerFactor,
                    demandRatio = d.demandRatio,
                    voltageType = d.voltage.type
                )
            }

        if (newCurrent == 0.0) {
            groupDao.deleteGroupByGroupId(groupId)
        } else {
            groupDao.updateGroupCurrent(groupId, newCurrent)
        }
    }

    override suspend fun getAllGroups(): List<CircuitGroup> {
        return groupDao.getAllGroupsWithDevices().mapToDomainGroupsFromRelations()
    }

    override suspend fun deleteAllGroups() {
        withContext(dispatchers) { groupDao.deleteAllGroups() }
    }

    override suspend fun addDeviceToGroup(deviceId: Long, groupId: Long) {
        groupDeviceJoinDao.insertJoin(GroupDeviceJoin(groupId, deviceId))
    }

    override suspend fun getDevicesForGroup(groupId: Long): List<Device> {
        return groupDeviceJoinDao.getDevicesForGroup(groupId).mapToDomainDevices()
    }

    override suspend fun handleRoomDeletion(roomId: Long) {
        groupDeviceJoinDao.deleteJoinsForRoom(roomId)
        groupDao.deleteGroupByRoomId(roomId)
    }

    override suspend fun handleDeviceDeletion(deviceId: Long) {
        val groupIds = groupDeviceJoinDao.getGroupIdsForDevice(deviceId)
        groupDeviceJoinDao.deleteJoinsForDevice(deviceId)
        groupIds.forEach { id ->
            groupDao.getGroupById(id)?.let { groupDao.updateGroupCurrent(id, 0.0) }
        }
    }

    override suspend fun getGroupById(groupId: Long): CircuitGroup? {
        return groupDao.getGroupWithDevicesById(groupId)?.toDomainGroupFromRelation()
    }

    override suspend fun getGroupByRoom(roomName: String): List<CircuitGroup> =
        withContext(dispatchers) {
            try {
                groupDao.getGroupByRoom(roomName).mapToDomainGroups()
            } catch (_: Exception) {
                throw GroupNotFoundException()
            }
        }

    override suspend fun getGroupByType(groupType: DeviceType): List<CircuitGroup> =
        withContext(dispatchers) {
            try {
                groupDao.getGroupByType(groupType).mapToDomainGroups()
            } catch (_: Exception) {
                throw GroupNotFoundException()
            }
        }

    override suspend fun replaceAllGroupsTransactional(groups: List<CircuitGroup>) =
        withContext(dispatchers) {
            val projectId = activeProjectDs.activeProjectId.first()
            db.withTransaction {
                groupDao.deleteAllGroups()
                val groupEntities =
                    groups.map { it.toEntityGroup().copy(groupId = 0, projectId = projectId) }
                val newIds = groupDao.insertGroups(groupEntities)
                require(newIds.size == groups.size) { "insertGroups returned ${newIds.size} ids for ${groups.size} groups" }

                val joins = buildList {
                    groups.forEachIndexed { idx, g ->
                        val newGroupId = newIds[idx]
                        g.devices.forEach { d ->
                            require(d.id > 0L) { "Device id must be > 0 for join. Device=${d.name}" }
                            add(GroupDeviceJoin(groupId = newGroupId, deviceId = d.id))
                        }
                    }
                }
                if (joins.isNotEmpty()) groupDeviceJoinDao.insertAll(joins)
            }
        }
}