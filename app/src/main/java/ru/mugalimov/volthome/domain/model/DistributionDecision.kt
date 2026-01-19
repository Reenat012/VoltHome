package ru.mugalimov.volthome.domain.model

/**
 * Лог решений алгоритма распределения групп по фазам.
 * Никакого UI пока — только доказательная база “почему так”.
 */
enum class DecisionEventType {
    GREEDY_ASSIGN,
    LOCAL_OPT_MOVE
}

data class DistributionDecision(
    val groupNumber: Int,
    val groupCurrentA: Double,
    val chosenPhase: Phase,
    val phaseCurrentsBefore: Map<Phase, Double>,
    val phaseCurrentsAfter: Map<Phase, Double>,

    // ✅ Коммит 2: структурированная часть события (для уровня C)
    val eventType: DecisionEventType = DecisionEventType.GREEDY_ASSIGN,
    val fromPhase: Phase? = null,
    val toPhase: Phase? = null,

    // ✅ метрики (без парсинга note)
    val imbalanceBeforeA: Double = 0.0,   // перекос ДО (max-min)
    val imbalanceAfterA: Double = 0.0,    // перекос ПОСЛЕ (max-min)

    // оставляем как "техническая подпись", но UI не использует
    val algorithm: String = "balanced_greedy_min_current",
    val note: String? = null
)