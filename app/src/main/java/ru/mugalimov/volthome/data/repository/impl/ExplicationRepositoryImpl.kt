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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.core.error.GroupNotFoundException
import ru.mugalimov.volthome.core.logging.GroupWriteLogger
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
import ru.mugalimov.volthome.domain.use_case.ProjectStructuralWriteMutex

class ExplicationRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val groupDeviceJoinDao: GroupDeviceJoinDao,
    private val deviceDao: DeviceDao,
    private val overrideDao: GroupPhaseOverrideDao,
    private val db: AppDatabase,
    private val activeProjectDs: ActiveProjectDataStore,
    @IoDispatcher private val dispatchers: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
    private val structuralWriteMutex: ProjectStructuralWriteMutex,
) : ExplicationRepository {

    // ===== Decision log распределения фаз (in-memory) =====
    // Не пишем в БД, чтобы не тащить миграции.
    private val _distributionDecisions = MutableStateFlow<List<DistributionDecision>>(emptyList())

    override fun observeDistributionDecisions(): Flow<List<DistributionDecision>> =
        _distributionDecisions.asStateFlow()

    override suspend fun setLastDistributionDecisions(decisions: List<DistributionDecision>) {
        _distributionDecisions.value = decisions
    }

    /**
     * Reader Map (Коммит 0):
     * - UI читает группы через observeGroupsWithDevices(), где есть boundary по activeProjectId:
     *   groupDao.observeAllGroupsByProject(pid) + deviceDao.observeAllDevices() + joinDao.observeJoins().
     * - membership источник истины: group_device_join (без projectId).
     * - структура групп: таблица groups (с projectId).
     */
    override fun observeAllGroup(): Flow<List<CircuitGroup>> =
        observeGroupsWithDevices().map { rel -> rel.map { it.toDomainGroupFromRelation() } }

    override fun observeGroupsWithDevices(): Flow<List<CircuitGroupWithDevices>> =
        activeProjectDs.activeProjectId.flatMapLatest { projectIdNullable ->
            val projectId = projectIdNullable.orEmpty()
            if (projectId.isBlank()) {
                flowOf(emptyList())
            } else {
                groupDao.observeGroupsWithDevicesByProject(projectId)
                    .map { rel ->
                        // Небольшой доказательный лог: если вдруг снова появится "groups>0 & devices=0"
                        // мы это увидим сразу.
                        if (rel.isNotEmpty()) {
                            val zeros = rel.count { it.devices.isEmpty() }
                            if (zeros > 0) {
                                Log.w(
                                    "EXP_OBSERVE",
                                    "observeGroupsWithDevicesByProject pid=$projectId groups=${rel.size} emptyDeviceGroups=$zeros"
                                )
                            }
                        }
                        rel
                    }
            }
        }

    override suspend fun commitManualDraftTransactional(
        projectId: String,
        draftState: ProjectEditState
    ) {
        withContext(dispatchers) {
            require(projectId.isNotBlank()) { "projectId must be non-blank" }

            structuralWriteMutex.withLock(projectId) {

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
                    Log.e(tag, "SANITY FAILED: $message")
                    throw IllegalStateException(message)
                }

                db.withTransaction {
                    // Коммит 0: fingerprint снимаем внутри транзакции (атомарно относительно наших write).
                    val fpBefore = snapshotFingerprintInTx(projectId)

                    GroupWriteLogger.logStructureWrite(
                        source = GroupWriteLogger.Source.MANUAL_SAVE,
                        reason = GroupWriteLogger.Reason.DIFF_COMMIT,
                        projectId = projectId,
                        groupsCount = draftState.groups.size,
                        joinsCount = -1,
                        before = fpBefore,
                        after = null,
                        sample = "BEGIN op=MANUAL_DIFF_COMMIT draftGroups=${draftState.groups.size}",
                        throwable = null
                    )

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

                    val dbJoins = if (dbGroupIds.isEmpty()) emptyList()
                    else groupDeviceJoinDao.getJoinsForGroupIds(dbGroupIds.toList())

                    Log.d(
                        tag,
                        "APPLY_DIFF DB snapshot: groups=${dbGroups.size} joins=${dbJoins.size} " +
                                "groupIds(sample)=${dbGroupIds.take(12)} dbGroups=[${
                                    groupsSummaryFromDb(
                                        dbGroups
                                    )
                                }]"
                    )

                    // =========================
// 2) BUILD desiredState (existing vs new) + resolve temp ids (idempotent save)
// =========================

                    // deviceIds в draft, которые реально должны быть join’ами (unassigned не пишем)
                    fun assignedDeviceIdsForDraftGroup(g: ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft): Set<Long> =
                        g.deviceIds.asSequence()
                            .filter { it !in desiredUnassignedIds }
                            .toSet()

                    val dbDevicesByGroupId: Map<Long, Set<Long>> =
                        dbJoins.groupBy({ it.groupId }, { it.deviceId })
                            .mapValues { (_, ids) -> ids.toSet() }

// tempId(-1) -> realId(7288) если в БД уже есть группа, совпадающая по сигнатуре и membership
                    val tempToExistingId = LinkedHashMap<Long, Long>()

// Чтобы не “назначить” один db group на две temp-группы
                    val claimedDbIds = HashSet<Long>()

                    desiredDraftGroups
                        .asSequence()
                        .filter { it.groupId <= 0L }
                        .sortedBy { it.groupNumber } // детерминизм
                        .forEach { gDraft ->
                            val wantedDevices = assignedDeviceIdsForDraftGroup(gDraft)

                            // Кандидаты: по “структуре”, затем по membership
                            val candidates = dbGroups
                                .asSequence()
                                .filter { it.groupId !in claimedDbIds }
                                .filter { it.groupNumber == gDraft.groupNumber }
                                .filter { it.phase == gDraft.phase.name }
                                .filter { it.groupType == gDraft.groupType.name }
                                .filter { it.roomId == gDraft.roomId }
                                .filter { it.roomName == gDraft.roomName }
                                .toList()

                            val matched = candidates.firstOrNull { db ->
                                val dbDevs = dbDevicesByGroupId[db.groupId].orEmpty()
                                dbDevs == wantedDevices
                            }

                            if (matched != null) {
                                tempToExistingId[gDraft.groupId] = matched.groupId
                                claimedDbIds += matched.groupId
                            }
                        }

                    val desiredExistingDraft = desiredDraftGroups
                        .filter { g ->
                            // existing, если:
                            // 1) реальный id > 0 и он есть в БД
                            // 2) или temp id резолвится в уже существующую группу БД
                            (g.groupId > 0L && dbGroupsById.containsKey(g.groupId)) || tempToExistingId.containsKey(g.groupId)
                        }

                    val desiredExistingIds = desiredExistingDraft
                        .map { g -> tempToExistingId[g.groupId] ?: g.groupId }
                        .toSet()

                    val desiredNewDraft = desiredDraftGroups
                        .filter { g ->
                            // new, если:
                            // - это temp id, который НЕ резолвится
                            // - или real id, которого нет в БД (крайний кейс)
                            (g.groupId <= 0L && !tempToExistingId.containsKey(g.groupId)) ||
                                    (g.groupId > 0L && !dbGroupsById.containsKey(g.groupId))
                        }

                    Log.d(
                        tag,
                        "APPLY_DIFF desired split: existing=${desiredExistingDraft.size} new=${desiredNewDraft.size} " +
                                "existingIds(sample)=${desiredExistingIds.take(12)} tempResolved=${tempToExistingId.size}"
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
                        // expected joins to delete = join'ы тех групп, которые удаляем (снимок dbJoins уже есть)
                        val expectedJoinsToDelete = dbJoins.count { it.groupId in groupsToDeleteIds }

                        val rowsJoinsDeleted = groupDeviceJoinDao.deleteJoinsForGroupIds(groupsToDeleteIds)
                        if (rowsJoinsDeleted != expectedJoinsToDelete) {
                            sanityFail("deleteJoinsForGroupIds rows mismatch. expected=$expectedJoinsToDelete actual=$rowsJoinsDeleted deleteGroups=${groupsToDeleteIds.size}")
                        }

                        val rowsGroupsDeleted = groupDao.deleteGroupsByIds(projectId = projectId, groupIds = groupsToDeleteIds)
                        if (rowsGroupsDeleted != groupsToDeleteIds.size) {
                            sanityFail("deleteGroupsByIds rows mismatch. expected=${groupsToDeleteIds.size} actual=$rowsGroupsDeleted")
                        }

                        Log.d(tag, "APPLY_DIFF Applied delete: groups=${groupsToDeleteIds.size} joins=$rowsJoinsDeleted")
                    }

                    // =========================
                    // 5) APPLY updates/inserts groups (без REPLACE)
                    // =========================
                    if (groupsToUpdateEntities.isNotEmpty()) {
                        groupDao.updateGroups(groupsToUpdateEntities)
                        Log.d(
                            tag,
                            "APPLY_DIFF Applied update: groups=${groupsToUpdateEntities.size}"
                        )
                    }

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
                            "APPLY_DIFF Applied insert: groups=${newIds.size} newIds(sample)=${
                                newIds.take(
                                    12
                                )
                            } tempToNew(sample)=${tempToNewId.entries.take(12)}"
                        )
                    } else {
                        Log.d(tag, "APPLY_DIFF Applied insert: groups=0")
                    }

                    // =========================
                    // 6) APPLY joins + clean overrides
                    // =========================
                    overrideDao.deleteByProject(projectId)

                    val affectedDeviceIds = (desiredAllDeviceIds + desiredUnassignedIds).toList()
                    if (affectedDeviceIds.isNotEmpty()) {
                        // ✅ boundary: текущие группы проекта (после delete/update/insert групп)
                        val curGroupIds = groupDao.getGroupIdsByProject(projectId)
                        val curJoins = if (curGroupIds.isEmpty()) emptyList() else groupDeviceJoinDao.getJoinsForGroupIds(curGroupIds)

                        val expectedDeviceJoinDeletes = curJoins.count { it.deviceId in affectedDeviceIds }

                        val rowsDeleted = groupDeviceJoinDao.deleteJoinsForDeviceIds(affectedDeviceIds)
                        if (rowsDeleted != expectedDeviceJoinDeletes) {
                            sanityFail("deleteJoinsForDeviceIds rows mismatch. expected=$expectedDeviceJoinDeletes actual=$rowsDeleted affectedDevices=${affectedDeviceIds.size}")
                        }
                    }

                    val joinsToInsert = buildList {
                        desiredDraftGroups.forEach { gDraft ->
                            val finalGroupId =
                                tempToNewId[gDraft.groupId]
                                    ?: tempToExistingId[gDraft.groupId]
                                    ?: gDraft.groupId

                            if (finalGroupId <= 0L) return@forEach

                            gDraft.deviceIds
                                .filter { it !in desiredUnassignedIds }
                                .distinct()
                                .forEach { deviceId ->
                                    add(
                                        GroupDeviceJoin(
                                            groupId = finalGroupId,
                                            deviceId = deviceId
                                        )
                                    )
                                }
                        }
                    }

                    if (joinsToInsert.isNotEmpty()) {
                        groupDeviceJoinDao.insertAll(joinsToInsert)
                    }

                    // ✅ Полный sanity: join состояние == desired
                    val finalGroupIds = groupDao.getGroupIdsByProject(projectId)

                    val expectedPairs = joinsToInsert
                        .asSequence()
                        .map { it.groupId to it.deviceId }
                        .toSet()

                    assertJoinsState(tag = tag, groupIdsBoundary = finalGroupIds, expectedPairs = expectedPairs)

                    // =========================
                    // 7) SANITY CHECK
                    // =========================
                    val afterGroups = groupDao.getAllGroupsByProject(projectId)
                    val afterGroupIds = afterGroups.map { it.groupId }.toSet()

                    if (afterGroups.size != desiredGroupCount) {
                        sanityFail("groups count mismatch. desired=$desiredGroupCount actual=${afterGroups.size}")
                    }

                    val missingStable = desiredExistingIds - afterGroupIds
                    if (missingStable.isNotEmpty()) {
                        sanityFail(
                            "stable groupIds lost after commit. missing=${
                                missingStable.take(
                                    30
                                )
                            }"
                        )
                    }

                    if (desiredNewDraft.isNotEmpty()) {
                        val newRealIds = tempToNewId.values.toSet()
                        val missingNew = newRealIds - afterGroupIds
                        if (missingNew.isNotEmpty()) {
                            sanityFail(
                                "new groupIds not found after commit. missing=${
                                    missingNew.take(
                                        30
                                    )
                                } tempToNew=$tempToNewId"
                            )
                        }
                    }

                    val afterJoinsCount = if (afterGroupIds.isNotEmpty()) {
                        groupDeviceJoinDao.getJoinsForGroupIds(afterGroupIds.toList()).size
                    } else 0

                    val fpAfter = snapshotFingerprintInTx(projectId)

                    GroupWriteLogger.logStructureWrite(
                        source = GroupWriteLogger.Source.MANUAL_SAVE,
                        reason = GroupWriteLogger.Reason.DIFF_COMMIT,
                        projectId = projectId,
                        groupsCount = afterGroups.size,
                        joinsCount = afterJoinsCount,
                        before = fpBefore,
                        after = fpAfter,
                        sample = "END op=MANUAL_DIFF_COMMIT stableIds=${desiredExistingIds.size} newIds=${tempToNewId.size}",
                        throwable = null
                    )

                    Log.d(tag, "MANUAL_SAVE END projectId=$projectId")
                }
            }
        }
    }

    override suspend fun replaceAllGroupsTransactional(
        projectId: String,
        groups: List<CircuitGroup>
    ) = withContext(dispatchers) {
        require(projectId.isNotBlank()) { "projectId must be non-blank" }

        // ✅ Single-flight на уровне репозитория: structural операции строго последовательны по проекту
        structuralWriteMutex.withLock(projectId) {

            val shortCaller = GroupWriteLogger.shortCallerTrace(skip = 2, take = 8)

            val fullCaller = Throwable().stackTrace
                .joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }

            val detectedSource = GroupWriteLogger.detectSourceFromCaller(fullCaller)

            val allowedStructuralWriters = setOf(
                GroupWriteLogger.Source.AUTO_SAVE,
                GroupWriteLogger.Source.MANUAL_SAVE,
                GroupWriteLogger.Source.UNKNOWN
            )

            if (detectedSource !in allowedStructuralWriters) {
                val msg =
                    "STRUCTURE WRITE BLOCKED: replaceAllGroupsTransactional called from forbidden source=$detectedSource " +
                            "projectId=$projectId caller=$shortCaller"
                Log.e("STRUCTURE_GUARD", msg, Throwable("STACK"))
                require(false) { msg }
            }

            val tag = "AUTO_REPLACE"

            db.withTransaction {
                val fpBefore = snapshotFingerprintInTx(projectId)

                GroupWriteLogger.logStructureWrite(
                    source = detectedSource,
                    reason = GroupWriteLogger.Reason.EXPLICIT_REBUILD,
                    projectId = projectId,
                    groupsCount = groups.size,
                    joinsCount = -1,
                    before = fpBefore,
                    after = null,
                    sample = "BEGIN op=REPLACE_ALL caller=$shortCaller",
                    throwable = null
                )

                // 1) Снимок старого состояния в границах проекта
                val oldGroupIds = groupDao.getGroupIdsByProject(projectId)

                // ВАЖНО: join не имеет projectId -> boundary через groupIds проекта
                val oldJoins = if (oldGroupIds.isEmpty()) emptyList()
                else groupDeviceJoinDao.getJoinsForGroupIds(oldGroupIds)
                val expectedOldJoins = oldJoins.size

                // 2) Удаляем join'ы старых групп
                if (oldGroupIds.isNotEmpty()) {
                    val rowsJoinsDeleted = groupDeviceJoinDao.deleteJoinsForGroupIds(oldGroupIds)
                    if (rowsJoinsDeleted != expectedOldJoins) {
                        sanityFail(
                            tag,
                            "deleteJoinsForGroupIds rows mismatch. expected=$expectedOldJoins actual=$rowsJoinsDeleted"
                        )
                    }
                }

                // 3) Чистим overrides (они могут “накрыть” структуру)
                overrideDao.deleteByProject(projectId)

                // 4) Удаляем группы проекта
                val rowsGroupsDeleted = groupDao.deleteAllGroupsByProject(projectId)
                if (rowsGroupsDeleted != oldGroupIds.size) {
                    // Если mismatch — это почти всегда гонка/битая выборка/сторонняя запись
                    sanityFail(
                        tag,
                        "deleteAllGroupsByProject rows mismatch. expected=${oldGroupIds.size} actual=$rowsGroupsDeleted"
                    )
                }

                // 5) Вставляем новые группы
                val groupEntities = groups.map { g -> g.toEntityGroup(projectId).copy(groupId = 0) }
                val newIds = groupDao.insertGroups(groupEntities)
                if (newIds.size != groups.size) {
                    sanityFail(
                        tag,
                        "insertGroups returned ${newIds.size} ids for ${groups.size} groups"
                    )
                }

                // 6) Вставляем join'ы под новые groupId
                val desiredPairs = buildSet {
                    groups.forEachIndexed { idx, g ->
                        val newGroupId = newIds[idx]
                        g.devices.forEach { d ->
                            require(d.id > 0L) { "Device id must be > 0 for join. Device=${d.name}" }
                            add(newGroupId to d.id)
                        }
                    }
                }

                val joinsToInsert = desiredPairs.map { (gid, did) ->
                    GroupDeviceJoin(
                        groupId = gid,
                        deviceId = did
                    )
                }
                if (joinsToInsert.isNotEmpty()) {
                    groupDeviceJoinDao.insertAll(joinsToInsert)
                }

                // 7) DB-sanity: фактическое состояние join'ов должно ровно совпасть с ожидаемым
                assertJoinsState(tag = tag, groupIdsBoundary = newIds, expectedPairs = desiredPairs)

                // 8) DB-sanity: старых join'ов больше не существует
                if (oldGroupIds.isNotEmpty()) {
                    val leakedOld = groupDeviceJoinDao.getJoinsForGroupIds(oldGroupIds)
                    if (leakedOld.isNotEmpty()) {
                        sanityFail(
                            tag,
                            "old joins leaked after delete. leaked=${leakedOld.size} sample=${
                                leakedOld.take(10)
                            }"
                        )
                    }
                }

                val fpAfter = snapshotFingerprintInTx(projectId)

                GroupWriteLogger.logStructureWrite(
                    source = detectedSource,
                    reason = GroupWriteLogger.Reason.EXPLICIT_REBUILD,
                    projectId = projectId,
                    groupsCount = groups.size,
                    joinsCount = joinsToInsert.size,
                    before = fpBefore,
                    after = fpAfter,
                    sample = "END op=REPLACE_ALL insertedGroups=${groups.size} insertedJoins=${joinsToInsert.size} oldGroups=${oldGroupIds.size}",
                    throwable = null
                )
            }
        }
    }

    override suspend fun replaceAllGroupsTransactional(groups: List<CircuitGroup>) =
        withContext(dispatchers) {
            // ✅ FIX: activeProjectId nullable -> делаем String
            val projectId = activeProjectDs.activeProjectId.first().orEmpty()
            require(projectId.isNotBlank()) {
                "activeProjectId is null/blank. Нельзя выполнять replaceAllGroupsTransactional без projectId."
            }
            replaceAllGroupsTransactional(projectId = projectId, groups = groups)
        }

    override suspend fun getGroupsWithDevices(): List<GroupWithDevices> {
        val projectIdNullable = activeProjectDs.activeProjectId.first()
        val groups = if (!projectIdNullable.isNullOrBlank()) {
            groupDao.getAllGroupsByProject(projectIdNullable)
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
            // ✅ FIX: activeProjectId nullable -> делаем String
            val projectId = activeProjectDs.activeProjectId.first().orEmpty()
            require(projectId.isNotBlank()) { "activeProjectId is null/blank. addGroup запрещён без projectId." }

            db.withTransaction {
                val fpBefore = snapshotFingerprintInTx(projectId)

                GroupWriteLogger.logStructureWrite(
                    source = GroupWriteLogger.Source.UNKNOWN,
                    reason = GroupWriteLogger.Reason.USER_ACTION,
                    projectId = projectId,
                    groupsCount = circuitGroups.size,
                    joinsCount = -1,
                    before = fpBefore,
                    after = null,
                    sample = "BEGIN op=ADD_GROUP(legacy) groups=${circuitGroups.size}",
                    throwable = null
                )

                val oldGroupIds = groupDao.getGroupIdsByProject(projectId)
                if (oldGroupIds.isNotEmpty()) {
                    groupDeviceJoinDao.deleteJoinsForGroupIds(oldGroupIds)
                }
                groupDao.deleteAllGroupsByProject(projectId)

                circuitGroups.forEach { group ->
                    val entityToInsert = group.toEntityGroup(projectId).copy(groupId = 0)
                    val newGroupId = groupDao.addGroup(entityToInsert)

                    group.devices.forEach { device ->
                        require(device.id > 0L) { "Device id must be > 0 for join. Device=${device.name}" }
                        groupDeviceJoinDao.insertJoin(
                            GroupDeviceJoin(
                                groupId = newGroupId,
                                deviceId = device.id
                            )
                        )
                    }
                }

                val fpAfter = snapshotFingerprintInTx(projectId)

                GroupWriteLogger.logStructureWrite(
                    source = GroupWriteLogger.Source.UNKNOWN,
                    reason = GroupWriteLogger.Reason.USER_ACTION,
                    projectId = projectId,
                    groupsCount = circuitGroups.size,
                    joinsCount = -1,
                    before = fpBefore,
                    after = fpAfter,
                    sample = "END op=ADD_GROUP(legacy)",
                    throwable = null
                )
            }
        }
    }

    override suspend fun updateGroup(groupId: Long) {
        Log.e("GROUP_WRITE", "UPDATE_GROUP BEGIN groupId=$groupId", Throwable("STACK"))

        val groupWithDevices = groupDao.getGroupWithDevicesById(groupId)
            ?: throw GroupNotFoundException("Группа $groupId не найдена")

        // projectId обязателен для derived-write boundary
        val projectId = groupWithDevices.group.projectId.orEmpty()
        require(projectId.isNotBlank()) { "projectId is blank for groupId=$groupId. derived write is forbidden." }

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

        // ❗Коммит 4: updateGroup = только derived-параметры через whitelist
        GroupWriteLogger.logParameterWrite(
            source = GroupWriteLogger.Source.UPDATE_GROUP,
            reason = GroupWriteLogger.Reason.USER_ACTION,
            projectId = projectId,
            groupsCount = 1,
            sample = "op=UPDATE_GROUP derived nominal_current groupId=$groupId newCurrent=$newCurrent devs=$devCount",
            throwable = null
        )

        // ✅ Whitelist derived update (project boundary + идемпотентность)
        groupDao.updateDerivedNominalCurrentOnly(
            projectId = projectId,
            groupId = groupId,
            nominalCurrent = newCurrent,
            epsilon = 1e-4
        )

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
        withContext(dispatchers) {
            val projectId = activeProjectDs.activeProjectId.first().orEmpty()
            require(projectId.isNotBlank()) { "activeProjectId пуст. deleteAllGroups запрещён." }
            replaceAllGroupsTransactional(projectId = projectId, groups = emptyList())
        }
    }

    override suspend fun addDeviceToGroup(deviceId: Long, groupId: Long) {
        // STRUCTURE: вставка join (membership)
        val groupEntity = groupDao.getGroupById(groupId)
        val projectId = groupEntity?.projectId.orEmpty().ifBlank { "unknown" }

        GroupWriteLogger.logStructureWrite(
            source = GroupWriteLogger.Source.UNKNOWN,
            reason = GroupWriteLogger.Reason.USER_ACTION,
            projectId = projectId,
            groupsCount = 0,
            joinsCount = 1,
            before = null,
            after = null,
            sample = "op=ADD_DEVICE_TO_GROUP join(groupId=$groupId, deviceId=$deviceId)",
            throwable = null
        )

        groupDeviceJoinDao.insertJoin(GroupDeviceJoin(groupId, deviceId))
    }

    override suspend fun getDevicesForGroup(groupId: Long): List<Device> {
        return groupDeviceJoinDao.getDevicesForGroup(groupId).mapToDomainDevices()
    }

    override suspend fun handleRoomDeletion(roomId: Long) {
        // STRUCTURE: удаление join'ов и групп комнаты
        val projectId = activeProjectDs.activeProjectId.first().orEmpty().ifBlank { "unknown" }

        GroupWriteLogger.logStructureWrite(
            source = GroupWriteLogger.Source.UNKNOWN,
            reason = GroupWriteLogger.Reason.USER_ACTION,
            projectId = projectId,
            groupsCount = -1,
            joinsCount = -1,
            before = null,
            after = null,
            sample = "op=HANDLE_ROOM_DELETION roomId=$roomId",
            throwable = null
        )

        groupDeviceJoinDao.deleteJoinsForRoom(roomId)
        groupDao.deleteGroupByRoomId(roomId)
    }

    override suspend fun handleDeviceDeletion(deviceId: Long) {
        val projectId = activeProjectDs.activeProjectId.first().orEmpty().ifBlank { "unknown" }

        // Берём список групп ДО удаления join, иначе потеряем связь
        val groupIds = groupDeviceJoinDao.getGroupIdsForDevice(deviceId)

        GroupWriteLogger.logStructureWrite(
            source = GroupWriteLogger.Source.UNKNOWN,
            reason = GroupWriteLogger.Reason.USER_ACTION,
            projectId = projectId,
            groupsCount = groupIds.size,
            joinsCount = -1,
            before = null,
            after = null,
            sample = "op=HANDLE_DEVICE_DELETE deviceId=$deviceId groups=${groupIds.size}",
            throwable = Throwable("STACK")
        )

        groupDeviceJoinDao.deleteJoinsForDevice(deviceId)

        // ✅ derived current пересчитываем/сбрасываем только whitelist'ом
        // ВАЖНО: projectId может быть unknown (если activeProject пуст). Тогда derived-write запрещаем.
        if (projectId != "unknown") {
            groupIds.forEach { id ->
                GroupWriteLogger.logParameterWrite(
                    source = GroupWriteLogger.Source.UNKNOWN,
                    reason = GroupWriteLogger.Reason.USER_ACTION,
                    projectId = projectId,
                    groupsCount = 1,
                    sample = "op=HANDLE_DEVICE_DELETE derived nominal_current groupId=$id current=0.0 (deviceId=$deviceId)",
                    throwable = null
                )

                groupDao.updateDerivedNominalCurrentOnly(
                    projectId = projectId,
                    groupId = id,
                    nominalCurrent = 0.0,
                    epsilon = 1e-4
                )
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
                groupDao.getGroupByType(groupType.name).mapToDomainGroups()
            } catch (_: Exception) {
                throw GroupNotFoundException()
            }
        }

    // -------------------- Fingerprint helpers (Коммит 0) --------------------
    // join-таблица не имеет projectId, поэтому boundary делаем через groupIds проекта.

    private suspend fun snapshotFingerprintInTx(projectId: String): GroupWriteLogger.Fingerprint {
        val groups = groupDao.getAllGroupsByProject(projectId)

        val groupIds = groups.map { it.groupId }
        val joins =
            if (groupIds.isEmpty()) emptyList() else groupDeviceJoinDao.getJoinsForGroupIds(groupIds)

        val joinPairs = joins
            .map { it.groupId to it.deviceId }
            .sortedWith(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })

        val groupStruct = groups
            .map { g ->
                "${g.groupId}|ph=${g.phase}|num=${g.groupNumber}|type=${g.groupType}|room=${g.roomId}"
            }
            .sorted()

        return GroupWriteLogger.fingerprint(joinPairs, groupStruct)
    }

    // -------------------- DB-sanity helpers --------------------

    private fun sanityFail(tag: String, message: String): Nothing {
        Log.e(tag, "SANITY FAILED: $message", Throwable("STACK"))
        throw IllegalStateException(message)
    }

    private fun joinPairs(joins: List<GroupDeviceJoin>): Set<Pair<Long, Long>> =
        joins.asSequence()
            .map { it.groupId to it.deviceId }
            .toSet()

    /**
     * Проверка: фактические join'ы в БД == ожидаемые (как set пар groupId-deviceId).
     * Это заменяет "rowsInserted==expected" для insert'ов.
     */
    private suspend fun assertJoinsState(
        tag: String,
        groupIdsBoundary: List<Long>,
        expectedPairs: Set<Pair<Long, Long>>
    ) {
        val actual = if (groupIdsBoundary.isEmpty()) emptyList()
        else groupDeviceJoinDao.getJoinsForGroupIds(groupIdsBoundary)

        val actualPairs = joinPairs(actual)

        if (actualPairs != expectedPairs) {
            val missing = (expectedPairs - actualPairs).take(30)
            val extra = (actualPairs - expectedPairs).take(30)
            sanityFail(
                tag,
                "joins mismatch. expected=${expectedPairs.size} actual=${actualPairs.size} " +
                        "missing(sample)=$missing extra(sample)=$extra"
            )
        }
    }
}

