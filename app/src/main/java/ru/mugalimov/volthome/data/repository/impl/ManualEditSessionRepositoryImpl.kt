package ru.mugalimov.volthome.data.repository.impl

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.domain.model.DeviceType
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.manual.ManualEditAction
import ru.mugalimov.volthome.domain.model.manual.ManualEditSession
import ru.mugalimov.volthome.domain.model.manual.ManualGroupDraft
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState
import ru.mugalimov.volthome.domain.use_case.manual.DeleteGroupCascadeUseCase
import ru.mugalimov.volthome.domain.use_case.manual.ManualDraftSelectors
import ru.mugalimov.volthome.domain.use_case.manual.RecalculateGroupLineUseCase

@Singleton
class ManualEditSessionRepositoryImpl @Inject constructor(
    private val recalculateGroupLineUseCase: RecalculateGroupLineUseCase,
    private val deleteGroupCascadeUseCase: DeleteGroupCascadeUseCase,
) : ManualEditSessionRepository {
    private val sessionFlow = MutableStateFlow<ManualEditSession?>(null)

    override fun observeSession(projectId: String): Flow<ManualEditSession?> {
        return sessionFlow
            .map { s -> if (s?.projectId == projectId) s else null }
            .distinctUntilChanged()
    }

    override fun getActiveSession(): ManualEditSession? = sessionFlow.value

    override suspend fun enterManualMode(projectId: String, baseState: ProjectEditState) {
        val now = System.currentTimeMillis()
        val base = baseState.deepCopy()
        val draft = baseState.deepCopy()

        sessionFlow.value = ManualEditSession(
            projectId = projectId,
            manualModeActive = true,
            baseState = base,
            draftState = draft,
            version = 1L,
            updatedAtEpochMs = now
        )
    }

    override suspend fun exitManualMode(projectId: String) {
        val current = sessionFlow.value
        if (current?.projectId != projectId) return
        sessionFlow.value = null
    }

    override suspend fun apply(action: ManualEditAction) {
        val current = sessionFlow.value ?: return
        if (!current.manualModeActive) return

        val now = System.currentTimeMillis()

        val newDraft = reduceDraft(
            draft = current.draftState,
            action = action,
            // Если у тебя где-то в проекте хранится phaseMode для manual — прокинешь сюда.
            // Пока считаем, что manual экран у тебя трёхфазный по умолчанию.
            mode = PhaseMode.THREE
        )

        sessionFlow.value = current.copy(
            draftState = newDraft,
            version = current.version + 1L,
            updatedAtEpochMs = now
        )
    }

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
                // Мы не знаем id новой группы тут заранее (он генерится внутри),
                // поэтому проще: bulk-финализация.
                val changed = createNewGroupAndMove(draft, mode, action.deviceId)
                finalizeAfterBulkCompositionChange(changed)
            }

            ManualEditAction.AutoAssignUnassigned -> {
                val changed = autoAssignUnassigned(draft, mode)
                finalizeAfterBulkCompositionChange(changed)
            }
        }
    }

    /**
     * Общая финализация после изменения СОСТАВА групп:
     * - удаляем пустые группы каскадом
     * - пересчитываем линию для затронутых групп
     *
     * ВАЖНО: порядок именно такой:
     * 1) сначала удаляем пустые группы (чтобы не пересчитывать мусор),
     * 2) затем пересчитываем линию для оставшихся.
     */
    private fun finalizeAfterCompositionChange(
        draft: ProjectEditState,
        touchedGroupIds: Set<Long>
    ): ProjectEditState {
        var cur = draft

        // 1) Удаляем пустые группы (на случай, если они появились после move)
        val emptyGroupIds = cur.groups
            .filter { it.deviceIds.isEmpty() }
            .map { it.groupId }

        for (gid in emptyGroupIds) {
            // Каскадный delete: в нашем кейсе группа пустая => устройства не уедут,
            // но мы централизуем удаление в одном механизме.
            cur = deleteGroupCascadeUseCase.execute(
                DeleteGroupCascadeUseCase.Params(state = cur, groupId = gid)
            )
        }

        // 2) Пересчитываем линию только затронутых групп
        if (touchedGroupIds.isEmpty()) return cur

        val devicesById = cur.devices.associateBy { it.deviceId }

        val newGroups: List<ManualGroupDraft> = cur.groups.map { g ->
            if (!touchedGroupIds.contains(g.groupId)) return@map g

            val devsInGroup = g.deviceIds.mapNotNull { id -> devicesById[id] }
            recalculateGroupLineUseCase.execute(
                RecalculateGroupLineUseCase.Params(
                    group = g,
                    devicesInGroup = devsInGroup
                )
            )
        }

        return cur.copy(groups = newGroups)
    }

    /**
     * Удобный вариант для автоопераций: пересчитать линию у ВСЕХ групп.
     * (AutoAssign может трогать много групп, проще и надёжнее пересчитать все.)
     */
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
        // if device is already in some group: remove it first (also allows "New group" from group)
        val currentGroupId = ManualDraftSelectors.findGroupIdContainingDevice(draft, deviceId)
        val intermediate = if (currentGroupId != null) {
            moveToUnassigned(draft, deviceId, currentGroupId)
        } else draft

        val phase = ManualDraftSelectors.choosePhaseForNewGroup(intermediate, mode)
        val newGroupId = ManualDraftSelectors.nextTempGroupId(intermediate)
        val newGroupNumber = intermediate.nextGroupNumber

        // roomId/name/type: keep minimal placeholders; you can refine mapping later
        val newGroup = ManualGroupDraft(
            groupId = newGroupId,
            groupNumber = newGroupNumber,
            roomId = 0L,
            roomName = "",
            groupType = intermediate.groups.firstOrNull()?.groupType ?: DeviceType.OTHER,
            phase = phase,
            deviceIds = listOf(deviceId),
            nominalCurrent = 0.0,
            circuitBreaker = 16, // default placeholder: adjust to your domain rules
            cableSection = 2.5,  // default placeholder
            breakerType = intermediate.groups.firstOrNull()?.breakerType ?: "",
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

    private fun autoAssignUnassigned(
        draft: ProjectEditState,
        mode: PhaseMode
    ): ProjectEditState {
        if (draft.unassignedDeviceIds.isEmpty()) return draft

        val devicesById = ManualDraftSelectors.devicesById(draft)
        var curDraft = draft

        // deterministic order: by deviceId asc
        val unassignedOrdered = draft.unassignedDeviceIds.toList().sorted()

        for (deviceId in unassignedOrdered) {
            val device = devicesById[deviceId] ?: continue

            // candidates among existing groups
            val candidates = curDraft.groups.map { g ->
                g to ManualDraftSelectors.scorePlacement(curDraft, mode, device, g)
            }

            val best = candidates
                .filter { it.second.capacityClass != 2 } // drop impossible
                .minWithOrNull { a, b -> ManualDraftSelectors.compareScore(a.second, b.second) }

            curDraft = if (best == null) {
                // no feasible group => new group
                createNewGroupAndMove(curDraft, mode, deviceId)
            } else {
                // place into best group (from unassigned)
                moveFromUnassigned(curDraft, deviceId, best.first.groupId)
            }
        }

        return curDraft
    }
}