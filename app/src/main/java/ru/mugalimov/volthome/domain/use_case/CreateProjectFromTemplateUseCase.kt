package ru.mugalimov.volthome.domain.use_case

import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import ru.mugalimov.volthome.core.analytics.AnalyticsEvent
import ru.mugalimov.volthome.core.analytics.AnalyticsMode
import ru.mugalimov.volthome.core.analytics.AnalyticsTracker
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectSetup
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.data.repository.CableCalculationRepository
import ru.mugalimov.volthome.data.repository.ProjectsRepository
import ru.mugalimov.volthome.data.repository.RoomRepository
import ru.mugalimov.volthome.domain.errors.ProjectLimitError
import ru.mugalimov.volthome.domain.model.projectwizard.CreateProjectFromTemplateRequest
import ru.mugalimov.volthome.domain.model.projectwizard.CreatedProjectSummary
import ru.mugalimov.volthome.domain.model.create.AutoCalculationResult
import ru.mugalimov.volthome.domain.model.cable.ProjectCableDefaults

class CreateProjectFromTemplateUseCase @Inject constructor(
    private val createProject: CreateProjectUseCase,
    private val projectsRepository: ProjectsRepository,
    private val roomRepository: RoomRepository,
    private val setupRepository: ProjectSetupRepository,
    private val cableCalculationRepository: CableCalculationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val analytics: AnalyticsTracker
) {
    sealed interface Outcome {
        data class Success(val summary: CreatedProjectSummary) : Outcome
        data class LimitReached(val error: ProjectLimitError) : Outcome
        data class Failure(val message: String) : Outcome
    }

    suspend operator fun invoke(request: CreateProjectFromTemplateRequest): Outcome {
        val projectName = request.name.trim()
        if (projectName.isBlank()) return Outcome.Failure("Укажите название проекта")

        val previousProjectId = projectsRepository.getActiveProjectId()
        val previousPhaseMode = preferencesRepository.phaseMode.first()

        val creation = createProject(name = projectName, note = null, activate = false)
        if (creation is CreateProjectUseCase.Outcome.Failure) {
            return Outcome.LimitReached(creation.error)
        }
        val projectId = (creation as CreateProjectUseCase.Outcome.Success).projectId

        return try {
            setupRepository.save(
                ProjectSetup(
                    projectId = projectId,
                    objectType = request.objectType,
                    phaseMode = request.phaseMode,
                    inputPowerKw = request.inputPowerKw,
                    sourceTemplateId = request.templateId,
                    sourceTemplateVersion = request.templateVersion,
                    wizardCompleted = true
                )
            )
            cableCalculationRepository.saveDefaults(ProjectCableDefaults(projectId = projectId))
            val batch = roomRepository.addRoomsWithDevicesBatch(
                projectId = projectId,
                requests = request.rooms,
                phaseMode = request.phaseMode,
                opId = "project-wizard-${UUID.randomUUID()}"
            )

            val devicesCount = batch.rooms.sumOf { it.deviceIds.size }
            val calculation = batch.calculation
            if (devicesCount > 0 && calculation !is AutoCalculationResult.Success) {
                val reason = when (calculation) {
                    is AutoCalculationResult.Failure -> calculation.message
                    is AutoCalculationResult.Skipped -> calculation.reason
                    is AutoCalculationResult.Success -> error("unreachable")
                }
                error("Не удалось рассчитать линии: $reason")
            }

            val calculated = calculation as? AutoCalculationResult.Success
            val calculatedPowerKw = (calculated?.calculatedPowerW ?: 0.0) / 1000.0
            val warnings = buildList {
                request.inputPowerKw?.let { availableKw ->
                    when {
                        calculatedPowerKw > availableKw -> add(
                            "Расчётная нагрузка ${formatKw(calculatedPowerKw)} кВт превышает " +
                                "указанную доступную мощность ${formatKw(availableKw)} кВт"
                        )
                        calculatedPowerKw >= availableKw * 0.8 -> add(
                            "Расчётная нагрузка занимает более 80% указанной доступной мощности"
                        )
                    }
                }
            }

            projectsRepository.openProject(projectId)
            preferencesRepository.setPhaseMode(request.phaseMode)
            analytics.track(AnalyticsEvent.ProjectCreated(mode = AnalyticsMode.AUTOMATIC))

            Outcome.Success(
                CreatedProjectSummary(
                    projectId = projectId,
                    roomsCount = batch.rooms.size,
                    devicesCount = devicesCount,
                    phaseMode = request.phaseMode,
                    linesCount = calculated?.linesCount ?: 0,
                    installedPowerW = calculated?.installedPowerW ?: 0.0,
                    calculatedPowerW = calculated?.calculatedPowerW ?: 0.0,
                    inputPowerKw = request.inputPowerKw,
                    calculationCompleted = calculated != null,
                    warnings = warnings
                )
            )
        } catch (t: Throwable) {
            runCatching { projectsRepository.deleteProject(projectId) }
            runCatching {
                if (!previousProjectId.isNullOrBlank()) projectsRepository.openProject(previousProjectId)
                preferencesRepository.setPhaseMode(previousPhaseMode)
            }
            Outcome.Failure(t.message ?: "Не удалось создать проект")
        }
    }

    private fun formatKw(value: Double): String =
        String.format(java.util.Locale("ru", "RU"), "%.1f", value)
}
