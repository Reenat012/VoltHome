package ru.mugalimov.volthome.data.repository.impl

import android.content.Context
import android.util.Log
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
import ru.mugalimov.volthome.data.local.entity.CircuitGroupEntity
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
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
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
            if (projectId.isNullOrBlank()) {
                // нет активного проекта => нет групп
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                val groupsFlow = groupDao.observeAllGroupsByProject(projectId)
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
    }

    override suspend fun commitManualDraftTransactional(
        projectId: String,
        draftState: ProjectEditState
    ) {
        withContext(dispatchers) {
            require(projectId.isNotBlank()) { "projectId must be non-blank" }

            // Один tag на весь коммит — удобно фильтровать.
            val tag = "APPLY_DIFF"

            fun groupsSummaryFromDraft(): String =
                draftState.groups
                    .sortedBy { it.groupNumber }
                    .joinToString { g -> "${g.groupId}#${g.groupNumber}#${g.phase.name}(devs=${g.deviceIds.size})" }

            fun groupsSummaryFromDb(groups: List<CircuitGroupEntity>): String =
                groups
                    .sortedBy { it.groupNumber }
                    .joinToString { g -> "${g.groupId}#${g.groupNumber}#${g.phase}(type=${g.groupType})" }

            fun sanityFail(message: String): Nothing {
                // Важно: логируем перед падением, иначе require() не оставит следа кроме stacktrace.
                Log.e(tag, "SANITY FAILED: $message")
                throw IllegalStateException(message)
            }

            db.withTransaction {
                // =========================
                // 0) MANUAL_SAVE: входные данные (draft)
                // =========================
                val desiredDraftGroups = draftState.groups
                val desiredGroupCount = desiredDraftGroups.size

                val desiredUnassignedIds = draftState.unassignedDeviceIds.toSet()
                val desiredAllDeviceIds = desiredDraftGroups.flatMap { it.deviceIds }.toSet()
                val desiredAssignedIds = desiredAllDeviceIds - desiredUnassignedIds

                Log.d(
                    tag,
                    "MANUAL_SAVE BEGIN projectId=$projectId " +
                            "desiredGroups=$desiredGroupCount devices(all)=${desiredAllDeviceIds.size} " +
                            "assigned=${desiredAssignedIds.size} unassigned=${desiredUnassignedIds.size} " +
                            "draftGroups=[${groupsSummaryFromDraft()}]"
                )

                // =========================
                // 1) LOAD dbState (groups + joins) строго по projectId boundary
                // =========================
                val dbGroups = groupDao.getAllGroupsByProject(projectId)
                val dbGroupsById = dbGroups.associateBy { it.groupId }
                val dbGroupIds = dbGroups.map { it.groupId }.toSet()

                val dbJoins = if (dbGroupIds.isEmpty()) {
                    emptyList()
                } else {
                    groupDeviceJoinDao.getJoinsForGroupIds(dbGroupIds.toList())
                }

                Log.d(
                    tag,
                    "APPLY_DIFF DB snapshot: groups=${dbGroups.size} joins=${dbJoins.size} " +
                            "groupIds(sample)=${dbGroupIds.take(12)} dbGroups=[${groupsSummaryFromDb(dbGroups)}]"
                )

                // =========================
                // 2) BUILD desiredState (разделяем existing vs new)
                // =========================
                val desiredExistingDraft = desiredDraftGroups
                    .filter { it.groupId > 0L && dbGroupsById.containsKey(it.groupId) }
                val desiredExistingIds = desiredExistingDraft.map { it.groupId }.toSet()

                val desiredNewDraft = desiredDraftGroups
                    .filter { it.groupId <= 0L || !dbGroupsById.containsKey(it.groupId) }

                Log.d(
                    tag,
                    "APPLY_DIFF desired split: existing=${desiredExistingDraft.size} new=${desiredNewDraft.size} " +
                            "existingIds(sample)=${desiredExistingIds.take(12)}"
                )

                // =========================
                // 3) COMPUTE diff (groups)
                // =========================
                val groupsToDeleteIds = (dbGroupIds - desiredExistingIds).toList()

                val groupsToUpdateEntities = desiredExistingDraft.mapNotNull { gDraft ->
                    val dbEntity = dbGroupsById[gDraft.groupId] ?: return@mapNotNull null
                    dbEntity.copy(
                        groupNumber = gDraft.groupNumber,
                        roomId = gDraft.roomId,
                        roomName = gDraft.roomName,
                        groupType = gDraft.groupType.name,
                        phase = gDraft.phase.name,
                        nominalCurrent = gDraft.nominalCurrent ?: 0.0,
                        circuitBreaker = gDraft.circuitBreaker ?: 16,
                        cableSection = gDraft.cableSection ?: 2.5,
                        breakerType = gDraft.breakerType ?: "",
                        rcdRequired = gDraft.rcdRequired ?: false,
                        rcdCurrent = gDraft.rcdCurrent ?: 30
                    )
                }

                val groupsToInsertEntities = desiredNewDraft.map { gDraft ->
                    CircuitGroupEntity(
                        groupId = 0L,
                        groupNumber = gDraft.groupNumber,
                        roomId = gDraft.roomId,
                        roomName = gDraft.roomName,
                        groupType = gDraft.groupType.name,
                        nominalCurrent = gDraft.nominalCurrent ?: 0.0,
                        circuitBreaker = gDraft.circuitBreaker ?: 16,
                        cableSection = gDraft.cableSection ?: 2.5,
                        breakerType = gDraft.breakerType ?: "",
                        rcdRequired = gDraft.rcdRequired ?: false,
                        rcdCurrent = gDraft.rcdCurrent ?: 30,
                        phase = gDraft.phase.name,
                        projectId = projectId
                    )
                }

                Log.d(
                    tag,
                    "APPLY_DIFF delta groups: toDelete=${groupsToDeleteIds.size} " +
                            "toUpdate=${groupsToUpdateEntities.size} toInsert=${groupsToInsertEntities.size} " +
                            "deleteIds(sample)=${groupsToDeleteIds.take(12)}"
                )

                // =========================
                // 4) APPLY deletes (joins -> groups)
                // =========================
                if (groupsToDeleteIds.isNotEmpty()) {
                    groupDeviceJoinDao.deleteJoinsForGroupIds(groupsToDeleteIds)
                    groupDao.deleteGroupsByIds(projectId = projectId, groupIds = groupsToDeleteIds)
                    Log.d(tag, "APPLY_DIFF Applied delete: groups=${groupsToDeleteIds.size}")
                }

                // =========================
                // 5) APPLY updates/inserts groups (без REPLACE)
                // =========================
                if (groupsToUpdateEntities.isNotEmpty()) {
                    groupDao.updateGroups(groupsToUpdateEntities)
                    Log.d(tag, "APPLY_DIFF Applied update: groups=${groupsToUpdateEntities.size}")
                }

                // tempId -> newRealId (для новых групп из draft)
                val tempToNewId = LinkedHashMap<Long, Long>()
                if (groupsToInsertEntities.isNotEmpty()) {
                    val newIds = groupDao.insertGroups(groupsToInsertEntities)
                    if (newIds.size != groupsToInsertEntities.size) {
                        sanityFail("insertGroups returned ${newIds.size} ids for ${groupsToInsertEntities.size} groups")
                    }

                    desiredNewDraft.forEachIndexed { idx, draft ->
                        tempToNewId[draft.groupId] = newIds[idx]
                    }

                    Log.d(
                        tag,
                        "APPLY_DIFF Applied insert: groups=${newIds.size} newIds(sample)=${newIds.take(12)} tempToNew(sample)=${tempToNewId.entries.take(12)}"
                    )
                } else {
                    Log.d(tag, "APPLY_DIFF Applied insert: groups=0")
                }

                // =========================
                // 6) APPLY joins + clean overrides
                // =========================
                // ВАЖНО: после коммита manual фазы в AUTO не должны перекрываться старым override.
                overrideDao.deleteByProject(projectId)

                val affectedDeviceIds = (desiredAllDeviceIds + desiredUnassignedIds).toList()
                if (affectedDeviceIds.isNotEmpty()) {
                    groupDeviceJoinDao.deleteJoinsForDeviceIds(affectedDeviceIds)
                }

                val joinsToInsert = buildList {
                    desiredDraftGroups.forEach { gDraft ->
                        val finalGroupId = tempToNewId[gDraft.groupId] ?: gDraft.groupId
                        if (finalGroupId <= 0L) return@forEach

                        gDraft.deviceIds
                            .filter { it !in desiredUnassignedIds } // unassigned не должны получить join
                            .distinct()
                            .forEach { deviceId ->
                                add(GroupDeviceJoin(groupId = finalGroupId, deviceId = deviceId))
                            }
                    }
                }

                if (joinsToInsert.isNotEmpty()) {
                    groupDeviceJoinDao.insertAll(joinsToInsert)
                }

                Log.d(
                    tag,
                    "APPLY_DIFF delta joins: affectedDevices=${affectedDeviceIds.size} " +
                            "joinsInserted=${joinsToInsert.size} joinsSample=${joinsToInsert.take(12).map { it.groupId to it.deviceId }}"
                )

                // =========================
                // 7) SANITY CHECK (жёстко доказываем корректность)
                // =========================
                val afterGroups = groupDao.getAllGroupsByProject(projectId)
                val afterGroupIds = afterGroups.map { it.groupId }.toSet()

                if (afterGroups.size != desiredGroupCount) {
                    sanityFail("groups count mismatch. desired=$desiredGroupCount actual=${afterGroups.size}")
                }

                // 7.1) Доказываем стабильность существующих group_id (они обязаны пережить коммит)
                val missingStable = desiredExistingIds - afterGroupIds
                if (missingStable.isNotEmpty()) {
                    sanityFail("stable groupIds lost after commit. missing=${missingStable.take(30)}")
                }

                // 7.2) Доказываем что новые группы получили реальные id
                // (если были новые, tempToNewId должен быть заполнен и эти id должны существовать)
                if (desiredNewDraft.isNotEmpty()) {
                    val newRealIds = tempToNewId.values.toSet()
                    val missingNew = newRealIds - afterGroupIds
                    if (missingNew.isNotEmpty()) {
                        sanityFail("new groupIds not found after commit. missing=${missingNew.take(30)} tempToNew=$tempToNewId")
                    }
                }

                if (afterGroupIds.isNotEmpty()) {
                    val afterJoins = groupDeviceJoinDao.getJoinsForGroupIds(afterGroupIds.toList())
                    val afterJoinDeviceIds = afterJoins.map { it.deviceId }.toSet()

                    // 7.3) unassigned устройства не должны иметь join’ов
                    val badUnassigned = desiredUnassignedIds.intersect(afterJoinDeviceIds)
                    if (badUnassigned.isNotEmpty()) {
                        sanityFail("unassigned devices still have joins. bad=${badUnassigned.take(30)}")
                    }

                    // 7.4) membership должен совпасть 1:1
                    val desiredJoinPairs = joinsToInsert.map { it.groupId to it.deviceId }.toSet()
                    val actualJoinPairs = afterJoins.map { it.groupId to it.deviceId }.toSet()

                    if (actualJoinPairs != desiredJoinPairs) {
                        val missing = (desiredJoinPairs - actualJoinPairs).take(30)
                        val extra = (actualJoinPairs - desiredJoinPairs).take(30)
                        sanityFail(
                            "joins mismatch. desired=${desiredJoinPairs.size} actual=${actualJoinPairs.size} " +
                                    "missing=$missing extra=$extra"
                        )
                    }

                    // Итоговый "до/после" пакет
                    val afterSummary = afterGroups
                        .sortedBy { it.groupNumber }
                        .joinToString { g -> "${g.groupId}#${g.groupNumber}#${g.phase}(type=${g.groupType})" }

                    Log.d(
                        tag,
                        "SANITY OK projectId=$projectId groups=${afterGroups.size} joins=${afterJoins.size} " +
                                "stableIds=${desiredExistingIds.size} newIds=${tempToNewId.size} " +
                                "afterGroups=[$afterSummary]"
                    )
                } else {
                    Log.d(tag, "SANITY OK projectId=$projectId (no groups) desired=$desiredGroupCount")
                }

                Log.d(tag, "MANUAL_SAVE END projectId=$projectId")
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

        Log.e(
            "GROUP_WRITE",
            "REPLACE_ALL BEGIN pid=$projectId groups=${groups.size} " +
                    "thread=${Thread.currentThread().name}",
            Throwable("STACK")
        )

        require(projectId.isNotBlank()) { "projectId must be non-blank" }

        db.withTransaction {
            val oldGroupIds = groupDao.getGroupIdsByProject(projectId)

            Log.e(
                "GROUP_WRITE",
                "REPLACE_ALL DB snapshot pid=$projectId oldGroups=${oldGroupIds.size} " +
                        "oldIds(sample)=${oldGroupIds.take(12)}"
            )

            if (oldGroupIds.isNotEmpty()) {
                groupDeviceJoinDao.deleteJoinsForGroupIds(oldGroupIds)
            }

            // ✅ КРИТИЧНО: сносим overrides проекта, чтобы они не "накрывали" сохранённые фазы после Save.
            overrideDao.deleteByProject(projectId)

            groupDao.deleteAllGroupsByProject(projectId)

            val groupEntities = groups.map { g ->
                // projectId обязателен на уровне маппера
                g.toEntityGroup(projectId).copy(
                    // при replace мы всегда хотим новые id
                    groupId = 0
                )
            }

            val newIds = groupDao.insertGroups(groupEntities)
            require(newIds.size == groups.size) {
                "insertGroups returned ${newIds.size} ids for ${groups.size} groups"
            }
            Log.e("GROUP_WRITE", "REPLACE_ALL END pid=$projectId insertedGroups=${groups.size}")

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

            Log.e(
                "GROUP_WRITE",
                "REPLACE_ALL(overload) activePid=$projectId groups=${groups.size} " +
                        "thread=${Thread.currentThread().name}",
                Throwable("STACK")
            )

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

            val projectId = activeProjectDs.activeProjectId.first()
            require(!projectId.isNullOrBlank()) { "activeProjectId is null/blank. addGroup запрещён без projectId." }

            Log.e(
                "GROUP_WRITE",
                "ADD_GROUP BEGIN pid=$projectId groups=${circuitGroups.size} " +
                        "thread=${Thread.currentThread().name}",
                Throwable("STACK")
            )

            db.withTransaction {
                val oldGroupIds = groupDao.getGroupIdsByProject(projectId)

                Log.e(
                    "GROUP_WRITE",
                    "ADD_GROUP DB snapshot pid=$projectId oldGroups=${oldGroupIds.size} " +
                            "oldIds(sample)=${oldGroupIds.take(12)}"
                )

                // 2) Чистим join-таблицу только для групп проекта
                if (oldGroupIds.isNotEmpty()) {
                    groupDeviceJoinDao.deleteJoinsForGroupIds(oldGroupIds)
                }

                // 3) Удаляем группы только текущего проекта (никаких wipe all)
                groupDao.deleteAllGroupsByProject(projectId)

                // 4) Вставляем новые группы с явным projectId
                circuitGroups.forEach { group ->
                    val entityToInsert = group.toEntityGroup(projectId).copy(
                        // при вставке всегда autoIncrement
                        groupId = 0
                    )

                    val newGroupId = groupDao.addGroup(entityToInsert)

                    // 5) Вставляем join'ы
                    group.devices.forEach { device ->
                        require(device.id > 0L) { "Device id must be > 0 for join. Device=${device.name}" }
                        groupDeviceJoinDao.insertJoin(
                            GroupDeviceJoin(groupId = newGroupId, deviceId = device.id)
                        )
                        Log.e("GROUP_WRITE", "ADD_GROUP END pid=$projectId insertedGroups=${circuitGroups.size}")
                    }
                }
            }
        }
    }

    override suspend fun updateGroup(groupId: Long) {
        Log.e(
            "GROUP_WRITE",
            "UPDATE_GROUP BEGIN groupId=$groupId thread=${Thread.currentThread().name}",
            Throwable("STACK")
        )

        val groupWithDevices = groupDao.getGroupWithDevicesById(groupId)
            ?: throw GroupNotFoundException("Группа $groupId не найдена")

        val devCount = groupWithDevices.devices.size

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

        Log.e(
            "GROUP_WRITE",
            "UPDATE_GROUP CALC groupId=$groupId devs=$devCount newCurrent=$newCurrent"
        )

        if (newCurrent == 0.0) {
            Log.e("GROUP_WRITE", "UPDATE_GROUP DELETE groupId=$groupId (current=0)")
            groupDao.deleteGroupByGroupId(groupId)
        } else {
            Log.e("GROUP_WRITE", "UPDATE_GROUP SET_CURRENT groupId=$groupId current=$newCurrent")
            groupDao.updateGroupCurrent(groupId, newCurrent)
        }

        Log.e("GROUP_WRITE", "UPDATE_GROUP END groupId=$groupId")
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
        Log.e(
            "GROUP_WRITE",
            "HANDLE_DEVICE_DELETE BEGIN deviceId=$deviceId thread=${Thread.currentThread().name}",
            Throwable("STACK")
        )

        val groupIds = groupDeviceJoinDao.getGroupIdsForDevice(deviceId)

        Log.e("GROUP_WRITE", "HANDLE_DEVICE_DELETE deviceId=$deviceId groups=${groupIds.size} ids=${groupIds.take(20)}")

        groupDeviceJoinDao.deleteJoinsForDevice(deviceId)

        groupIds.forEach { id ->
            groupDao.getGroupById(id)?.let {
                Log.e("GROUP_WRITE", "HANDLE_DEVICE_DELETE zeroCurrent groupId=$id")
                groupDao.updateGroupCurrent(id, 0.0)
            }
        }

        Log.e("GROUP_WRITE", "HANDLE_DEVICE_DELETE END deviceId=$deviceId")
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