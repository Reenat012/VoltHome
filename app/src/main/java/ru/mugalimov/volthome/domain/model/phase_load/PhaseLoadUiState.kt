package ru.mugalimov.volthome.domain.model.phase_load

import ru.mugalimov.volthome.domain.model.PhaseMode
import ru.mugalimov.volthome.domain.model.incomer.IncomerSpec

data class PhaseLoadUiState(
    val data: List<PhaseLoadItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: Throwable? = null,
    val mode: PhaseMode = PhaseMode.THREE,
    val incomer: IncomerSpec? = null,
    val thresholds: LoadThresholds = LoadThresholds()
    )
