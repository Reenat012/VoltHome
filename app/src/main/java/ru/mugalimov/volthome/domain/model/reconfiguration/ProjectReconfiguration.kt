package ru.mugalimov.volthome.domain.model.reconfiguration

import ru.mugalimov.volthome.domain.model.PhaseMode

/** Параметры, которые 4A разрешает изменить как одну безопасную операцию. */
data class ProjectConfigurationDraft(
    val projectId: String,
    val phaseMode: PhaseMode,
    val inputPowerKw: Double?
)

data class ReconfigurationImpact(
    val currentPhaseMode: PhaseMode,
    val proposedPhaseMode: PhaseMode,
    val currentInputPowerKw: Double?,
    val proposedInputPowerKw: Double?,
    val currentGroupsCount: Int,
    val proposedGroupsCount: Int,
    val devicesCount: Int,
    val calculatedPowerKw: Double,
    val currentMaxPhaseCurrentA: Double,
    val proposedMaxPhaseCurrentA: Double,
    val hasManualStructure: Boolean,
    val hasActiveManualSession: Boolean,
    val phaseModeChanged: Boolean,
    val inputPowerChanged: Boolean
) {
    val hasChanges: Boolean get() = phaseModeChanged || inputPowerChanged
    val requiresStructuralRebuild: Boolean get() = phaseModeChanged
}

sealed interface ReconfigurationOutcome {
    data class Success(val impact: ReconfigurationImpact) : ReconfigurationOutcome
    data class Failure(val message: String) : ReconfigurationOutcome
}
