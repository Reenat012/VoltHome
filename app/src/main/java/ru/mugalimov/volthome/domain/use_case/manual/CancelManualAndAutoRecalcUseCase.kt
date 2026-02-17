package ru.mugalimov.volthome.domain.use_case.manual

import android.util.Log
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.SaveAutoCalculatedGroupsToLocalDbUseCase

/**
 * Cancel manual -> полный авто-пересчёт -> сохранение результата в локальную БД.
 *
 * ❗STRUCTURE writer:
 * - Отмена manual — это ЯВНЫЙ сценарий, где допустима структурная запись (rebuild).
 * - Поэтому коммит делаем ТОЛЬКО через SaveAutoCalculatedGroupsToLocalDbUseCase (единая точка).
 */
@Singleton
class CancelManualAndAutoRecalcUseCase @Inject constructor(
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: PreferencesRepository,
    private val saveAutoCalculatedGroupsToLocalDbUseCase: SaveAutoCalculatedGroupsToLocalDbUseCase,
) {

    data class Params(
        val projectId: String
    )

    suspend fun execute(params: Params): GroupingResult {
        Log.e(
            "MANUAL_CANCEL",
            "CANCEL_USE_CASE EXECUTE pid=${params.projectId}",
            Throwable("STACK")
        )

        val projectId = params.projectId
        if (projectId.isBlank()) {
            return GroupingResult.Error("projectId пуст")
        }

        // 1) Актуальный режим фаз
        val mode = preferencesRepository.phaseMode.first()

        // 2) Считаем
        val calc = groupCalculatorFactory.create()
        return when (val res = calc.calculateGroups(mode)) {
            is GroupingResult.Error -> res
            is GroupingResult.Success -> {
                // 3) ✅ Единственный explicit structural writer для AUTO rebuild
                saveAutoCalculatedGroupsToLocalDbUseCase.execute(
                    SaveAutoCalculatedGroupsToLocalDbUseCase.Params(
                        projectId = projectId,
                        groups = res.system.groups,
                        distributionDecisions = res.distributionDecisions
                    )
                )
                res
            }
        }
    }
}