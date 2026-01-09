package ru.mugalimov.volthome.domain.model

// Результат расчета групп
sealed class GroupingResult {
    data class Success(
        val system: ElectricalSystem,
        val distributionDecisions: List<DistributionDecision> = emptyList()
    ) : GroupingResult()

    data class Error(val message: String) : GroupingResult()
}