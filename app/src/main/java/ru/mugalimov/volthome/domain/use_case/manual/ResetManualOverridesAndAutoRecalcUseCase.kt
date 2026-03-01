package ru.mugalimov.volthome.domain.use_case.manual

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.repository.ManualOverridesCleanerRepository
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.SaveAutoCalculatedGroupsToLocalDbUseCase

class ResetManualOverridesAndAutoRecalcUseCase @Inject constructor(
    private val manualOverridesCleanerRepository: ManualOverridesCleanerRepository,
    private val projectOwnershipRepository: ProjectOwnershipRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val saveAutoCalculatedGroupsToLocalDbUseCase: SaveAutoCalculatedGroupsToLocalDbUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    data class Params(
        val projectId: String,
        val phaseMode: PhaseMode
    )

    suspend fun execute(params: Params): GroupingResult =
        withContext(ioDispatcher) {
            val pid = params.projectId.trim()
            if (pid.isBlank()) return@withContext GroupingResult.Error("projectId пустой")

            Log.w("MANUAL_RESET", "USECASE START pid=$pid mode=${params.phaseMode}")

            // 1) чистим persisted overrides
            val clean = manualOverridesCleanerRepository.clearAllManualOverrides(pid)
            if (!clean) {
                // это “жёсткая” операция: если не смогли — лучше упасть, чем оставить проект в полусостоянии
                return@withContext GroupingResult.Error("Не удалось удалить ручные overrides (DB)")
            }

            // 2) снимаем ownership marker
            projectOwnershipRepository.setManualLock(pid, false)

            // 3) полный auto-recalc
            val calc = groupCalculatorFactory.create()
            when (val res = calc.calculateGroups(params.phaseMode)) {
                is GroupingResult.Error -> {
                    Log.e("MANUAL_RESET", "AUTO_RECALC ERROR pid=$pid msg=${res.message}")
                    res
                }

                is GroupingResult.Success -> {
                    val groups = res.system.groups

                    Log.w("MANUAL_RESET", "AUTO_RECALC OK pid=$pid groups=${groups.size} -> SAVE")

                    saveAutoCalculatedGroupsToLocalDbUseCase.execute(
                        SaveAutoCalculatedGroupsToLocalDbUseCase.Params(
                            projectId = pid,
                            groups = groups,
                            distributionDecisions = res.distributionDecisions,
                            source = "ResetManualOverridesAndAutoRecalcUseCase",
                            opId = null
                        )
                    )

                    Log.w("MANUAL_RESET", "USECASE END OK pid=$pid groups=${groups.size}")
                    res
                }
            }
        }
}