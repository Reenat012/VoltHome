package ru.mugalimov.volthome.domain.use_case.manual

import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.data.ownership.OwnershipOverridesCleaner
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState

/**
 * Commit manual draft -> локальная БД.
 *
 * ВАЖНО:
 * - НЕ трогаем сервер/outbox.
 * - Manual Save обязан идти через DIFF-COMMIT.
 * - После успешного Save:
 *   1) чистим все overrides через единый cleaner,
 *   2) фиксируем manual lock = true,
 *   3) логируем MANUAL_SAVE DONE.
 */
class CommitManualDraftToLocalDbUseCase @Inject constructor(
    private val explicationRepository: ExplicationRepository,
    private val projectOwnershipRepository: ProjectOwnershipRepository,
    private val ownershipOverridesCleaner: OwnershipOverridesCleaner,
    private val recalculateGroupLineUseCase: RecalculateGroupLineUseCase,
) {

    data class Params(
        val projectId: String,
        val draft: ProjectEditState
    )

    /**
     * @return список групп из БД ПОСЛЕ коммита.
     *
     * Ошибки пробрасываются наверх.
     */
    suspend fun execute(params: Params): List<CircuitGroup> {
        val projectId = params.projectId.trim()
        require(projectId.isNotBlank()) { "projectId must be non-blank" }

        val draft = params.draft

        // =========================
        // 0) FINAL RECOMPUTE BARRIER
        // =========================
        // Перед save обязаны пересчитать line params всех групп
        // по актуальному составу draft.devices, иначе можно закоммитить
        // устаревший nominalCurrent / breaker / cable.
        val devicesById = draft.devices.associateBy { it.deviceId }

        val recomputedGroups = draft.groups.map { group ->
            val devicesInGroup = group.deviceIds.mapNotNull { deviceId ->
                devicesById[deviceId]
            }

            recalculateGroupLineUseCase.execute(
                RecalculateGroupLineUseCase.Params(
                    group = group,
                    devicesInGroup = devicesInGroup
                )
            )
        }

        val recomputedDraft = draft.copy(groups = recomputedGroups)

        // =========================
        // MANUAL_SAVE: входные данные
        // =========================
        val desiredGroups = recomputedDraft.groups
        val desiredGroupCount = desiredGroups.size

        val desiredUnassigned = recomputedDraft.unassignedDeviceIds.toSet()
        val desiredAllDeviceIds = desiredGroups.flatMap { it.deviceIds }.toSet()

        // assigned = в группах, но не в unassigned
        val desiredAssigned = desiredAllDeviceIds - desiredUnassigned

        val desiredSummary = desiredGroups
            .sortedBy { it.groupNumber }
            .joinToString { g ->
                "${g.groupId}#${g.groupNumber}#${g.phase.name}(devs=${g.deviceIds.size},I=${g.nominalCurrent})"
            }

        Log.d(
            "MANUAL_SAVE",
            "MANUAL_SAVE BEGIN projectId=$projectId groups=$desiredGroupCount " +
                    "devices(all)=${desiredAllDeviceIds.size} assigned=${desiredAssigned.size} " +
                    "unassigned=${desiredUnassigned.size} groupsSummary=[$desiredSummary]"
        )

        // =========================
        // 1) DIFF-COMMIT (атомарно)
        // =========================
        explicationRepository.commitManualDraftTransactional(
            projectId = projectId,
            draftState = recomputedDraft
        )

        // =========================
        // 2) Чистим все overrides единым cleaner
        // =========================
        val clearStats = ownershipOverridesCleaner.clearAll(projectId)

        // =========================
        // 3) Явно фиксируем post-condition: manualLock=true
        // =========================
        // Да, commit-point уже ставит lock внутри repo-транзакции.
        // Но здесь мы ещё раз фиксируем постусловие usecase-а на своей границе.
        projectOwnershipRepository.setManualLock(projectId, true)

        val manualLock = projectOwnershipRepository.isManualLock(projectId)
        check(manualLock) {
            "MANUAL_SAVE post-condition failed: manualLock=false for pid=$projectId"
        }

        // =========================
        // 4) Читаем фактическое состояние БД
        // =========================
        val after = explicationRepository
            .getGroupsWithDevicesByProject(projectId)
            .map { it.group }
            .sortedBy { it.groupNumber }

        val afterSummary = after.joinToString { g ->
            val phaseName = g.phase?.name ?: "null"
            "${g.groupId}#${g.groupNumber}#$phaseName(devs=${g.devices.size})"
        }

        Log.d(
            "MANUAL_SAVE",
            "MANUAL_SAVE END projectId=$projectId groups=${after.size} groupsSummary=[$afterSummary]"
        )

        Log.w(
            "MANUAL_SAVE",
            "MANUAL_SAVE DONE pid=$projectId manualLock=true " +
                    "clearedOverrides=${clearStats.totalDeleted} groups=${after.size}"
        )

        return after
    }
}