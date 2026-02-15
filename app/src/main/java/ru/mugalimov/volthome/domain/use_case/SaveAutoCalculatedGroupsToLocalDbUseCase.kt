package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.DistributionDecision

/**
 * Сохранение результата AUTO-пересчёта в локальную БД.
 *
 * ВАЖНО:
 * - Это AUTO-путь (исторический): replace-by-delete+insert.
 * - Для manual Save использовать ЗАПРЕЩЕНО (manual делает diff-commit).
 * - Вынесено в domain/use_case, чтобы UI/VM не держали прямых вызовов replaceAllGroupsTransactional().
 */
class SaveAutoCalculatedGroupsToLocalDbUseCase @Inject constructor(
    private val explicationRepository: ExplicationRepository
) {
    data class Params(
        val projectId: String,
        val groups: List<CircuitGroup>,
        val distributionDecisions: List<DistributionDecision>
    )

    suspend fun execute(params: Params) {
        require(params.projectId.isNotBlank()) { "projectId must be non-blank" }

        // 1) Сохраняем группы AUTO-результата (replace допустим только тут)
        explicationRepository.replaceAllGroupsTransactional(
            projectId = params.projectId,
            groups = params.groups
        )

        // 2) Обновляем decision log (in-memory)
        explicationRepository.setLastDistributionDecisions(params.distributionDecisions)
    }
}