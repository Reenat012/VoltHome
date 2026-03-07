package ru.mugalimov.volthome.domain.use_case.manual

import android.util.Log
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.ownership.OwnershipOverridesCleaner
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.SaveAutoCalculatedGroupsToLocalDbUseCase
import ru.mugalimov.volthome.domain.use_case.StructuralWriteCoordinator

/**
 * Reset ручных overrides + полный auto-recalc + save.
 *
 * ВАЖНО:
 * - reset идёт через единый writer-coordinator
 * - clear overrides -> unlock -> recalc -> auto save
 * - после reset AUTO снова имеет право писать структуру
 */
class ResetManualOverridesAndAutoRecalcUseCase @Inject constructor(
    private val ownershipOverridesCleaner: OwnershipOverridesCleaner,
    private val projectOwnershipRepository: ProjectOwnershipRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory,
    private val saveAutoCalculatedGroupsToLocalDbUseCase: SaveAutoCalculatedGroupsToLocalDbUseCase,
    private val structuralWriteCoordinator: StructuralWriteCoordinator,
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

            val opId = "manual-reset-${UUID.randomUUID()}"

            when (
                val outcome = structuralWriteCoordinator.execute(
                    projectId = pid,
                    opName = "MANUAL_RESET",
                    meta = StructuralWriteCoordinator.Meta(
                        reason = "RESET_MANUAL_OVERRIDES",
                        source = "ResetManualOverridesAndAutoRecalcUseCase",
                        opId = opId
                    )
                ) {
                    Log.w(
                        "MANUAL_RESET",
                        "MANUAL_RESET BEGIN pid=$pid mode=${params.phaseMode} opId=$opId"
                    )

                    // 1) Чистим ВСЕ overrides через единый cleaner
                    val clearStats = ownershipOverridesCleaner.clearAll(pid)

                    Log.w(
                        "MANUAL_RESET",
                        "MANUAL_RESET STEP overridesCleared pid=$pid opId=$opId totalDeleted=${clearStats.totalDeleted}"
                    )

                    // 2) Снимаем manual lock ДО auto-save, иначе AUTO_SAVE будет suppressed
                    projectOwnershipRepository.setManualLock(pid, false)

                    val lockAfterUnlock = projectOwnershipRepository.isManualLock(pid)
                    if (lockAfterUnlock) {
                        return@execute GroupingResult.Error("Не удалось снять manual lock")
                    }

                    Log.w("MANUAL_RESET", "MANUAL_RESET STEP manualLock=false pid=$pid opId=$opId")

                    // 3) Полный auto-recalc в правильном project context
                    val calc = groupCalculatorFactory.create(pid)

                    when (val res = calc.calculateGroups(params.phaseMode)) {
                        is GroupingResult.Error -> {
                            Log.e(
                                "MANUAL_RESET",
                                "MANUAL_RESET FAILED pid=$pid opId=$opId msg=${res.message}"
                            )
                            res
                        }

                        is GroupingResult.Success -> {
                            val groups = res.system.groups

                            // 4) Сохраняем авто-структуру
                            saveAutoCalculatedGroupsToLocalDbUseCase.executeAlreadyLocked(
                                SaveAutoCalculatedGroupsToLocalDbUseCase.Params(
                                    projectId = pid,
                                    groups = groups,
                                    distributionDecisions = res.distributionDecisions,
                                    source = "MANUAL_RESET",
                                    opId = opId
                                )
                            )

                            Log.w(
                                "MANUAL_RESET",
                                "MANUAL_RESET DONE pid=$pid opId=$opId groups=${groups.size}"
                            )

                            res
                        }
                    }
                }
            ) {
                is StructuralWriteCoordinator.Outcome.Success -> outcome.value
                StructuralWriteCoordinator.Outcome.Busy ->
                    GroupingResult.Error("Сброс уже выполняется")

                StructuralWriteCoordinator.Outcome.Panic ->
                    GroupingResult.Error("Сброс прерван по таймауту writer")

                is StructuralWriteCoordinator.Outcome.Error ->
                    GroupingResult.Error(
                        outcome.throwable.message ?: "Ошибка сброса ручных изменений"
                    )
            }
        }
}