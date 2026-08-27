package ru.mugalimov.volthome.domain.use_case.cable

import javax.inject.Inject
import ru.mugalimov.volthome.data.repository.CableCalculationRepository
import ru.mugalimov.volthome.domain.model.CircuitGroup
import ru.mugalimov.volthome.domain.model.Phase
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.cable.CableLineCalculation

class CalculateAndSaveCableLineUseCase @Inject constructor(
    private val repository: CableCalculationRepository,
    private val calculator: CableLineCalculator
) {
    suspend operator fun invoke(
        projectId: String,
        group: CircuitGroup,
        phaseMode: PhaseMode,
        lengthM: Double?,
        powerFactor: Double = group.devices.minOfOrNull { it.powerFactor } ?: 1.0,
        manualSectionMm2: Double? = null
    ): CableLineCalculation {
        val defaults = repository.getDefaults(projectId)
        val linePhaseMode = if (phaseMode == PhaseMode.THREE && group.phase == Phase.THREE_PHASE) {
            PhaseMode.THREE
        } else {
            PhaseMode.SINGLE
        }
        val result = calculator.calculate(
            ru.mugalimov.volthome.domain.model.cable.CableLineInput(
                projectId = projectId,
                groupId = group.groupId,
                phaseMode = linePhaseMode,
                loadCurrentA = group.nominalCurrent,
                breakerA = group.circuitBreaker,
                lengthM = lengthM,
                powerFactor = powerFactor.coerceIn(0.1, 1.0),
                defaults = defaults,
                manualSectionMm2 = manualSectionMm2,
                legacySectionMm2 = group.cableSection
            )
        )
        repository.saveCalculation(result)
        return result
    }
}
