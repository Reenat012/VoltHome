package ru.mugalimov.volthome.data.repository.impl

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.core.error.GroupNotFoundException
import ru.mugalimov.volthome.data.local.dao.DeviceDao
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.data.local.dao.GroupDeviceJoinDao
import ru.mugalimov.volthome.data.local.dao.GroupPhaseOverrideDao
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
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.phase_load.GroupWithDevices
import ru.mugalimov.volthome.domain.use_case.CurrentCalculator
import javax.inject.Inject

class ExplicationRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val groupDeviceJoinDao: GroupDeviceJoinDao,
    private val deviceDao: DeviceDao,
    private val overrideDao: GroupPhaseOverrideDao, // ✅ добавили
    private val db: AppDatabase,
    private val activeProjectDs: ActiveProjectDataStore,
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) : ExplicationRepository {

    // ===== Decision log распределения фаз (in-memory) =====
    // Не пишем в БД, чтобы не тащить миграции.
    // Для UI "почему так распределилось" достаточно этого списка в памяти.
    private val _distributionDecisions = MutableStateFlow<List<DistributionDecision>>(emptyList())

    override fun observeDistributionDecisions(): Flow<List<DistributionDecision>> =
        _distributionDecisions.asStateFlow()

    override suspend fun setLastDistributionDecisions(decisions: List<DistributionDecision>) {
        _distributionDecisions.value = decisions
    }

    /**
     * Поток доменных групп с учётом активного проекта.
     * Важно: источник — observeGroupsWithDevices(), который уже фильтруется по activeProjectId.
     */
    override fun observeAllGroup(): Flow<List<CircuitGroup>> =
        observeGroupsWithDevices().map { rel ->
            rel.map { it.toDomainGroupFromRelation() }
        }

    /**
     * Поток сущностей для PhaseLoad: строго в рамках активного проекта.
     *
     * Важно:
     * - join-таблица group_device_join не содержит projectId, поэтому фильтрация происходит
     *   за счёт того, что сами группы берутся по projectId.
     * - устройства берём из общего потока devices и маппим по deviceId.
     */
    override fun observeGroupsWithDevices(): Flow<List<CircuitGroupWithDevices>> {
        val allDevicesFlow = deviceDao.observeAllDevices()
        val joinsFlow = groupDeviceJoinDao.observeJoins()

        return activeProjectDs.activeProjectId.flatMapLatest { projectId ->
            val groupsFlow = if (projectId != null) {
                groupDao.observeAllGroupsByProject(projectId)
            } else {
                // Теоретически не должно происходить (активный проект обязателен),
                // но оставляем fallback, чтобы не уронить приложение в состоянии миграции/пустой БД.
                groupDao.observeAllGroups()
            }

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

    /**
     * Основная реализация replace: строго в рамках projectId.
     *
     * Почему так:
     * - join-таблица завязана на group_id, а group_id при REPLACE меняется (autoIncrement),
     *   поэтому мы:
     *   1) читаем старые group_id проекта
     *   2) чистим join по этим group_id
     *   3) удаляем группы проекта
     *   4) вставляем новые группы (получаем новые group_id)
     *   5) пересоздаём join под новые group_id
     */
    override suspend fun replaceAllGroupsTransactional(
        projectId: String,
        groups: List<CircuitGroup>
    ) = withContext(dispatchers) {
        require(projectId.isNotBlank()) { "projectId must be non-blank" }

        db.withTransaction {
            val oldGroupIds = groupDao.getGroupIdsByProject(projectId)

            if (oldGroupIds.isNotEmpty()) {
                groupDeviceJoinDao.deleteJoinsForGroupIds(oldGroupIds)
            }

            // ✅ КРИТИЧНО: сносим overrides проекта, чтобы они не "накрывали" сохранённые фазы после Save.
            overrideDao.deleteByProject(projectId)

            groupDao.deleteAllGroupsByProject(projectId)

            val groupEntities = groups.map {
                it.toEntityGroup().copy(
                    groupId = 0,
                    projectId = projectId
                )
            }

            val newIds = groupDao.insertGroups(groupEntities)
            require(newIds.size == groups.size) {
                "insertGroups returned ${newIds.size} ids for ${groups.size} groups"
            }

            // 5) Создаём новые join записи под новые group_id
            val joins = buildList {
                groups.forEachIndexed { idx, g ->
                    val newGroupId = newIds[idx]
                    g.devices.forEach { d ->
                        require(d.id > 0L) {
                            "Device id must be > 0 for join. Device=${d.name}"
                        }
                        add(GroupDeviceJoin(groupId = newGroupId, deviceId = d.id))
                    }
                }
            }

            if (joins.isNotEmpty()) {
                groupDeviceJoinDao.insertAll(joins)
            }
        }
    }

    /**
     * Overload без projectId.
     * Важно: НЕ дублируем транзакционную логику — делегируем в основной метод.
     */
    override suspend fun replaceAllGroupsTransactional(groups: List<CircuitGroup>) =
        withContext(dispatchers) {
            val projectId = activeProjectDs.activeProjectId.first()

            require(!projectId.isNullOrBlank()) {
                "activeProjectId is null/blank. Нельзя выполнять replaceAllGroupsTransactional без projectId."
            }

            replaceAllGroupsTransactional(projectId = projectId, groups = groups)
        }

    override suspend fun getGroupsWithDevices(): List<GroupWithDevices> {
        val projectId = activeProjectDs.activeProjectId.first()

        val groups = if (projectId != null) {
            groupDao.getAllGroupsByProject(projectId)
        } else {
            groupDao.getAllGroups()
        }

        return groups.map { entity ->
            val devices = groupDeviceJoinDao.getDevicesForGroup(entity.groupId)
            val domainDevices = devices.map { it.toDomainDevice() }

            GroupWithDevices(
                group = entity.toDomainGroup(domainDevices),
                devices = domainDevices
            )
        }
    }

    /**
     * Снимок групп + устройств строго по projectId.
     * Нужен для построения baseState при входе в manual из общего AppBar.
     */
    override suspend fun getGroupsWithDevicesByProject(projectId: String): List<GroupWithDevices> {
        val groups = groupDao.getAllGroupsByProject(projectId)

        return groups.map { entity ->
            val devices = groupDeviceJoinDao.getDevicesForGroup(entity.groupId)
            val domainDevices = devices.map { it.toDomainDevice() }

            GroupWithDevices(
                group = entity.toDomainGroup(domainDevices),
                devices = domainDevices
            )
        }
    }

    override suspend fun addGroup(circuitGroups: List<CircuitGroup>) {
        withContext(dispatchers) {
            // В мультипроектности добавление групп должно быть строго в рамках активного проекта.
            val projectId = activeProjectDs.activeProjectId.first()
            require(!projectId.isNullOrBlank()) { "activeProjectId is null/blank. addGroup запрещён без projectId." }

            db.withTransaction {
                // 1) Берём старые group_id проекта, чтобы почистить join-таблицу точечно
                val oldGroupIds = groupDao.getGroupIdsByProject(projectId)

                // 2) Чистим join-таблицу только для групп проекта
                if (oldGroupIds.isNotEmpty()) {
                    groupDeviceJoinDao.deleteJoinsForGroupIds(oldGroupIds)
                }

                // 3) Удаляем группы только текущего проекта (никаких wipe all)
                groupDao.deleteAllGroupsByProject(projectId)

                // 4) Вставляем новые группы с явным projectId
                circuitGroups.forEach { group ->
                    val entityToInsert = group.toEntityGroup().copy(
                        groupId = 0,
                        projectId = projectId
                    )

                    val newGroupId = groupDao.addGroup(entityToInsert)

                    // 5) Вставляем join'ы
                    group.devices.forEach { device ->
                        require(device.id > 0L) { "Device id must be > 0 for join. Device=${device.name}" }
                        groupDeviceJoinDao.insertJoin(
                            GroupDeviceJoin(groupId = newGroupId, deviceId = device.id)
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

    @Deprecated(
        message = "deleteAllGroups() запрещён. Используйте replaceAllGroupsTransactional(projectId, emptyList())",
        level = DeprecationLevel.ERROR
    )
    override suspend fun deleteAllGroups() {
        // Даже если кто-то обойдёт deprecation через @Suppress,
        // мы не выполняем wipe-all и не трогаем другие проекты.
        withContext(dispatchers) {
            val projectId = activeProjectDs.activeProjectId.first().orEmpty()
            require(projectId.isNotBlank()) { "activeProjectId пуст. deleteAllGroups запрещён." }

            // Эквивалент "удалить всё в проекте" — через безопасный транзакционный replace.
            replaceAllGroupsTransactional(projectId = projectId, groups = emptyList())
        }
    }

    override suspend fun addDeviceToGroup(deviceId: Long, groupId: Long) {
        groupDeviceJoinDao.insertJoin(GroupDeviceJoin(groupId, deviceId))
    }

    override suspend fun getDevicesForGroup(groupId: Long): List<Device> {
        return groupDeviceJoinDao.getDevicesForGroup(groupId).mapToDomainDevices()
    }

    override suspend fun handleRoomDeletion(roomId: Long) {
        // Удаляем join для групп комнаты и сами группы комнаты
        groupDeviceJoinDao.deleteJoinsForRoom(roomId)
        groupDao.deleteGroupByRoomId(roomId)
    }

    override suspend fun handleDeviceDeletion(deviceId: Long) {
        // Находим, в каких группах был девайс
        val groupIds = groupDeviceJoinDao.getGroupIdsForDevice(deviceId)

        // Удаляем связи
        groupDeviceJoinDao.deleteJoinsForDevice(deviceId)

        // Обнуляем ток групп (логика у тебя историческая: “current=0 -> группа удалится при updateGroup()”)
        groupIds.forEach { id ->
            groupDao.getGroupById(id)?.let {
                groupDao.updateGroupCurrent(id, 0.0)
            }
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
                // В БД group_type хранится как String, поэтому в DAO должен быть String.
                // Если ты уже поправил DAO на String — это верный вызов.
                groupDao.getGroupByType(groupType.name).mapToDomainGroups()
            } catch (_: Exception) {
                throw GroupNotFoundException()
            }
        }
}