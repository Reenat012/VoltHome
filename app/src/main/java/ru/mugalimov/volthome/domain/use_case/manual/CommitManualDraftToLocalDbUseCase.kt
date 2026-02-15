package ru.mugalimov.volthome.domain.use_case.manual

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
     *   при этом политика "ошибка не выключает manual" реализуется на уровне VM/Coordinator.
     */
    suspend fun execute(params: Params): List<CircuitGroup> {
        val projectId = params.projectId
        require(projectId.isNotBlank()) { "projectId must be non-blank" }

        val draft = params.draft

        // 1) DIFF-COMMIT: репозиторий сам:
        // - читает dbState (groups + joins) в рамках projectId,
        // - вычисляет diff,
        // - применяет deletes/updates/inserts,
        // - пересобирает joins,
        // - делает sanity-check и падает, если что-то не сошлось.
        explicationRepository.commitManualDraftTransactional(
            projectId = projectId,
            draftState = draft
        )

        // 2) Возвращаем фактическое состояние БД после коммита.
        // Это важнее, чем "просто draft", потому что:
        // - у новых групп появились реальные group_id,
        // - membership уже нормализован,
        // - unassigned гарантированно без join’ов.
        return explicationRepository
            .getGroupsWithDevicesByProject(projectId)
            .map { it.group }
            .sortedBy { it.groupNumber }
    }
}