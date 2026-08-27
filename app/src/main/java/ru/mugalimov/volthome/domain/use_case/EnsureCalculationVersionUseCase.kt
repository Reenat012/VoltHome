package ru.mugalimov.volthome.domain.use_case

import javax.inject.Inject
import ru.mugalimov.volthome.data.local.dao.GroupDao
import ru.mugalimov.volthome.domain.model.CalculationAlgorithm
import ru.mugalimov.volthome.domain.model.CalculationSource
import ru.mugalimov.volthome.domain.model.create.AutoCalculationResult

/**
 * Обновляет только устаревший AUTO-результат при открытии проекта.
 * Ручную структуру не меняет: её защищает и тип источника, и manual lock внутри
 * [AutoRebuildGroupsAfterDeviceInsertUseCase].
 */
class EnsureCalculationVersionUseCase @Inject constructor(
    private val groupDao: GroupDao,
    private val autoRebuild: AutoRebuildGroupsAfterDeviceInsertUseCase
) {
    suspend fun execute(projectId: String): AutoCalculationResult {
        val normalizedId = projectId.trim()
        if (normalizedId.isBlank()) {
            return AutoCalculationResult.Skipped("Проект не выбран")
        }

        val groups = groupDao.getAllGroupsByProject(normalizedId)
        if (groups.isEmpty()) {
            return AutoCalculationResult.Skipped("В проекте пока нет рассчитанных линий")
        }

        val hasOutdatedAutoResult = groups.any { entity ->
            entity.calculationSource == CalculationSource.AUTO.name &&
                entity.algorithmVersion < CalculationAlgorithm.VERSION
        }
        if (!hasOutdatedAutoResult) {
            return AutoCalculationResult.Skipped("Расчёт уже актуален")
        }

        return autoRebuild.execute(
            AutoRebuildGroupsAfterDeviceInsertUseCase.Params(
                projectIdRecorded = normalizedId,
                insertedIds = emptyList(),
                opId = "CALCULATION_VERSION_${CalculationAlgorithm.VERSION}",
                forceRecalculation = true
            )
        )
    }
}
