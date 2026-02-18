package ru.mugalimov.volthome.domain.use_case.manual

import android.util.Log
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.ProjectStructuralWriteMutex
import ru.mugalimov.volthome.domain.use_case.SaveAutoCalculatedGroupsToLocalDbUseCase

@Singleton
class CancelManualAndAutoRecalcUseCase @Inject constructor(
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val preferencesRepository: PreferencesRepository,
    private val saveAutoCalculatedGroupsToLocalDbUseCase: SaveAutoCalculatedGroupsToLocalDbUseCase,
    private val structuralWriteMutex: ProjectStructuralWriteMutex, // ✅ single-flight
) {

    data class Params(
        val projectId: String
    )

    suspend fun execute(params: Params): GroupingResult {
        Log.w("MANUAL_CANCEL", "CANCEL_USE_CASE EXECUTE pid=${params.projectId}")

        val projectId = params.projectId
        if (projectId.isBlank()) {
            return GroupingResult.Error("projectId пуст")
        }

        return structuralWriteMutex.withLock(projectId) {
            val mode = preferencesRepository.phaseMode.first()
            val calc = groupCalculatorFactory.create()
            when (val res = calc.calculateGroups(mode)) {
                is GroupingResult.Error -> res
                is GroupingResult.Success -> {
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
}