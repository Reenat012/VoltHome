package ru.mugalimov.volthome.domain.model.phase_load

data class PhaseLoadUiState(
    val isLoading: Boolean = true,
    val error: Throwable? = null,
    val data: List<PhaseLoadItem> = emptyList()
)
