package ru.mugalimov.volthome.data.repository.impl

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.data.local.dao.ProjectLocalStateDao
import ru.mugalimov.volthome.data.repository.DeviceRepository
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.model.Device
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.manual.ManualDeviceDraft
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction
import ru.mugalimov.volthome.domain.model.manual.ManualEditSession
import ru.mugalimov.volthome.domain.model.manual.ManualGroupComposition
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
import ru.mugalimov.volthome.domain.use_case.StructuralWriteCoordinator
import ru.mugalimov.volthome.domain.use_case.manual.DeleteGroupCascadeUseCase
import ru.mugalimov.volthome.domain.use_case.manual.ManualDraftSelectors
import ru.mugalimov.volthome.domain.use_case.manual.RecalculateGroupLineUseCase
import ru.mugalimov.volthome.ui.utilities.ManualDraftResetNotifier

@Singleton
class ManualEditSessionRepositoryImpl @Inject constructor(
    private val recalculateGroupLineUseCase: RecalculateGroupLineUseCase,
    private val deleteGroupCascadeUseCase: DeleteGroupCascadeUseCase,
    private val manualDraftResetNotifier: ManualDraftResetNotifier, // ✅ kill-process UX маркер
    private val projectLocalStateDao: ProjectLocalStateDao,
    private val structuralWriteCoordinator: StructuralWriteCoordinator,
    private val deviceRepository: DeviceRepository,
) : ManualEditSessionRepository {

    companion object {
        private const val TAG = "MANUAL_REPO"
        private const val INVARIANT_TAG = "MANUAL_DRAFT_INVARIANT"
        private const val AUTO_ASSIGN_TAG = "MANUAL_AUTO_ASSIGN"
    }

    /**
     * ✅ Commit 2: сессии по projectId (защита от “manual активен не того проекта”).
     *
     * ВАЖНО: persisted marker глобальный => одновременно manual активен только у одного проекта.
     * Поэтому при успешном enterManualMode(projectId) мы чистим любые другие сессии в памяти,
     * чтобы не копился мусор и никто случайно не прочитал “чужую” сессию.
     */
    private val sessionsFlow = MutableStateFlow<Map<String, ManualEditSession>>(emptyMap())

    // Глобальный key для сериализации операций с persisted marker.
    // enter/exit manual должны быть глобально последовательны, иначе возможны 2 marker одновременно.
    private val GLOBAL_MARKER_KEY = "__GLOBAL_MANUAL_MARKER__"

    /**
     * Снимок проверки целостности manual draft.
     *
     * Инвариант:
     * - каждый deviceId, на который ссылаются groups.deviceIds
     * - и каждый deviceId из unassignedDeviceIds
     * обязан существовать в draft.devices
     */
    private data class ManualDraftInvariantSnapshot(
        val assignedIds: Set<Long>,
        val unassignedIds: Set<Long>,
        val allReferencedIds: Set<Long>,
        val knownIds: Set<Long>,
        val missingInDevices: Set<Long>,
        val danglingDevices: Set<Long>,
    ) {
        val ok: Boolean = missingInDevices.isEmpty()
    }

    /**
     * Собираем полную диагностику целостности draft.
     */
    private fun buildDraftInvariantSnapshot(draft: ProjectEditState): ManualDraftInvariantSnapshot {
        val assignedIds = draft.groups
            .asSequence()
            .flatMap { it.deviceIds.asSequence() }
            .toSet()

        val unassignedIds = draft.unassignedDeviceIds.toSet()
        val allReferencedIds = assignedIds + unassignedIds
        val knownIds = draft.devices
            .asSequence()
            .map { it.deviceId }
            .toSet()

        val missingInDevices = allReferencedIds - knownIds
        val danglingDevices = knownIds - allReferencedIds

        return ManualDraftInvariantSnapshot(
            assignedIds = assignedIds,
            unassignedIds = unassignedIds,
            allReferencedIds = allReferencedIds,
            knownIds = knownIds,
            missingInDevices = missingInDevices,
            danglingDevices = danglingDevices
        )
    }

    /**
     * Доказательный лог инварианта.
     *
     * Важно:
     * - не падаем
     * - только детерминированно логируем, чтобы поймать регрессию
     */
    private fun logDraftInvariant(
        projectId: String,
        source: String,
        draft: ProjectEditState
    ) {
        val snapshot = buildDraftInvariantSnapshot(draft)

        Log.i(
            INVARIANT_TAG,
            "pid=$projectId source=$source " +
                    "groupsRefs=${snapshot.assignedIds.size} " +
                    "unassignedRefs=${snapshot.unassignedIds.size} " +
                    "allRefs=${snapshot.allReferencedIds.size} " +
                    "devices=${snapshot.knownIds.size} " +
                    "missingInDevices=${snapshot.missingInDevices.toList().sorted()} " +
                    "danglingDevices=${snapshot.danglingDevices.toList().sorted()} " +
                    "ok=${snapshot.ok}"
        )
    }

    /**
     * Преобразование обычной доменной модели устройства в manual draft-модель.
     *
     * Важно:
     * - roomId в manual draft обязателен для дальнейших операций create group / resolve room
     * - если roomId отсутствует, это уже битые входные данные, их нельзя молча проглатывать
     */
    private fun Device.toManualDeviceDraft(): ManualDeviceDraft {
        val nonNullRoomId = requireNotNull(roomId) {
            "MANUAL_POST_INSERT: deviceId=$id has null roomId"
        }

        return ManualDeviceDraft(
            deviceId = id,
            roomId = nonNullRoomId,
            roomName = fallbackRoomName(nonNullRoomId),
            deviceType = deviceType,
            powerW = power,
            voltageType = voltage.type,
            demandRatio = demandRatio,
            powerFactor = powerFactor,
            hasMotor = hasMotor,
            requiresDedicatedCircuit = requiresDedicatedCircuit
        )
    }

    private fun fallbackRoomName(roomId: Long): String = "Помещение #$roomId"

    /**
     * Merge новых устройств в draft.devices.
     *
     * Политика:
     * - если deviceId уже существует — обновляем запись
     * - если deviceId новый — добавляем
     * - итог всегда уникален по deviceId
     * - порядок детерминированный: сохраняем старый порядок + добавляем новые в хвост
     */
    private fun mergeDevicesIntoDraft(
        existing: List<ManualDeviceDraft>,
        inserted: List<ManualDeviceDraft>
    ): List<ManualDeviceDraft> {
        if (inserted.isEmpty()) return existing

        val merged = LinkedHashMap<Long, ManualDeviceDraft>()

        // Сначала кладём текущее состояние draft
        existing.forEach { draftDevice ->
            merged[draftDevice.deviceId] = draftDevice
        }

        // Затем накатываем актуальные post-insert устройства
        inserted.forEach { insertedDevice ->
            merged[insertedDevice.deviceId] = insertedDevice
        }

        return merged.values.toList()
    }

    override fun observeSession(projectId: String): Flow<ManualEditSession?> {
        return sessionsFlow
            .map { map -> map[projectId] }
            .distinctUntilChanged()
    }
    override fun isManualActive(projectId: String): Boolean =
        sessionsFlow.value[projectId]?.manualModeActive == true

    override fun getSession(projectId: String): ManualEditSession? =
        sessionsFlow.value[projectId]

    @Deprecated("Compat-only. UI/ViewModel обязаны использовать getSession(projectId)/isManualActive(projectId).")
    override fun getActiveSession(): ManualEditSession? {
        val active = sessionsFlow.value.values.filter { it.manualModeActive }
        if (active.size > 1) {
            // Это реально не должно случаться при глобальном marker.
            Log.e(TAG, "INVARIANT: multiple active manual sessions! pids=${active.map { it.projectId }}")
        }
        return active.firstOrNull()
    }

    override suspend fun enterManualMode(projectId: String, baseState: ProjectEditState) {
        val TAG = "MANUAL_REPO"
        require(projectId.isNotBlank()) { "projectId must be non-blank" }

        Log.d(TAG, "enterManualMode pid=$projectId baseGroups=${baseState.groups.size} baseDevices=${baseState.devices.size}")

        // ✅ Сначала ставим persisted marker (атомарно и глобально), затем создаём in-memory session.
        val markerOut = structuralWriteCoordinator.execute(
            projectId = GLOBAL_MARKER_KEY,
            opName = "MANUAL_MARKER_SET"
        ) {
            // Guard: если manual уже активен на другом проекте — не воруем marker.
            val marked = projectLocalStateDao.getActiveManualProjectId()
            if (marked != null && marked != projectId) {
                throw IllegalStateException("Manual already active for another project (marked=$marked, requested=$projectId)")
            }

            // Санитизация: гарантируем один marker.
            projectLocalStateDao.clearActiveManualMarkerGlobal()

            // Реальная запись marker (ensureRow + rowsUpdated==1)
            projectLocalStateDao.ensureRow(projectId)
            val rows = projectLocalStateDao.setActiveManualMarker(projectId)
            require(rows == 1) { "Failed to set persisted manual marker (rowsUpdated=$rows)" }
        }

        when (markerOut) {
            is StructuralWriteCoordinator.Outcome.Success -> {
                val now = System.currentTimeMillis()
                val base = baseState.deepCopy()
                val draft = baseState.deepCopy()

                val session = ManualEditSession(
                    projectId = projectId,
                    manualModeActive = true,
                    baseState = base,
                    draftState = draft,
                    version = 1L,
                    updatedAtEpochMs = now
                )

                // ✅ Политика: глобальный marker => держим в памяти только текущий проект.
                sessionsFlow.value = mapOf(projectId to session)

                // UX маркер
                manualDraftResetNotifier.markExpected(projectId)

                // Доказательно фиксируем целостность draft на входе в manual
                logDraftInvariant(
                    projectId = projectId,
                    source = "enterManualMode",
                    draft = session.draftState
                )

                Log.d(TAG, "enterManualMode OK pid=$projectId markerSet=1 sessionsKept=1")
            }

            StructuralWriteCoordinator.Outcome.Busy -> {
                Log.w(TAG, "enterManualMode BUSY pid=$projectId")
                throw IllegalStateException("Busy: marker operation in progress")
            }

            StructuralWriteCoordinator.Outcome.Panic -> {
                Log.e(TAG, "enterManualMode PANIC pid=$projectId")
                throw IllegalStateException("Panic: marker operation timed out")
            }

            is StructuralWriteCoordinator.Outcome.Error -> {
                Log.e(TAG, "enterManualMode ERROR pid=$projectId", markerOut.throwable)
                throw markerOut.throwable
            }
        }
    }

    override suspend fun exitManualMode(projectId: String) {
        val TAG = "MANUAL_REPO"
        require(projectId.isNotBlank()) { "projectId must be non-blank" }

        // ✅ КРИТИЧНО:
        // exitManualMode обязан очищать persisted marker даже если in-memory сессии нет.
        // Иначе после kill-process получаем "manual активен" в БД и NULL в памяти → рассинхрон.
        val current = sessionsFlow.value[projectId]
        Log.d(TAG, "exitManualMode START pid=$projectId hasSession=${current != null} manualActive=${current?.manualModeActive}")

        val markerOut = structuralWriteCoordinator.execute(
            projectId = GLOBAL_MARKER_KEY,
            opName = "MANUAL_MARKER_CLEAR"
        ) {
            val cleared = projectLocalStateDao.clearActiveManualMarkerGlobal()
            Log.d(TAG, "exitManualMode marker cleared rows=$cleared pid=$projectId")
        }

        when (markerOut) {
            is StructuralWriteCoordinator.Outcome.Success -> {
                // ✅ UX маркер снимаем всегда
                manualDraftResetNotifier.clearExpected(projectId)

                // ✅ Сессию удаляем, если была
                if (current != null) {
                    sessionsFlow.value = sessionsFlow.value - projectId
                }

                Log.d(TAG, "exitManualMode OK pid=$projectId sessionRemoved=${current != null}")
            }

            StructuralWriteCoordinator.Outcome.Busy -> {
                Log.w(TAG, "exitManualMode BUSY pid=$projectId")
                throw IllegalStateException("Busy: marker clear in progress")
            }

            StructuralWriteCoordinator.Outcome.Panic -> {
                Log.e(TAG, "exitManualMode PANIC pid=$projectId")
                throw IllegalStateException("Panic: marker clear timed out")
            }

            is StructuralWriteCoordinator.Outcome.Error -> {
                Log.e(TAG, "exitManualMode ERROR pid=$projectId", markerOut.throwable)
                throw markerOut.throwable
            }
        }
    }

    override suspend fun apply(projectId: String, action: ManualEditAction) {
        val current = sessionsFlow.value[projectId] ?: run {
            Log.w(TAG, "apply ignored: no session for pid=$projectId action=$action")
            return
        }
        if (!current.manualModeActive) {
            Log.w(TAG, "apply ignored: manualModeActive=false pid=$projectId action=$action")
            return
        }

        val before = current.draftState

        Log.d(TAG, "apply pid=$projectId action=$action ver=${current.version} groups=${before.groups.size}")

        // Логируем инвариант до применения действия
        logDraftInvariant(
            projectId = projectId,
            source = "apply_before_${action::class.simpleName ?: "UnknownAction"}",
            draft = before
        )

        // Отдельный proof-log именно для автоназначения
        if (action == ManualEditAction.AutoAssignUnassigned) {
            Log.i(
                AUTO_ASSIGN_TAG,
                "START pid=$projectId unassignedBefore=${before.unassignedDeviceIds.size} " +
                        "ids=${before.unassignedDeviceIds.toList().sorted()}"
            )
        }

        // ⚠️ НЕ МЕНЯЕМ фазность в Commit 2: оставляем как было у тебя до этого.
        val newDraft = reduceDraft(before, action, mode = PhaseMode.THREE)

        val changed = before != newDraft
        Log.d(
            TAG,
            "apply done pid=$projectId changed=$changed " +
                    "newGroups=${newDraft.groups.size} unassigned=${newDraft.unassignedDeviceIds.size}"
        )

        // Логируем инвариант после применения действия
        logDraftInvariant(
            projectId = projectId,
            source = "apply_after_${action::class.simpleName ?: "UnknownAction"}",
            draft = newDraft
        )

        if (action == ManualEditAction.AutoAssignUnassigned) {
            Log.i(
                AUTO_ASSIGN_TAG,
                "END pid=$projectId unassignedAfter=${newDraft.unassignedDeviceIds.size} " +
                        "ids=${newDraft.unassignedDeviceIds.toList().sorted()}"
            )
        }

        val updated = current.copy(
            draftState = newDraft,
            version = current.version + 1L,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        sessionsFlow.value = sessionsFlow.value + (projectId to updated)
    }

    override suspend fun addInsertedDevicesToUnassigned(
        projectId: String,
        insertedDeviceIds: List<Long>,
        opId: String
    ) {
        if (insertedDeviceIds.isEmpty()) return

        val current = sessionsFlow.value[projectId] ?: return
        if (!current.manualModeActive) return

        val before = current.draftState

        // Быстрый set assigned: чтобы устройство не оказалось и assigned и unassigned одновременно.
        val assigned: Set<Long> = before.groups
            .asSequence()
            .flatMap { it.deviceIds.asSequence() }
            .toSet()

        // Узкий path:
        // читаем из БД только фактически вставленные устройства, а не пересобираем весь draft.
        val insertedDevicesFromDb = deviceRepository
            .getDevicesByIds(insertedDeviceIds)
            .map { it.toManualDeviceDraft() }

        // Для доказательности логируем, если БД вернула не все id.
        val fetchedIds = insertedDevicesFromDb.map { it.deviceId }.toSet()
        val missingFromDb = insertedDeviceIds.toSet() - fetchedIds
        if (missingFromDb.isNotEmpty()) {
            Log.w(
                "MANUAL_POST_INSERT",
                "pid=$projectId opId=$opId fetched=${fetchedIds.size} " +
                        "missingFromDb=${missingFromDb.toList().sorted()}"
            )
        }

        // Обновляем полный draft:
        // 1) merge новых устройств в draft.devices
        val mergedDevices = mergeDevicesIntoDraft(
            existing = before.devices,
            inserted = insertedDevicesFromDb
        )

        // 2) merge новых id в unassigned (с защитой от дублей и от assigned)
        val mergedUnassigned: Set<Long> = buildSet {
            addAll(before.unassignedDeviceIds)
            insertedDeviceIds.forEach { add(it) }
        }
            .asSequence()
            .filterNot { it in assigned }
            .toCollection(LinkedHashSet())

        val newDraft = before.copy(
            devices = mergedDevices,
            unassignedDeviceIds = mergedUnassigned
        )

        val updated = current.copy(
            draftState = newDraft,
            version = current.version + 1L,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        sessionsFlow.value = sessionsFlow.value + (projectId to updated)

        // ✅ DoD корреляции: этот opId должен совпадать с CREATE_DEVICE_DB opId.
        val caller = Throwable().stackTrace
            .take(12)
            .joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }

        Log.i(
            "MANUAL_POST_INSERT",
            "pid=$projectId opId=$opId inserted=${insertedDeviceIds.size} " +
                    "insertedIds=${insertedDeviceIds.sorted()} " +
                    "fetchedDevices=${insertedDevicesFromDb.map { it.deviceId }.sorted()} " +
                    "devices(before=${before.devices.size} after=${mergedDevices.size}) " +
                    "unassigned(before=${before.unassignedDeviceIds.size} after=${mergedUnassigned.size}) " +
                    "unassignedIdsBefore=${before.unassignedDeviceIds.toList().sorted()} " +
                    "unassignedIdsAfter=${mergedUnassigned.toList().sorted()} " +
                    "caller=$caller"
        )

        // После post-insert draft обязан оставаться консистентным.
        logDraftInvariant(
            projectId = projectId,
            source = "addInsertedDevicesToUnassigned",
            draft = newDraft
        )
    }

    @Deprecated("Compat-only. UI/ViewModel обязаны использовать apply(projectId, action).")
    override suspend fun apply(action: ManualEditAction) {
        val active = getActiveSession() ?: run {
            Log.w(TAG, "legacy apply ignored: activeSession=null action=$action")
            return
        }
        apply(projectId = active.projectId, action = action)
    }

    // -------------------- Draft reduce logic (твой код, без изменений по сути) --------------------

    private fun reduceDraft(
        draft: ProjectEditState,
        action: ManualEditAction,
        mode: PhaseMode
    ): ProjectEditState {
        return when (action) {
            ManualEditAction.NoOp -> draft
            is ManualEditAction.ReplaceDraft -> action.newDraft.deepCopy()

            is ManualEditAction.SetGroupPhase -> {
                val updatedGroups = draft.groups.map { g ->
                    if (g.groupId == action.groupId) g.copy(phase = action.phase) else g
                }
                draft.copy(groups = updatedGroups)
            }

            is ManualEditAction.MoveDevice -> {
                val changed = moveDeviceBetweenGroups(draft, action.deviceId, action.fromGroupId, action.toGroupId)
                finalizeAfterCompositionChange(
                    draft = changed,
                    touchedGroupIds = setOf(action.fromGroupId, action.toGroupId)
                )
            }

            is ManualEditAction.MoveToUnassigned -> {
                val changed = moveToUnassigned(draft, action.deviceId, action.fromGroupId)
                finalizeAfterCompositionChange(
                    draft = changed,
                    touchedGroupIds = setOf(action.fromGroupId)
                )
            }

            is ManualEditAction.MoveFromUnassigned -> {
                val changed = moveFromUnassigned(draft, action.deviceId, action.toGroupId)
                finalizeAfterCompositionChange(
                    draft = changed,
                    touchedGroupIds = setOf(action.toGroupId)
                )
            }

            is ManualEditAction.CreateNewGroupAndMove -> {
                val changed = createNewGroupAndMove(draft, mode, action.deviceId)
                finalizeAfterBulkCompositionChange(changed)
            }

            ManualEditAction.AutoAssignUnassigned -> {
                val changed = autoAssignUnassigned(draft, mode)
                finalizeAfterBulkCompositionChange(changed)
            }
        }
    }

    private fun finalizeAfterCompositionChange(
        draft: ProjectEditState,
        touchedGroupIds: Set<Long>
    ): ProjectEditState {
        var cur = draft

        // 1) Удаляем пустые группы каскадом
        val emptyGroupIds = cur.groups
            .filter { it.deviceIds.isEmpty() }
            .map { it.groupId }

        for (gid in emptyGroupIds) {
            cur = deleteGroupCascadeUseCase.execute(
                DeleteGroupCascadeUseCase.Params(state = cur, groupId = gid)
            )
        }

        // 2) Пересчитываем линию + composition только затронутых групп
        if (touchedGroupIds.isEmpty()) return cur

        val devicesById = cur.devices.associateBy { it.deviceId }

        val newGroups: List<ManualGroupDraft> = cur.groups.map { g ->
            if (!touchedGroupIds.contains(g.groupId)) return@map g

            val devsInGroup = g.deviceIds.mapNotNull { id -> devicesById[id] }

            // ✅ composition: если в группе есть устройства другого типа — помечаем MIXED_MANUAL
            val newComposition = run {
                if (devsInGroup.isEmpty()) {
                    // Пустые группы мы уже удалили, но на всякий случай — NORMAL.
                    ManualGroupComposition.NORMAL
                } else {
                    val hasForeignType = devsInGroup.any { it.deviceType != g.groupType }
                    if (hasForeignType) ManualGroupComposition.MIXED_MANUAL else ManualGroupComposition.NORMAL
                }
            }

            // ✅ пересчёт линии (как у тебя было)
            val recalculated = recalculateGroupLineUseCase.execute(
                RecalculateGroupLineUseCase.Params(
                    group = g,
                    devicesInGroup = devsInGroup
                )
            )

            // ✅ возвращаем группу с обновлённой composition
            recalculated.copy(composition = newComposition)
        }

        return cur.copy(groups = newGroups)
    }

    private fun finalizeAfterBulkCompositionChange(draft: ProjectEditState): ProjectEditState {
        val allIds = draft.groups.map { it.groupId }.toSet()
        return finalizeAfterCompositionChange(draft, allIds)
    }

    private fun moveDeviceBetweenGroups(
        draft: ProjectEditState,
        deviceId: Long,
        fromGroupId: Long,
        toGroupId: Long
    ): ProjectEditState {
        if (fromGroupId == toGroupId) return draft

        val from = draft.groups.firstOrNull { it.groupId == fromGroupId } ?: return draft
        val to = draft.groups.firstOrNull { it.groupId == toGroupId } ?: return draft
        if (!from.deviceIds.contains(deviceId)) return draft

        val updatedFrom = from.copy(deviceIds = from.deviceIds.filterNot { it == deviceId })
        val updatedTo = to.copy(deviceIds = (to.deviceIds + deviceId).distinct())

        val updatedGroups = draft.groups.map { g ->
            when (g.groupId) {
                fromGroupId -> updatedFrom
                toGroupId -> updatedTo
                else -> g
            }
        }

        // invariant: device cannot be both assigned and unassigned
        val newUnassigned = draft.unassignedDeviceIds - deviceId

        return draft.copy(groups = updatedGroups, unassignedDeviceIds = newUnassigned)
    }

    private fun moveToUnassigned(
        draft: ProjectEditState,
        deviceId: Long,
        fromGroupId: Long
    ): ProjectEditState {
        val from = draft.groups.firstOrNull { it.groupId == fromGroupId } ?: return draft
        if (!from.deviceIds.contains(deviceId)) return draft

        val updatedFrom = from.copy(deviceIds = from.deviceIds.filterNot { it == deviceId })

        val updatedGroups = draft.groups.map { g ->
            if (g.groupId == fromGroupId) updatedFrom else g
        }

        val newUnassigned = draft.unassignedDeviceIds + deviceId

        return draft.copy(groups = updatedGroups, unassignedDeviceIds = newUnassigned)
    }

    private fun moveFromUnassigned(
        draft: ProjectEditState,
        deviceId: Long,
        toGroupId: Long
    ): ProjectEditState {
        if (!draft.unassignedDeviceIds.contains(deviceId)) return draft
        val to = draft.groups.firstOrNull { it.groupId == toGroupId } ?: return draft

        val updatedTo = to.copy(deviceIds = (to.deviceIds + deviceId).distinct())
        val updatedGroups = draft.groups.map { g -> if (g.groupId == toGroupId) updatedTo else g }

        val newUnassigned = draft.unassignedDeviceIds - deviceId

        return draft.copy(groups = updatedGroups, unassignedDeviceIds = newUnassigned)
    }

    private fun createNewGroupAndMove(
        draft: ProjectEditState,
        mode: PhaseMode,
        deviceId: Long
    ): ProjectEditState {
        val currentGroupId = ManualDraftSelectors.findGroupIdContainingDevice(draft, deviceId)

        val intermediate = if (currentGroupId != null) {
            moveToUnassigned(draft, deviceId, currentGroupId)
        } else {
            draft
        }

        // ✅ Устройство обязано существовать в draft
        val device = intermediate.devices.firstOrNull { it.deviceId == deviceId }
            ?: throw IllegalStateException("MANUAL_CREATE_GROUP: device not found for deviceId=$deviceId")

        // ✅ Тип новой группы = тип переносимого устройства
        val newGroupType: DeviceType = device.deviceType

        // ✅ Комната новой группы = комната устройства / исходной группы
        val (newRoomId, newRoomName) = resolveRoomForNewGroup(
            draft = draft,
            deviceId = deviceId,
            currentGroupId = currentGroupId
        )

        val phase = ManualDraftSelectors.choosePhaseForNewGroup(intermediate, mode)
        val newGroupId = ManualDraftSelectors.nextTempGroupId(intermediate)
        val newGroupNumber = intermediate.nextGroupNumber

        // ✅ breakerType лучше брать не "из первой попавшейся группы",
        // а ставить безопасный дефолт. Иначе можно притащить чужую кривую.
        val newGroup = ManualGroupDraft(
            groupId = newGroupId,
            groupNumber = newGroupNumber,
            roomId = newRoomId,
            roomName = newRoomName,
            groupType = newGroupType,
            composition = ManualGroupComposition.NORMAL,
            phase = phase,
            deviceIds = listOf(deviceId),
            nominalCurrent = 0.0,
            circuitBreaker = 16,
            cableSection = 2.5,
            breakerType = "C",
            rcdRequired = false,
            rcdCurrent = null
        )

        val newGroups = intermediate.groups + newGroup
        val newUnassigned = intermediate.unassignedDeviceIds - deviceId

        return intermediate.copy(
            groups = newGroups,
            unassignedDeviceIds = newUnassigned,
            nextGroupNumber = newGroupNumber + 1
        )
    }

    /**
     * Определяет валидные roomId / roomName для новой группы.
     *
     * Приоритет:
     * 1. Если устройство уже было в существующей группе — берём комнату этой группы.
     * 2. Иначе ищем комнату по самому устройству (device.roomId) среди групп draft/base.
     * 3. Если не нашли — это уже инвариантная ошибка, такую группу создавать нельзя.
     */
    private fun resolveRoomForNewGroup(
        draft: ProjectEditState,
        deviceId: Long,
        currentGroupId: Long?
    ): Pair<Long, String> {
        // 1) Если устройство уже было в существующей группе — это самый надёжный источник
        val sourceGroup = currentGroupId?.let { gid ->
            draft.groups.firstOrNull { it.groupId == gid }
        }

        if (sourceGroup != null && sourceGroup.roomId > 0L) {
            val sourceRoomName = sourceGroup.roomName.trim()
            if (sourceRoomName.isNotBlank()) {
                return sourceGroup.roomId to sourceRoomName
            }

            Log.w(
                TAG,
                "MANUAL_CREATE_GROUP blank source roomName, " +
                        "deviceId=$deviceId currentGroupId=$currentGroupId roomId=${sourceGroup.roomId}"
            )

            return sourceGroup.roomId to fallbackRoomName(sourceGroup.roomId)
        }

        // 2) Берём устройство из draft
        val device = draft.devices.firstOrNull { it.deviceId == deviceId }
            ?: throw IllegalStateException("MANUAL_CREATE_GROUP: device not found for deviceId=$deviceId")

        val deviceRoomId = device.roomId
        require(deviceRoomId > 0L) {
            "MANUAL_CREATE_GROUP: deviceId=$deviceId has invalid roomId=$deviceRoomId"
        }

        // 3) Сначала пытаемся взять имя комнаты прямо из устройства
        val deviceRoomName = device.roomName.trim()
        if (deviceRoomName.isNotBlank()) {
            return deviceRoomId to deviceRoomName
        }

        // 4) Потом пытаемся восстановить через существующие группы того же roomId
        val roomNameFromDraft = draft.groups
            .asSequence()
            .mapNotNull { group ->
                if (group.roomId != deviceRoomId) return@mapNotNull null
                val normalized = group.roomName.trim()
                if (normalized.isBlank()) null else normalized
            }
            .firstOrNull()

        if (!roomNameFromDraft.isNullOrBlank()) {
            return deviceRoomId to roomNameFromDraft
        }

        // 5) Аварийный fallback, чтобы manual не падал
        val fallback = fallbackRoomName(deviceRoomId)

        Log.w(
            TAG,
            "MANUAL_CREATE_GROUP fallback roomName used, " +
                    "deviceId=$deviceId roomId=$deviceRoomId fallback='$fallback'"
        )

        return deviceRoomId to fallback
    }

    private fun autoAssignUnassigned(
        draft: ProjectEditState,
        mode: PhaseMode
    ): ProjectEditState {
        if (draft.unassignedDeviceIds.isEmpty()) return draft

        val devicesById = ManualDraftSelectors.devicesById(draft)
        var curDraft = draft

        val unassignedOrdered = draft.unassignedDeviceIds.toList().sorted()

        for (deviceId in unassignedOrdered) {
            val device = devicesById[deviceId] ?: continue

            val candidates = curDraft.groups.map { g ->
                g to ManualDraftSelectors.scorePlacement(curDraft, mode, device, g)
            }

            val best = candidates
                .filter { it.second.capacityClass != 2 }
                .minWithOrNull { a, b -> ManualDraftSelectors.compareScore(a.second, b.second) }

            curDraft = if (best == null) {
                createNewGroupAndMove(curDraft, mode, deviceId)
            } else {
                moveFromUnassigned(curDraft, deviceId, best.first.groupId)
            }
        }

        return curDraft
    }
}