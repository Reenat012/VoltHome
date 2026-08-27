package ru.mugalimov.volthome.domain.use_case.reconfiguration

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.reconfiguration.ProjectReconfigurationBackupStore
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.reconfiguration.ProjectConfigurationDraft
import ru.mugalimov.volthome.domain.model.reconfiguration.ReconfigurationOutcome
import ru.mugalimov.volthome.domain.use_case.manual.ResetManualOverridesAndAutoRecalcUseCase

class ApplyProjectReconfigurationUseCase @Inject constructor(
    private val previewUseCase: BuildProjectReconfigurationPreviewUseCase,
    private val backupStore: ProjectReconfigurationBackupStore,
    private val explicationRepository: ExplicationRepository,
    private val setupRepository: ProjectSetupRepository,
    private val preferencesRepository: PreferencesRepository,
    private val resetUseCase: ResetManualOverridesAndAutoRecalcUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(draft: ProjectConfigurationDraft): ReconfigurationOutcome =
        withContext(ioDispatcher) {
            val impact = previewUseCase(draft).getOrElse {
                return@withContext ReconfigurationOutcome.Failure(
                    it.message ?: "Не удалось подготовить изменения"
                )
            }
            if (!impact.hasChanges) {
                return@withContext ReconfigurationOutcome.Failure("Параметры проекта не изменены")
            }
            if (impact.hasActiveManualSession) {
                return@withContext ReconfigurationOutcome.Failure(
                    "Сначала сохраните или отмените текущее ручное редактирование"
                )
            }

            val projectId = draft.projectId.trim()
            runCatching {
                backupStore.capture(projectId)
                if (impact.requiresStructuralRebuild) {
                    when (
                        val result = resetUseCase.execute(
                            ResetManualOverridesAndAutoRecalcUseCase.Params(
                                projectId = projectId,
                                phaseMode = draft.phaseMode
                            )
                        )
                    ) {
                        is GroupingResult.Error -> error(result.message)
                        is GroupingResult.Success -> Unit
                    }
                }

                val setup = setupRepository.getOrCreate(projectId, impact.currentPhaseMode)
                setupRepository.save(
                    setup.copy(
                        phaseMode = draft.phaseMode,
                        inputPowerKw = draft.inputPowerKw
                    )
                )
                preferencesRepository.setPhaseMode(draft.phaseMode)
            }.fold(
                onSuccess = { ReconfigurationOutcome.Success(impact) },
                onFailure = { failure ->
                    val rollbackFailure = runCatching {
                        val snapshot = backupStore.restore(projectId)
                        explicationRepository.setLastDistributionDecisions(emptyList())
                        val restoredMode = snapshot?.setup?.phase_mode
                            ?.let { runCatching { PhaseMode.valueOf(it) }.getOrNull() }
                        if (restoredMode != null) preferencesRepository.setPhaseMode(restoredMode)
                    }.exceptionOrNull()
                    ReconfigurationOutcome.Failure(
                        buildString {
                            append(failure.message ?: "Не удалось применить изменения")
                            if (rollbackFailure != null) {
                                append(". Не удалось восстановить резервную копию: ")
                                append(rollbackFailure.message ?: "неизвестная ошибка")
                            }
                        }
                    )
                }
            )
        }
}
