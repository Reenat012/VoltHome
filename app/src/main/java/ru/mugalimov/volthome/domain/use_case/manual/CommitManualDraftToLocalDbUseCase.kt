package ru.mugalimov.volthome.domain.use_case.manual

import android.util.Log
import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.manual.ProjectEditState

/**
 * Commit manual draft -> локальная БД.
 *
 * ВАЖНО:
 * - НЕ трогаем сервер/outbox.
 * - Manual Save обязан идти через DIFF-COMMIT, чтобы:
 *   1) сохранить стабильность group_id для существующих групп,
 *   2) корректно создать новые group_id только для новых групп,
 *   3) записать membership (устройство↔группа) через join-таблицу,
 *   4) гарантировать, что unassigned устройства после Save не имеют join’ов.
 *
 * КРИТИЧНО:
 * - Здесь ЗАПРЕЩЕНО использовать "replace-by-delete+insert"(...) в manual,
 *   потому что это ломает group_id и вызывает каскадные побочные эффекты.
 */
class CommitManualDraftToLocalDbUseCase @Inject constructor(
    private val explicationRepository: ExplicationRepository
) {

    data class Params(
        val projectId: String,
        val draft: ProjectEditState
    )

    /**
     * @return список групп из БД ПОСЛЕ коммита (актуальные id, актуальный membership).
     *
     * Ошибки:
     * - Исключение пробрасываем наверх (выше по стеку обязан быть try/catch),
     *   при этом политика "ошибка не выключает manual" реализуется на уровне UI/Coordinator.
     */
    suspend fun execute(params: Params): List<CircuitGroup> {
        val projectId = params.projectId
        require(projectId.isNotBlank()) { "projectId must be non-blank" }

        val draft = params.draft

        // =========================
        // MANUAL_SAVE: входные данные
        // =========================
        val desiredGroups = draft.groups
        val desiredGroupCount = desiredGroups.size

        val desiredUnassigned = draft.unassignedDeviceIds.toSet()
        val desiredAllDeviceIds = desiredGroups.flatMap { it.deviceIds }.toSet()

        // Важно: “assigned devices” = то, что в группах, но НЕ в unassigned
        val desiredAssigned = (desiredAllDeviceIds - desiredUnassigned)

        // Сводка по группам: groupId#num#phase#devCount
        val desiredSummary = desiredGroups
            .sortedBy { it.groupNumber }
            .joinToString { g -> "${g.groupId}#${g.groupNumber}#${g.phase.name}(devs=${g.deviceIds.size})" }

        Log.d(
            "MANUAL_SAVE",
            "MANUAL_SAVE BEGIN projectId=$projectId groups=$desiredGroupCount " +
                    "devices(all)=${desiredAllDeviceIds.size} assigned=${desiredAssigned.size} unassigned=${desiredUnassigned.size} " +
                    "groupsSummary=[$desiredSummary]"
        )

        // =========================
        // 1) DIFF-COMMIT (атомарно)
        // =========================
        explicationRepository.commitManualDraftTransactional(
            projectId = projectId,
            draftState = draft
        )

        // =========================
        // 2) Читаем ФАКТИЧЕСКОЕ состояние БД
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

        return after
    }
}