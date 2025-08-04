package ru.mugalimov.volthome.domain.model.phase_load

data class PhaseLoadUiState(
    val data: List<PhaseLoadItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: Throwable? = null
)
