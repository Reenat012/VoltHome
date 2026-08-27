package ru.mugalimov.volthome.domain.model.phase_load

import ru.mugalimov.volthome.domain.model.DistributionDecision
import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec
import ru.mugalimov.volthome.domain.model.incomer.IncomerAssessment

enum class PhaseLoadMode {
    AUTO,
    MANUAL
}

data class PhaseLoadUiState(
    val data: List<PhaseLoadItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: Throwable? = null,
    val mode: PhaseMode = PhaseMode.THREE,
    val phaseLoadMode: PhaseLoadMode = PhaseLoadMode.AUTO,
    val incomer: IncomerSpec? = null,
    val incomerAssessment: IncomerAssessment? = null,
    val thresholds: LoadThresholds = LoadThresholds(),
    val decisions: List<DistributionDecision> = emptyList()
)
