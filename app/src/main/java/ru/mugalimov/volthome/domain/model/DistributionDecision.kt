package ru.mugalimov.volthome.domain.model

/**
 * Лог решений алгоритма распределения групп по фазам.
 * Никакого UI пока — только доказательная база “почему так”.
 */
data class DistributionDecision(
    val groupNumber: Int,
    val groupCurrentA: Double,
    val chosenPhase: Phase,
    val phaseCurrentsBefore: Map<Phase, Double>,
    val phaseCurrentsAfter: Map<Phase, Double>,
    val algorithm: String = "balanced_greedy_min_current",
    val note: String? = null
)