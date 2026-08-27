package ru.mugalimov.volthome.domain.use_case.reconfiguration

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.mugalimov.volthome.data.repository.ExplicationRepository
import ru.mugalimov.volthome.data.repository.ManualEditSessionRepository
import ru.mugalimov.volthome.data.repository.PreferencesRepository
import ru.mugalimov.volthome.data.repository.ProjectOwnershipRepository
import ru.mugalimov.volthome.data.repository.ProjectSetupRepository
import ru.mugalimov.volthome.data.repository.resolve
import ru.mugalimov.volthome.di.database.IoDispatcher
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.GroupingResult
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.reconfiguration.ProjectConfigurationDraft
import ru.mugalimov.volthome.domain.model.reconfiguration.ReconfigurationImpact
import ru.mugalimov.volthome.domain.use_case.GroupCalculatorFactory
import ru.mugalimov.volthome.domain.use_case.CircuitLoadCalculator

class BuildProjectReconfigurationPreviewUseCase @Inject constructor(
    private val setupRepository: ProjectSetupRepository,
    private val preferencesRepository: PreferencesRepository,
    private val explicationRepository: ExplicationRepository,
    private val ownershipRepository: ProjectOwnershipRepository,
    private val manualSessionRepository: ManualEditSessionRepository,
    private val groupCalculatorFactory: GroupCalculatorFactory,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(draft: ProjectConfigurationDraft): Result<ReconfigurationImpact> =
        withContext(ioDispatcher) {
            runCatching {
                val projectId = draft.projectId.trim()
                require(projectId.isNotBlank()) { "Сначала выберите проект" }
                draft.inputPowerKw?.let {
                    require(it.isFinite() && it in MIN_INPUT_POWER_KW..MAX_INPUT_POWER_KW) {
                        "Доступная мощность должна быть от 0,5 до 1000 кВт"
                    }
                }

                val fallbackMode = preferencesRepository.phaseMode.first()
                val setup = setupRepository.resolve(projectId, fallbackMode)
                val currentGroups = explicationRepository.observeAllGroupByProject(projectId).first()
                val devices = explicationRepository.getAllDevicesByProject(projectId)
                val proposedGroups = when (
                    val result = groupCalculatorFactory.create(projectId)
                        .calculateGroups(draft.phaseMode)
                ) {
                    is GroupingResult.Success -> result.system.groups
                    is GroupingResult.Error -> error(result.message)
                }

                ReconfigurationImpact(
                    currentPhaseMode = setup.phaseMode,
                    proposedPhaseMode = draft.phaseMode,
                    currentInputPowerKw = setup.inputPowerKw,
                    proposedInputPowerKw = draft.inputPowerKw,
                    currentGroupsCount = currentGroups.size,
                    proposedGroupsCount = proposedGroups.size,
                    devicesCount = devices.size,
                    calculatedPowerKw = CircuitLoadCalculator.calculate(devices).calculatedPowerW / 1000.0,
                    currentMaxPhaseCurrentA = calculateMaxPhaseCurrent(currentGroups),
                    proposedMaxPhaseCurrentA = calculateMaxPhaseCurrent(proposedGroups),
                    hasManualStructure = ownershipRepository.isManualLock(projectId),
                    hasActiveManualSession = manualSessionRepository.isManualActive(projectId),
                    phaseModeChanged = setup.phaseMode != draft.phaseMode,
                    inputPowerChanged = !samePower(setup.inputPowerKw, draft.inputPowerKw)
                )
            }
        }

    private fun samePower(first: Double?, second: Double?): Boolean = when {
        first == null || second == null -> first == second
        else -> kotlin.math.abs(first - second) < 0.001
    }

    companion object {
        const val MIN_INPUT_POWER_KW = 0.5
        const val MAX_INPUT_POWER_KW = 1000.0
    }
}

internal fun calculateMaxPhaseCurrent(groups: List<CircuitGroup>): Double =
    groups.filter { it.phase == Phase.THREE_PHASE }.sumOf(CircuitGroup::nominalCurrent) +
        listOf(Phase.A, Phase.B, Phase.C).maxOf { phase ->
            groups.filter { it.phase == phase }.sumOf(CircuitGroup::nominalCurrent)
        }
